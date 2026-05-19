package com.controlbro.besteconomy.home;

import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class DelHomeCommand implements CommandExecutor, TabCompleter {
    private final HomeService homeService;

    public DelHomeCommand(HomeService homeService) {
        this.homeService = homeService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("besteconomy.home.use")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage: /delhome <name>");
            return true;
        }
        String homeName = args[0];
        if (!homeService.deleteHome(player.getUniqueId(), homeName)) {
            player.sendMessage(ChatColor.RED + "No home named '" + homeName.toLowerCase(Locale.ROOT) + "' exists.");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + "Deleted home '" + homeName.toLowerCase(Locale.ROOT) + "'.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        String query = args[0].toLowerCase(Locale.ROOT);
        return homeService.getHomeNames(player.getUniqueId()).stream()
            .filter(name -> name.startsWith(query))
            .toList();
    }
}
