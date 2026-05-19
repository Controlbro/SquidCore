package com.controlbro.besteconomy.home;

import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HomesCommand implements CommandExecutor {
    private final HomeService homeService;

    public HomesCommand(HomeService homeService) {
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
        List<String> homes = homeService.getHomeNames(player.getUniqueId());
        int maxHomes = homeService.getMaxHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "You have no homes set. Use /sethome [name]. (0/" + maxHomes + ")");
            return true;
        }
        player.sendMessage(ChatColor.GOLD + "Your homes (" + homes.size() + "/" + maxHomes + "): " + ChatColor.YELLOW + String.join(ChatColor.GRAY + ", " + ChatColor.YELLOW, homes));
        return true;
    }
}
