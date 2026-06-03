package com.controlbro.besteconomy.vanish;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class VanishService implements Listener {
    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private final Set<UUID> vanished = new HashSet<>();

    public VanishService(JavaPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    public void toggle(Player player) {
        if (vanished.remove(player.getUniqueId())) {
            showToVisiblePlayers(player);
            messageManager.send(player, "vanish.disabled", null);
            return;
        }
        vanished.add(player.getUniqueId());
        hideFromVisiblePlayers(player);
        messageManager.send(player, "vanish.enabled", null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (Player other : Bukkit.getOnlinePlayers()) {
            applyVisibility(other, joining);
            applyVisibility(joining, other);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        vanished.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                applyVisibility(viewer, event.getPlayer());
            }
        });
    }

    private void hideFromVisiblePlayers(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target) && !viewer.hasPermission("besteconomy.vanish.see")) {
                viewer.hidePlayer(plugin, target);
            }
        }
    }

    private void showToVisiblePlayers(Player target) {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(target)) {
                applyVisibility(viewer, target);
            }
        }
    }

    private void applyVisibility(Player viewer, Player target) {
        if (viewer.equals(target)) {
            return;
        }
        if (shouldHideFrom(viewer, target)) {
            viewer.hidePlayer(plugin, target);
            return;
        }
        viewer.showPlayer(plugin, target);
    }

    private boolean shouldHideFrom(Player viewer, Player target) {
        if (viewer.hasPermission("besteconomy.vanish.see")) {
            return false;
        }
        return vanished.contains(target.getUniqueId()) || target.getGameMode() == GameMode.SPECTATOR;
    }
}
