package com.controlbro.besteconomy.home;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class HomeService {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Location>> homesByPlayer = new HashMap<>();
    private final Map<UUID, Integer> extraHomesByPlayer = new HashMap<>();

    public HomeService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    public int getMaxHomes(UUID playerId) {
        return 1 + Math.max(0, extraHomesByPlayer.getOrDefault(playerId, 0));
    }

    public int getHomeCount(UUID playerId) {
        return homesByPlayer.getOrDefault(playerId, Map.of()).size();
    }

    public int getExtraHomes(UUID playerId) {
        return Math.max(0, extraHomesByPlayer.getOrDefault(playerId, 0));
    }

    public void addExtraHomes(UUID playerId, int amount) {
        if (amount <= 0) {
            return;
        }
        extraHomesByPlayer.put(playerId, getExtraHomes(playerId) + amount);
        save();
    }

    public boolean setHome(Player player, String rawName) {
        String name = normalizeName(rawName);
        Map<String, Location> homes = homesByPlayer.computeIfAbsent(player.getUniqueId(), key -> new LinkedHashMap<>());
        if (!homes.containsKey(name) && homes.size() >= getMaxHomes(player.getUniqueId())) {
            return false;
        }
        homes.put(name, player.getLocation().clone());
        save();
        return true;
    }

    public boolean deleteHome(UUID playerId, String rawName) {
        String name = normalizeName(rawName);
        Map<String, Location> homes = homesByPlayer.get(playerId);
        if (homes == null) {
            return false;
        }
        Location removed = homes.remove(name);
        if (homes.isEmpty()) {
            homesByPlayer.remove(playerId);
        }
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public Location getHome(UUID playerId, String rawName) {
        String name = normalizeName(rawName);
        Map<String, Location> homes = homesByPlayer.get(playerId);
        if (homes == null) {
            return null;
        }
        Location location = homes.get(name);
        if (location == null) {
            return null;
        }
        return location.clone();
    }

    public List<String> getHomeNames(UUID playerId) {
        Map<String, Location> homes = homesByPlayer.get(playerId);
        if (homes == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(homes.keySet());
        names.sort(Comparator.naturalOrder());
        return names;
    }

    public Location getDefaultRespawnHome(UUID playerId) {
        Map<String, Location> homes = homesByPlayer.get(playerId);
        if (homes == null || homes.isEmpty()) {
            return null;
        }
        Location namedHome = homes.get("home");
        if (namedHome != null) {
            return namedHome.clone();
        }
        return homes.values().iterator().next().clone();
    }

    private String normalizeName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "home";
        }
        return rawName.toLowerCase(Locale.ROOT);
    }

    private void load() {
        homesByPlayer.clear();
        extraHomesByPlayer.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }
        for (String uuidString : playersSection.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection playerSection = playersSection.getConfigurationSection(uuidString);
            if (playerSection == null) {
                continue;
            }
            int extra = Math.max(0, playerSection.getInt("extra-homes", 0));
            if (extra > 0) {
                extraHomesByPlayer.put(uuid, extra);
            }
            ConfigurationSection homesSection = playerSection.getConfigurationSection("homes");
            if (homesSection == null) {
                continue;
            }
            Map<String, Location> homes = new LinkedHashMap<>();
            for (String homeName : homesSection.getKeys(false)) {
                Location location = readLocation(homesSection.getConfigurationSection(homeName));
                if (location != null) {
                    homes.put(homeName.toLowerCase(Locale.ROOT), location);
                }
            }
            if (!homes.isEmpty()) {
                homesByPlayer.put(uuid, homes);
            }
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection playersSection = config.createSection("players");
        for (UUID uuid : homesByPlayer.keySet()) {
            ConfigurationSection playerSection = playersSection.createSection(uuid.toString());
            int extra = getExtraHomes(uuid);
            if (extra > 0) {
                playerSection.set("extra-homes", extra);
            }
            ConfigurationSection homesSection = playerSection.createSection("homes");
            for (Map.Entry<String, Location> entry : homesByPlayer.get(uuid).entrySet()) {
                writeLocation(homesSection.createSection(entry.getKey()), entry.getValue());
            }
        }
        for (Map.Entry<UUID, Integer> entry : extraHomesByPlayer.entrySet()) {
            if (playersSection.isConfigurationSection(entry.getKey().toString())) {
                continue;
            }
            ConfigurationSection playerSection = playersSection.createSection(entry.getKey().toString());
            playerSection.set("extra-homes", entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save homes.yml: " + ex.getMessage());
        }
    }

    private void writeLocation(ConfigurationSection section, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        section.set("world", location.getWorld().getName());
        section.set("x", location.getX());
        section.set("y", location.getY());
        section.set("z", location.getZ());
        section.set("yaw", location.getYaw());
        section.set("pitch", location.getPitch());
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        World world = Bukkit.getWorld(section.getString("world", ""));
        if (world == null) {
            return null;
        }
        return new Location(world,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw"),
            (float) section.getDouble("pitch"));
    }
}
