package com.controlbro.besteconomy.teleport;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {
    private final TeleportService teleportService;

    public BackCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        Location back = teleportService.consumeBack(player);
        if (back == null) {
            player.sendMessage(ChatColor.RED + "No previous location is available.");
            return true;
        }
        player.teleport(back);
        player.sendMessage(ChatColor.GREEN + "Teleported back to your previous location.");
        return true;
    }
}
