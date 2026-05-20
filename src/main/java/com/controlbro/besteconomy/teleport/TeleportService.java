package com.controlbro.besteconomy.teleport;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class TeleportService implements Listener {
    private static final long REQUEST_TIMEOUT_MS = 60_000L;

    private final JavaPlugin plugin;
    private final Map<UUID, TeleportRequest> incomingRequests = new HashMap<>();
    private final Map<UUID, Location> backLocations = new HashMap<>();

    public TeleportService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player requester, Player target, boolean here) {
        incomingRequests.put(target.getUniqueId(), new TeleportRequest(requester.getUniqueId(), here, System.currentTimeMillis()));
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

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        backLocations.put(event.getEntity().getUniqueId(), event.getEntity().getLocation().clone());
    }

    public record TeleportRequest(UUID requester, boolean here, long createdAt) {
    }
}
