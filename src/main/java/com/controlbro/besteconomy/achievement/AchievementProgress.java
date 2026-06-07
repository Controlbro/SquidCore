package com.controlbro.besteconomy.achievement;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

public class AchievementProgress {
    final Set<String> completed = new HashSet<>();
    long blocksPlaced;
    long dragonSummons;
    long dragonKills;
    long sleeplessNights;
    long activeNight = -1;
    BigDecimal lifetimeShards = BigDecimal.ZERO;
    boolean marketListingCreated;
}
