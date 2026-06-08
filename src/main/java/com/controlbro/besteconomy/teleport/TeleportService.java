package com.controlbro.besteconomy.teleport;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TeleportService implements Listener {
    private static final long REQUEST_TIMEOUT_MS = 60_000L;

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, TeleportRequest> incomingRequests = new HashMap<>();
    private final Map<UUID, Location> backLocations = new HashMap<>();
    private final Set<UUID> requestBlocking = new HashSet<>();
    private final Map<UUID, TeleportBan> bans = new HashMap<>();

    public TeleportService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teleport-data.yml");
        load();
    }

    public void sendRequest(Player requester, Player target, boolean here) {
        incomingRequests.put(target.getUniqueId(), new TeleportRequest(requester.getUniqueId(), here, System.currentTimeMillis()));
    }

    public boolean isRequestBlocking(UUID playerId) {
        return requestBlocking.contains(playerId);
    }

    public boolean toggleRequestBlocking(UUID playerId) {
        boolean blocking;
        if (requestBlocking.remove(playerId)) {
            blocking = false;
        } else {
            requestBlocking.add(playerId);
            blocking = true;
        }
        save();
        return blocking;
    }

    public TeleportBan getActiveBan(UUID playerId) {
        TeleportBan ban = bans.get(playerId);
        if (ban != null && ban.expiresAt() > 0 && ban.expiresAt() <= System.currentTimeMillis()) {
            bans.remove(playerId);
            save();
            return null;
        }
        return ban;
    }

    public void ban(UUID playerId, String reason, long expiresAt) {
        bans.put(playerId, new TeleportBan(reason, expiresAt));
        save();
    }

    public boolean unban(UUID playerId) {
        boolean removed = bans.remove(playerId) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public TeleportRequest getRequest(Player target) {
        TeleportRequest request = incomingRequests.get(target.getUniqueId());
        if (request == null) {
            return null;
        }
        if (System.currentTimeMillis() - request.createdAt() > REQUEST_TIMEOUT_MS) {
            incomingRequests.remove(target.getUniqueId());
            return null;
        }
        return request;
    }

    public void clearRequest(Player target) {
        incomingRequests.remove(target.getUniqueId());
    }

    public Player getRequester(Player target) {
        TeleportRequest request = getRequest(target);
        if (request == null) {
            return null;
        }
        return plugin.getServer().getPlayer(request.requester());
    }

    public void rememberBack(Player player) {
        backLocations.put(player.getUniqueId(), player.getLocation().clone());
    }

    public Location consumeBack(Player player) {
        Location location = backLocations.remove(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("request-blocking", requestBlocking.stream().map(UUID::toString).toList());
        for (Map.Entry<UUID, TeleportBan> entry : bans.entrySet()) {
            String path = "bans." + entry.getKey();
            config.set(path + ".reason", entry.getValue().reason());
            config.set(path + ".expires-at", entry.getValue().expiresAt());
        }
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save teleport data: " + exception.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String uuidString : config.getStringList("request-blocking")) {
            try {
                requestBlocking.add(UUID.fromString(uuidString));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved UUIDs.
            }
        }
        ConfigurationSection section = config.getConfigurationSection("bans");
        if (section == null) {
            return;
        }
        for (String uuidString : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                String reason = section.getString(uuidString + ".reason", "No reason provided");
                long expiresAt = section.getLong(uuidString + ".expires-at");
                if (expiresAt == 0 || expiresAt > System.currentTimeMillis()) {
                    bans.put(uuid, new TeleportBan(reason, expiresAt));
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved UUIDs.
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        backLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation().clone());
    }

    public record TeleportRequest(UUID requester, boolean here, long createdAt) {
    }

    public record TeleportBan(String reason, long expiresAt) {
        public boolean permanent() {
            return expiresAt == 0;
        }
    }
}
