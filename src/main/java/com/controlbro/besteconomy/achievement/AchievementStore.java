package com.controlbro.besteconomy.achievement;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class AchievementStore {
    private final JavaPlugin plugin;
    private final File file;

    public AchievementStore(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "achievement-progress.yml");
    }

    public Map<UUID, AchievementProgress> load() {
        Map<UUID, AchievementProgress> result = new HashMap<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = config.getConfigurationSection("players");
        if (players == null) return result;
        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                ConfigurationSection section = players.getConfigurationSection(rawUuid);
                if (section == null) continue;
                AchievementProgress progress = new AchievementProgress();
                progress.completed.addAll(section.getStringList("completed"));
                progress.blocksPlaced = section.getLong("blocks-placed");
                progress.dragonSummons = section.getLong("dragon-summons");
                progress.dragonKills = section.getLong("dragon-kills");
                progress.sleeplessNights = section.getLong("sleepless-nights");
                progress.activeNight = section.getLong("active-night", -1);
                progress.lifetimeShards = new BigDecimal(section.getString("lifetime-shards", "0"));
                progress.marketListingCreated = section.getBoolean("market-listing-created");
                result.put(uuid, progress);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring invalid achievement player data: " + rawUuid);
            }
        }
        return result;
    }

    public void save(Map<UUID, AchievementProgress> progressByPlayer) {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection players = config.createSection("players");
        for (Map.Entry<UUID, AchievementProgress> entry : progressByPlayer.entrySet()) {
            AchievementProgress progress = entry.getValue();
            ConfigurationSection section = players.createSection(entry.getKey().toString());
            section.set("completed", progress.completed.stream().sorted().toList());
            section.set("blocks-placed", progress.blocksPlaced);
            section.set("dragon-summons", progress.dragonSummons);
            section.set("dragon-kills", progress.dragonKills);
            section.set("sleepless-nights", progress.sleeplessNights);
            section.set("active-night", progress.activeNight);
            section.set("lifetime-shards", progress.lifetimeShards.toPlainString());
            section.set("market-listing-created", progress.marketListingCreated);
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save achievement progress: " + ex.getMessage());
        }
    }
}
