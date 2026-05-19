package com.controlbro.besteconomy.home;

import java.util.List;
import java.util.Locale;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class HomeCommand implements CommandExecutor, TabCompleter {
    private final HomeService homeService;

    public HomeCommand(HomeService homeService) {
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
        String homeName = args.length > 0 ? args[0] : "home";
        Location home = homeService.getHome(player.getUniqueId(), homeName);
        if (home == null) {
            player.sendMessage(ChatColor.RED + "You do not have a home named '" + homeName.toLowerCase(Locale.ROOT) + "'.");
            return true;
        }
        player.teleport(home);
        player.sendMessage(ChatColor.GREEN + "Teleported to home '" + homeName.toLowerCase(Locale.ROOT) + "'.");
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
