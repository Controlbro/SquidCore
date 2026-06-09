package com.controlbro.besteconomy.chat;

import com.controlbro.besteconomy.BestEconomyPlugin;
import com.controlbro.besteconomy.player.NicknameService;
import com.controlbro.besteconomy.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatService implements Listener {
    private final BestEconomyPlugin plugin;
    private final NicknameService nicknameService;
    private final File dataFile;
    private final Map<UUID, String> selectedTags = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> grantedTags = new ConcurrentHashMap<>();
    private FileConfiguration tagsConfig;

    public ChatService(BestEconomyPlugin plugin, NicknameService nicknameService) {
        this.plugin = plugin;
        this.nicknameService = nicknameService;
        File configFile = new File(plugin.getDataFolder(), "tags.yml");
        boolean newConfig = !configFile.exists();
        if (newConfig) plugin.saveResource("tags.yml", false);
        tagsConfig = YamlConfiguration.loadConfiguration(configFile);
        if (newConfig) migrateLegacyTags(configFile);
        dataFile = new File(plugin.getDataFolder(), "tags-data.yml");
        loadData();
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("chat.formatting-enabled", true)) return;
        Player player = event.getPlayer();
        String format = plugin.getConfig().getString("chat.format", "{luckperms_prefix}&r{username}{tag} &7>> &f{message}");
        String message = LegacyComponentSerializer.legacySection().serialize(event.message());
        String rendered = format.replace("{luckperms_prefix}", resolvePrefix(player)).replace("{username}", nicknameService.displayName(player)).replace("{tag}", renderTag(player)).replace("{message}", message);
        Component formatted = ColorUtil.colorize(rendered);
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(formatted));
    }

    public List<TagOption> availableTags(Player player) {
        Set<String> granted = grantedTags.getOrDefault(player.getUniqueId(), Set.of());
        List<TagOption> tags = new ArrayList<>();
        ConfigurationSection section = tagsConfig.getConfigurationSection("tags");
        if (section == null) return tags;
        for (String key : section.getKeys(false)) {
            String normalized = normalize(key);
            String permission = section.getString(key + ".permission", "");
            if (!permission.isBlank() && !player.hasPermission(permission) && !granted.contains(normalized)) continue;
            tags.add(new TagOption(normalized, section.getString(key + ".display", key)));
        }
        tags.sort(Comparator.comparing(TagOption::key));
        return tags;
    }

    public boolean tagExists(String key) {
        return tagsConfig.isConfigurationSection("tags." + normalize(key));
    }

    public boolean giveTag(OfflinePlayer player, String key) {
        String normalized = normalize(key);
        if (!tagExists(normalized)) return false;
        grantedTags.computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet()).add(normalized);
        saveData();
        return true;
    }

    public List<String> tagKeys() {
        ConfigurationSection section = tagsConfig.getConfigurationSection("tags");
        if (section == null) return List.of();
        return section.getKeys(false).stream().map(this::normalize).sorted().toList();
    }

    public void setTag(Player player, String key) {
        selectedTags.put(player.getUniqueId(), normalize(key));
        saveData();
    }

    public void clearTag(Player player) {
        selectedTags.remove(player.getUniqueId());
        saveData();
    }

    public String selectedTag(Player player) {
        return selectedTags.get(player.getUniqueId());
    }

    public int guiSize() {
        int configured = tagsConfig.getInt("gui.size", 54);
        return Math.max(18, Math.min(54, ((configured + 8) / 9) * 9));
    }

    public String guiTitle() {
        return tagsConfig.getString("gui.title", "&8Chat Tags");
    }

    public void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        selectedTags.forEach((uuid, tag) -> data.set("players." + uuid + ".equipped", tag));
        grantedTags.forEach((uuid, tags) -> data.set("players." + uuid + ".granted", new ArrayList<>(tags)));
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save tags-data.yml: " + ex.getMessage());
        }
    }


    private void migrateLegacyTags(File configFile) {
        ConfigurationSection legacy = plugin.getConfig().getConfigurationSection("chat.tags");
        if (legacy == null) return;
        legacy.getValues(true).forEach((path, value) -> {
            if (!(value instanceof ConfigurationSection)) tagsConfig.set("tags." + path, value);
        });
        try {
            tagsConfig.save(configFile);
            plugin.getLogger().info("Moved legacy chat.tags configuration to tags.yml.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not migrate legacy tags to tags.yml: " + ex.getMessage());
        }
    }

    private void loadData() {
        if (!dataFile.exists()) return;
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String equipped = data.getString("players." + rawUuid + ".equipped");
                if (equipped != null && tagExists(equipped)) selectedTags.put(uuid, normalize(equipped));
                Set<String> granted = new HashSet<>();
                for (String key : data.getStringList("players." + rawUuid + ".granted")) {
                    if (tagExists(key)) granted.add(normalize(key));
                }
                if (!granted.isEmpty()) grantedTags.put(uuid, ConcurrentHashMap.newKeySet(granted.size()));
                if (!granted.isEmpty()) grantedTags.get(uuid).addAll(granted);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid player UUID in tags-data.yml: " + rawUuid);
            }
        }
    }

    private String resolvePrefix(Player player) { return plugin.getConfig().getString("chat.fallback-prefix", ""); }

    public String renderedTag(Player player) { return renderTag(player).trim(); }

    private String renderTag(Player player) {
        String key = selectedTags.get(player.getUniqueId());
        if (key == null) return "";
        String display = tagsConfig.getString("tags." + key + ".display", "");
        return display.isBlank() ? "" : " " + display;
    }

    private String normalize(String key) { return key.toLowerCase(Locale.ROOT); }

    public record TagOption(String key, String display) {}
}
