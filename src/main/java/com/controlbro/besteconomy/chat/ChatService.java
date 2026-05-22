package com.controlbro.besteconomy.chat;

import com.controlbro.besteconomy.BestEconomyPlugin;
import com.controlbro.besteconomy.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatService implements Listener {
    private final BestEconomyPlugin plugin;
    private final Map<UUID, String> selectedTags = new HashMap<>();

    public ChatService(BestEconomyPlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String format = plugin.getConfig().getString("chat.format", "{luckperms_prefix}&r{username}{tag} &7>> &f{message}");
        String message = LegacyComponentSerializer.legacySection().serialize(event.message());
        String rendered = format.replace("{luckperms_prefix}", resolvePrefix(player)).replace("{username}", player.getName()).replace("{tag}", renderTag(player)).replace("{message}", message);
        Component formatted = ColorUtil.colorize(rendered);
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(formatted));
    }

    public List<TagOption> availableTags(Player player) {
        List<TagOption> tags = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("chat.tags");
        if (section == null) return tags;
        for (String key : section.getKeys(false)) {
            String permission = section.getString(key + ".permission", "");
            if (!permission.isBlank() && !player.hasPermission(permission)) continue;
            String display = section.getString(key + ".display", "");
            tags.add(new TagOption(key, display));
        }
        tags.sort(Comparator.comparing(TagOption::key));
        return tags;
    }

    public void setTag(Player player, String key) { selectedTags.put(player.getUniqueId(), key); }

    private String resolvePrefix(Player player) { return plugin.getConfig().getString("chat.fallback-prefix", ""); }

    private String renderTag(Player player) {
        String key = selectedTags.get(player.getUniqueId());
        if (key == null) return "";
        String display = plugin.getConfig().getString("chat.tags." + key + ".display", "");
        return display.isBlank() ? "" : " " + display;
    }

    public record TagOption(String key, String display) {}
}
