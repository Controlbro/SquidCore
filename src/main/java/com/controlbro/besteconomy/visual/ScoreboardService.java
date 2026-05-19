package com.controlbro.besteconomy.visual;

import com.controlbro.besteconomy.placeholder.InternalPlaceholderService;
import com.controlbro.besteconomy.rtp.RtpService;
import com.controlbro.besteconomy.settings.UserSettingsService;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public class ScoreboardService implements Listener {
    private static final String CONFIG_PATH = "scoreboard.yml";
    private static final String OBJECTIVE_NAME = "besteconomy";
    private final JavaPlugin plugin;
    private final InternalPlaceholderService placeholderService;
    private final UserSettingsService userSettingsService;
    private final RtpService rtpService;
    private YamlConfiguration config;
    private BukkitTask updateTask;

    public ScoreboardService(JavaPlugin plugin, InternalPlaceholderService placeholderService, UserSettingsService userSettingsService, RtpService rtpService) {
        this.plugin = plugin;
        this.placeholderService = placeholderService;
        this.userSettingsService = userSettingsService;
        this.rtpService = rtpService;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), CONFIG_PATH);
        if (!file.exists()) {
            plugin.saveResource(CONFIG_PATH, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        stop();
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long intervalTicks = Math.max(1L, config.getLong("update-interval-ticks", 40L));
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, intervalTicks);
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getScoreboard().getObjective(OBJECTIVE_NAME) != null) {
                    player.setScoreboard(manager.getMainScoreboard());
                }
            }
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> update(event.getPlayer()), 1L);
    }

    private void update(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }
        if (rtpService != null && !rtpService.hasAgreed(player)) {
            player.setScoreboard(manager.getMainScoreboard());
            return;
        }
        if (userSettingsService != null && !userSettingsService.isScoreboardEnabled(player.getUniqueId())) {
            player.setScoreboard(manager.getMainScoreboard());
            return;
        }
        Scoreboard scoreboard = manager.getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", legacy(apply(player, config.getString("title", "&aBestEconomy"))));
        hideScoreNumbers(objective);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        List<String> lines = new ArrayList<>(config.getStringList("lines"));
        if (lines.size() > 15) {
            lines = lines.subList(0, 15);
        }
        int scoreValue = lines.size();
        int duplicate = 0;
        for (String line : lines) {
            String entry = uniqueEntry(legacy(apply(player, line)), duplicate++);
            Score score = objective.getScore(entry);
            score.setScore(scoreValue--);
            hideScoreNumbers(score);
        }
        player.setScoreboard(scoreboard);
    }

    private void hideScoreNumbers(Objective objective) {
        Class<?> numberFormatClass = numberFormatClass();
        Object blankFormat = blankNumberFormat(numberFormatClass);
        if (blankFormat == null) {
            return;
        }
        try {
            Objective.class.getMethod("numberFormat", numberFormatClass).invoke(objective, blankFormat);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            // Scoreboard number formatting is only available on newer Paper versions.
        }
    }

    private void hideScoreNumbers(Score score) {
        Class<?> numberFormatClass = numberFormatClass();
        Object blankFormat = blankNumberFormat(numberFormatClass);
        if (blankFormat == null) {
            return;
        }
        try {
            Score.class.getMethod("numberFormat", numberFormatClass).invoke(score, blankFormat);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            // Per-score number formatting is only available on newer Paper versions.
        }
    }

    private Object blankNumberFormat(Class<?> numberFormatClass) {
        if (numberFormatClass == null) {
            return null;
        }
        try {
            return numberFormatClass.getMethod("blank").invoke(null);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ignored) {
            return null;
        }
    }

    private Class<?> numberFormatClass() {
        try {
            return Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private String apply(Player player, String text) {
        return placeholderService.apply(text, player, Map.of("players_online_display", playersOnlineDisplay(player)));
    }

    private String playersOnlineDisplay(Player player) {
        if (!config.getBoolean("players-online.enabled", true)) {
            return "";
        }
        String format = config.getString("players-online.format", "{players_online}/{max_players}");
        return placeholderService.apply(format, player, null);
    }

    private String legacy(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String uniqueEntry(String entry, int duplicate) {
        String suffix = ChatColor.values()[duplicate % ChatColor.values().length].toString();
        String value = entry + suffix;
        if (value.length() <= 40) {
            return value;
        }
        return value.substring(0, 40);
    }
}
