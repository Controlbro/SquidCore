package com.controlbro.besteconomy.home;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetHomeCommand implements CommandExecutor {
    private final HomeService homeService;

    public SetHomeCommand(HomeService homeService) {
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
        boolean exists = homeService.getHome(player.getUniqueId(), homeName) != null;
        if (!homeService.setHome(player, homeName)) {
            player.sendMessage(ChatColor.RED + "You have reached your home limit (" + homeService.getHomeCount(player.getUniqueId()) + "/" + homeService.getMaxHomes(player.getUniqueId()) + ").");
            return true;
        }
        player.sendMessage(ChatColor.GREEN + (exists ? "Updated" : "Set") + " home '" + homeName.toLowerCase() + "'.");
        return true;
    }
}
