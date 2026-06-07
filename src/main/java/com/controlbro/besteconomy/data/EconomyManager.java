package com.controlbro.besteconomy.data;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EconomyManager {
    private static final String SHARDS_KEY = "shards";
    private final CurrencyManager currencyManager;
    private final DataStore dataStore;
    private final MySqlShardBalanceStore shardBalanceStore;
    private final Map<UUID, Map<String, BigDecimal>> balances;
    private final CopyOnWriteArrayList<BalanceChangeListener> balanceChangeListeners = new CopyOnWriteArrayList<>();

    public EconomyManager(CurrencyManager currencyManager, DataStore dataStore, MySqlShardBalanceStore shardBalanceStore) {
        this.currencyManager = currencyManager;
        this.dataStore = dataStore;
        this.shardBalanceStore = shardBalanceStore;
        this.balances = new ConcurrentHashMap<>(dataStore.load());
        loadShardBalancesFromMySql();
    }

    public void save() {
        dataStore.save(localBalancesSnapshot());
        if (isShardMysqlEnabled()) {
            shardBalanceStore.saveBalances(shardBalancesSnapshot());
        }
    }

    public void shutdown() {
        if (shardBalanceStore != null) {
            shardBalanceStore.shutdown();
        }
    }

    public void ensurePlayer(UUID uuid) {
        balances.computeIfAbsent(uuid, id -> new ConcurrentHashMap<>());
        for (Currency currency : currencyManager.getCurrencies()) {
            String key = currency.getName().toLowerCase();
            if (balances.get(uuid).putIfAbsent(key, currency.getStartingBalance()) == null && isShardCurrency(currency)) {
                persistShardBalance(uuid, currency.getStartingBalance());
            }
        }
    }

    public BigDecimal getBalance(UUID uuid, Currency currency) {
        ensurePlayer(uuid);
        return balances.get(uuid).getOrDefault(currency.getName().toLowerCase(), currency.getStartingBalance());
    }

    public void setBalance(UUID uuid, Currency currency, BigDecimal amount) {
        ensurePlayer(uuid);
        BigDecimal oldBalance = getBalance(uuid, currency);
        BigDecimal clamped = clamp(amount, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), clamped);
        notifyBalanceChanged(uuid, currency, oldBalance, clamped, BigDecimal.ZERO);
        if (isShardCurrency(currency)) {
            persistShardBalance(uuid, clamped);
        }
    }

    public BigDecimal addBalance(UUID uuid, Currency currency, BigDecimal amount) {
        ensurePlayer(uuid);
        BigDecimal current = getBalance(uuid, currency);
        BigDecimal newBalance = current.add(amount);
        BigDecimal clamped = clamp(newBalance, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), clamped);
        notifyBalanceChanged(uuid, currency, current, clamped, clamped.subtract(current).max(BigDecimal.ZERO));
        if (isShardCurrency(currency)) {
            persistShardBalance(uuid, clamped);
        }
        return clamped.subtract(current);
    }

    public BigDecimal subtractBalance(UUID uuid, Currency currency, BigDecimal amount) {
        ensurePlayer(uuid);
        BigDecimal current = getBalance(uuid, currency);
        BigDecimal newBalance = current.subtract(amount);
        BigDecimal clamped = clamp(newBalance, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), clamped);
        notifyBalanceChanged(uuid, currency, current, clamped, BigDecimal.ZERO);
        if (isShardCurrency(currency)) {
            persistShardBalance(uuid, clamped);
        }
        return current.subtract(clamped);
    }

    public BigDecimal resetBalance(UUID uuid, Currency currency) {
        ensurePlayer(uuid);
        BigDecimal oldBalance = getBalance(uuid, currency);
        balances.get(uuid).put(currency.getName().toLowerCase(), currency.getStartingBalance());
        notifyBalanceChanged(uuid, currency, oldBalance, currency.getStartingBalance(), BigDecimal.ZERO);
        if (isShardCurrency(currency)) {
            persistShardBalance(uuid, currency.getStartingBalance());
        }
        return currency.getStartingBalance();
    }

    public BigDecimal getAvailableToSpend(UUID uuid, Currency currency) {
        BigDecimal current = getBalance(uuid, currency);
        return current.subtract(currency.getMinMoney());
    }

    public Map<UUID, BigDecimal> getTopBalances(Currency currency) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        balances.entrySet().stream()
            .sorted(Map.Entry.comparingByValue(Comparator.comparing((Map<String, BigDecimal> map) ->
                map.getOrDefault(currency.getName().toLowerCase(), currency.getStartingBalance())).reversed()))
            .forEach(entry -> result.put(entry.getKey(),
                entry.getValue().getOrDefault(currency.getName().toLowerCase(), currency.getStartingBalance())));
        return Collections.unmodifiableMap(result);
    }

    public void addBalanceChangeListener(BalanceChangeListener listener) {
        balanceChangeListeners.add(listener);
    }

    public void removeBalanceChangeListener(BalanceChangeListener listener) {
        balanceChangeListeners.remove(listener);
    }

    private void notifyBalanceChanged(UUID uuid, Currency currency, BigDecimal oldBalance, BigDecimal newBalance, BigDecimal earnedAmount) {
        if (oldBalance.compareTo(newBalance) == 0) return;
        for (BalanceChangeListener listener : balanceChangeListeners) {
            listener.onBalanceChanged(uuid, currency, oldBalance, newBalance, earnedAmount);
        }
    }

    private void loadShardBalancesFromMySql() {
        if (!isShardMysqlEnabled()) {
            return;
        }
        Map<UUID, BigDecimal> mysqlBalances = shardBalanceStore.loadBalances();
        if (mysqlBalances.isEmpty()) {
            shardBalanceStore.saveBalances(shardBalancesSnapshot());
            return;
        }
        for (Map.Entry<UUID, BigDecimal> entry : mysqlBalances.entrySet()) {
            balances.computeIfAbsent(entry.getKey(), id -> new ConcurrentHashMap<>()).put(SHARDS_KEY, entry.getValue());
        }
    }

    private Map<UUID, Map<String, BigDecimal>> localBalancesSnapshot() {
        Map<UUID, Map<String, BigDecimal>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, BigDecimal>> entry : balances.entrySet()) {
            Map<String, BigDecimal> playerBalances = new HashMap<>(entry.getValue());
            if (isShardMysqlEnabled()) {
                playerBalances.remove(SHARDS_KEY);
            }
            snapshot.put(entry.getKey(), playerBalances);
        }
        return snapshot;
    }

    private Map<UUID, BigDecimal> shardBalancesSnapshot() {
        Currency shardCurrency = currencyManager.getCurrency(SHARDS_KEY);
        Map<UUID, BigDecimal> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, BigDecimal>> entry : balances.entrySet()) {
            BigDecimal fallback = shardCurrency == null ? BigDecimal.ZERO : shardCurrency.getStartingBalance();
            snapshot.put(entry.getKey(), entry.getValue().getOrDefault(SHARDS_KEY, fallback));
        }
        return snapshot;
    }

    private boolean isShardCurrency(Currency currency) {
        return currency != null && SHARDS_KEY.equalsIgnoreCase(currency.getName()) && isShardMysqlEnabled();
    }

    private boolean isShardMysqlEnabled() {
        return shardBalanceStore != null && shardBalanceStore.isEnabled();
    }

    private void persistShardBalance(UUID uuid, BigDecimal amount) {
        if (isShardMysqlEnabled()) {
            shardBalanceStore.saveBalanceAsync(uuid, amount);
        }
    }

    private BigDecimal clamp(BigDecimal value, Currency currency) {
        BigDecimal min = currency.getMinMoney();
        BigDecimal max = currency.getMaxMoney();
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }
}
