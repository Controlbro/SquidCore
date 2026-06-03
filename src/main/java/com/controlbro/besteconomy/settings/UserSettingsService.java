package com.controlbro.besteconomy.settings;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class UserSettingsService {
    private static final GameRule<Boolean> KEEP_INVENTORY_RULE = GameRule.getByName("keepInventory");
    private final JavaPlugin plugin;
    private final File file;
    private final Set<UUID> scoreboardDisabled = new HashSet<>();
    private final Set<UUID> autoLockDisabled = new HashSet<>();
    private boolean keepInventory;
    private boolean pvp;

    public UserSettingsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
        load();
        applyWorldSettings();
    }

    public boolean isScoreboardEnabled(UUID uuid) {
        return !scoreboardDisabled.contains(uuid);
    }

    public boolean toggleScoreboard(UUID uuid) {
        if (scoreboardDisabled.remove(uuid)) {
            save();
            return true;
        }
        scoreboardDisabled.add(uuid);
        save();
        return false;
    }

    public boolean isAutoLockEnabled(UUID uuid) {
        return !autoLockDisabled.contains(uuid);
    }

    public boolean toggleAutoLock(UUID uuid) {
        if (autoLockDisabled.remove(uuid)) {
            save();
            return true;
        }
        autoLockDisabled.add(uuid);
        save();
        return false;
    }

    public boolean isKeepInventory() {
        return keepInventory;
    }

    public boolean toggleKeepInventory() {
        keepInventory = !keepInventory;
        applyWorldSettings();
        save();
        return keepInventory;
    }

    public boolean isPvp() {
        return pvp;
    }

    public boolean togglePvp() {
        pvp = !pvp;
        applyWorldSettings();
        save();
        return pvp;
    }

    public void applyWorldSettings() {
        for (World world : Bukkit.getWorlds()) {
            if (KEEP_INVENTORY_RULE != null) {
                world.setGameRule(KEEP_INVENTORY_RULE, keepInventory);
            }
            world.setPVP(pvp);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("keep-inventory", keepInventory);
        config.set("pvp", pvp);
        config.set("scoreboard-disabled", scoreboardDisabled.stream().map(UUID::toString).toList());
        config.set("auto-lock-disabled", autoLockDisabled.stream().map(UUID::toString).toList());
        try {
            config.save(file);
        } catch (IOException ignored) {
            // ignored
        }
    }

    private void load() {
        if (!file.exists()) {
            keepInventory = plugin.getConfig().getBoolean("settings.keep-inventory-default", false);
            pvp = plugin.getConfig().getBoolean("settings.pvp-default", true);
            save();
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        keepInventory = config.getBoolean("keep-inventory", plugin.getConfig().getBoolean("settings.keep-inventory-default", false));
        pvp = config.getBoolean("pvp", plugin.getConfig().getBoolean("settings.pvp-default", true));
        scoreboardDisabled.clear();
        autoLockDisabled.clear();
        for (String uuidString : config.getStringList("scoreboard-disabled")) {
            try {
                scoreboardDisabled.add(UUID.fromString(uuidString));
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }
        for (String uuidString : config.getStringList("auto-lock-disabled")) {
            try {
                autoLockDisabled.add(UUID.fromString(uuidString));
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }
    }
}
