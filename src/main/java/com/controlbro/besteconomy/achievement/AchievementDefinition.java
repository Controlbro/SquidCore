package com.controlbro.besteconomy.achievement;

import java.math.BigDecimal;
import org.bukkit.Material;

public record AchievementDefinition(String id, String name, String description, Material icon, String frame,
                                    String parent, String type, BigDecimal target) {
}
