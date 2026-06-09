package com.controlbro.besteconomy.player;

import com.controlbro.besteconomy.util.ColorUtil;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class NicknameService implements Listener {
    private static final Pattern LEGACY_CODE = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEX_CODE = Pattern.compile("&#([0-9a-f]{6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile("<[^>]*>");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private final JavaPlugin plugin;
    private final File dataFile;
    private final Map<UUID, String> nicknames = new ConcurrentHashMap<>();

    public NicknameService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "nickname-data.yml");
        load();
        Bukkit.getOnlinePlayers().forEach(this::apply);
    }

    public NicknameResult setNickname(Player player, String requestedNickname) {
        String sanitized = sanitize(requestedNickname, player.hasPermission("besteconomy.nick.formatting"));
        String plain = plainNickname(sanitized);
        if (!VALID_NAME.matcher(plain).matches()) {
            return NicknameResult.INVALID;
        }
        nicknames.put(player.getUniqueId(), sanitized);
        apply(player);
        save();
        return NicknameResult.SUCCESS;
    }

    public boolean resetNickname(OfflinePlayer player) {
        boolean changed = nicknames.remove(player.getUniqueId()) != null;
        if (player.getPlayer() != null) {
            apply(player.getPlayer());
        }
        if (changed) {
            save();
        }
        return changed;
    }

    public String displayName(Player player) {
        return nicknames.getOrDefault(player.getUniqueId(), player.getName());
    }

    public String nickname(OfflinePlayer player) {
        return nicknames.get(player.getUniqueId());
    }

    public Player findOnlinePlayer(String nameOrNickname) {
        Player realNameMatch = Bukkit.getPlayerExact(nameOrNickname);
        if (realNameMatch != null) {
            return realNameMatch;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String nickname = nicknames.get(player.getUniqueId());
            if (nickname != null && plainNickname(nickname).equalsIgnoreCase(nameOrNickname)) {
                return player;
            }
        }
        return null;
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        nicknames.forEach((uuid, nickname) -> data.set("nicknames." + uuid, nickname));
        try {
            data.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save nickname-data.yml: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    private void apply(Player player) {
        player.displayName(ColorUtil.colorize(displayName(player)));
    }

    private String sanitize(String input, boolean allowFormatting) {
        String withoutTags = MINI_MESSAGE_TAG.matcher(input).replaceAll("");
        String withoutFormatting = allowFormatting ? withoutTags : stripFormattingCodes(withoutTags);
        return normalizeHexCodes(withoutFormatting);
    }

    private String stripFormattingCodes(String input) {
        Matcher matcher = LEGACY_CODE.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            String replacement = "0123456789abcdefr".indexOf(code) >= 0 ? matcher.group() : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizeHexCodes(String input) {
        Matcher matcher = HEX_CODE.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("&#" + matcher.group(1).toLowerCase(Locale.ROOT)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String plainNickname(String nickname) {
        return LEGACY_CODE.matcher(HEX_CODE.matcher(nickname).replaceAll("")).replaceAll("");
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(dataFile).getConfigurationSection("nicknames");
        if (section == null) {
            return;
        }
        for (String rawUuid : section.getKeys(false)) {
            try {
                String nickname = section.getString(rawUuid);
                if (nickname != null && VALID_NAME.matcher(plainNickname(nickname)).matches()) {
                    nicknames.put(UUID.fromString(rawUuid), nickname);
                }
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Ignoring invalid player UUID in nickname-data.yml: " + rawUuid);
            }
        }
    }

    public enum NicknameResult {
        SUCCESS,
        INVALID
    }
}
