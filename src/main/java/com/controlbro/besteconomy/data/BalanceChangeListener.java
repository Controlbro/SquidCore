package com.controlbro.besteconomy.data;

import com.controlbro.besteconomy.currency.Currency;
import java.math.BigDecimal;
import java.util.UUID;

@FunctionalInterface
public interface BalanceChangeListener {
    void onBalanceChanged(UUID uuid, Currency currency, BigDecimal oldBalance, BigDecimal newBalance, BigDecimal earnedAmount);
}
