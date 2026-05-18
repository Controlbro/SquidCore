package com.controlbro.besteconomy.links;

import com.controlbro.besteconomy.util.ColorUtil;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class LinkCommand implements CommandExecutor {
    private final String configPath;
    private final org.bukkit.plugin.java.JavaPlugin plugin;

    public LinkCommand(org.bukkit.plugin.java.JavaPlugin plugin, String configPath) {
        this.plugin = plugin;
        this.configPath = configPath;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        List<String> lines = plugin.getConfig().getStringList(configPath);
        if (lines.isEmpty()) {
            sender.sendMessage(ColorUtil.colorize("&cThis link command is not configured yet."));
            return true;
        }
        for (String line : lines) {
            sender.sendMessage(ColorUtil.colorize(line));
        }
        return true;
    }
}
