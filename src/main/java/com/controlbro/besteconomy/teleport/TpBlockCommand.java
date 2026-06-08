package com.controlbro.besteconomy.teleport;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpBlockCommand implements CommandExecutor {
    private final TeleportService teleportService;

    public TpBlockCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        boolean blocking = teleportService.toggleRequestBlocking(player.getUniqueId());
        player.sendMessage(blocking
                ? ChatColor.YELLOW + "You are now blocking new teleport requests. Use /tpblock again to allow them."
                : ChatColor.GREEN + "You are now accepting new teleport requests.");
        return true;
    }
}
