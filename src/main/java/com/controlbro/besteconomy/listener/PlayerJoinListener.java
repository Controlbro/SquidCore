package com.controlbro.besteconomy.listener;

import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.rtp.RtpService;
import com.controlbro.besteconomy.util.ColorUtil;
import net.kyori.adventure.text.Component;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PlayerJoinListener implements Listener {
    private final EconomyManager economyManager;
    private final MessageManager messageManager;
    private final RtpService rtpService;
    private final JavaPlugin plugin;

    public PlayerJoinListener(JavaPlugin plugin, EconomyManager economyManager, MessageManager messageManager, RtpService rtpService) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.messageManager = messageManager;
        this.rtpService = rtpService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        economyManager.ensurePlayer(event.getPlayer().getUniqueId());
        if (!messageManager.getBoolean("join.enabled", true)) {
            return;
        }
        boolean firstJoin = !event.getPlayer().hasPlayedBefore();
        if (firstJoin) {
            rtpService.teleportToOnboardingSpawn(event.getPlayer());
            showOnboardingJoinScreen(event);
        }
        String messagePath = firstJoin ? "join.first-time-message" : "join.message";
        event.joinMessage(messageManager.getMessage(messagePath, event.getPlayer(), null));
    }

    private void showOnboardingJoinScreen(PlayerJoinEvent event) {
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("rtp.onboarding.join-screen.enabled", true)) {
            return;
        }
        String titleText = config.getString("rtp.onboarding.join-screen.title", "&6Welcome to SheepSquid");
        String subtitleText = config.getString("rtp.onboarding.join-screen.subtitle", "&7Please complete this simple tutorial before playing.");
        Sound titleSound = parseSound(config.getString("rtp.onboarding.join-screen.title-sound", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP);
        Sound subtitleSound = parseSound(config.getString("rtp.onboarding.join-screen.subtitle-sound", "BLOCK_NOTE_BLOCK_PLING"), Sound.BLOCK_NOTE_BLOCK_PLING);

        Component title = ColorUtil.colorize(titleText);
        Component subtitle = ColorUtil.colorize("");
        event.getPlayer().showTitle(net.kyori.adventure.title.Title.title(title, subtitle));
        event.getPlayer().playSound(event.getPlayer().getLocation(), titleSound, 1.0F, 1.0F);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) {
                return;
            }
            event.getPlayer().showTitle(net.kyori.adventure.title.Title.title(ColorUtil.colorize(" "), ColorUtil.colorize(subtitleText)));
            event.getPlayer().playSound(event.getPlayer().getLocation(), subtitleSound, 1.0F, 1.0F);
        }, 30L);
    }

    private Sound parseSound(String value, Sound fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            NamespacedKey key = NamespacedKey.fromString(value.toLowerCase(Locale.ROOT));
            if (key == null) {
                return fallback;
            }
            Sound resolved = Registry.SOUNDS.get(key);
            return resolved != null ? resolved : fallback;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
