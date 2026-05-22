package com.controlbro.besteconomy.placeholder;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class InternalPlaceholderService {
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final Map<UUID, Long> sessionStartTimes = new HashMap<>();

    public InternalPlaceholderService(EconomyManager economyManager, CurrencyManager currencyManager) {
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
    }

    public String apply(String message, CommandSender sender, Map<String, String> placeholders) {
        String result = message == null ? "" : message;
        Map<String, String> merged = new HashMap<>();
        if (sender instanceof Player player) {
            merged.putAll(playerPlaceholders(player));
        } else {
            merged.putAll(serverPlaceholders());
        }
        if (placeholders != null) {
            merged.putAll(placeholders);
        }
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    public Map<String, String> playerPlaceholders(Player player) {
        Map<String, String> placeholders = serverPlaceholders();
        String money = formattedBalance(player, "money");
        String shards = formattedBalance(player, "shards");
        String playtime = formattedPlaytime(player);
        String ping = String.valueOf(ping(player));
        String sessionTime = formattedSessionTime(player);
        placeholders.put("player", player.getName());
        placeholders.put("Player", player.getName());
        placeholders.put("money", money);
        placeholders.put("Money", money);
        placeholders.put("shards", shards);
        placeholders.put("Shards", shards);
        placeholders.put("playtime", playtime);
        placeholders.put("Playtime", playtime);
        placeholders.put("ping", ping);
        placeholders.put("Ping", ping);
        placeholders.put("session_time", sessionTime);
        placeholders.put("sessiontime", sessionTime);
        placeholders.put("SessionTime", sessionTime);
        return placeholders;
    }

    public Map<String, String> serverPlaceholders() {
        Map<String, String> placeholders = new HashMap<>();
        String tps = formattedTps();
        String online = String.valueOf(Bukkit.getOnlinePlayers().size());
        String maxPlayers = String.valueOf(Bukkit.getMaxPlayers());
        placeholders.put("tps", tps);
        placeholders.put("TPS", tps);
        placeholders.put("players_online", online);
        placeholders.put("online", online);
        placeholders.put("max_players", maxPlayers);
        return placeholders;
    }

    private String formattedBalance(Player player, String currencyName) {
        Currency currency = currencyManager.getCurrency(currencyName);
        if (currency == null) {
            return "0";
        }
        return NumberUtil.format(economyManager.getBalance(player.getUniqueId(), currency));
    }

    private String formattedPlaytime(Player player) {
        int ticks = player.getStatistic(playtimeStatistic());
        long seconds = ticks / 20L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m";
        }
        return seconds + "s";
    }

    private Statistic playtimeStatistic() {
        for (String statisticName : new String[] {"PLAY_TIME", "PLAY_ONE_MINUTE"}) {
            for (Statistic statistic : Statistic.values()) {
                if (statistic.name().equals(statisticName)) {
                    return statistic;
                }
            }
        }
        throw new IllegalStateException("No Bukkit playtime statistic is available.");
    }

    public void startSession(Player player) {
        sessionStartTimes.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void endSession(Player player) {
        sessionStartTimes.remove(player.getUniqueId());
    }

    private String formattedSessionTime(Player player) {
        long now = System.currentTimeMillis();
        long start = sessionStartTimes.computeIfAbsent(player.getUniqueId(), ignored -> now);
        long elapsedSeconds = Math.max(0L, (now - start) / 1000L);
        long hours = elapsedSeconds / 3600L;
        long minutes = (elapsedSeconds % 3600L) / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private int ping(Player player) {
        try {
            Method method = player.getClass().getMethod("getPing");
            Object value = method.invoke(player);
            if (value instanceof Number number) {
                return number.intValue();
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            // ignored
        }
        return 0;
    }

    private String formattedTps() {
        double tps = currentTps();
        if (tps > 20.0D) {
            tps = 20.0D;
        }
        return BigDecimal.valueOf(tps).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private double currentTps() {
        try {
            Method method = Bukkit.class.getMethod("getTPS");
            Object value = method.invoke(null);
            if (value instanceof double[] tpsValues && tpsValues.length > 0) {
                return tpsValues[0];
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            // ignored
        }
        return 20.0D;
    }
}
