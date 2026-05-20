package com.controlbro.besteconomy.teleport;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpAcceptCommand implements CommandExecutor {
    private final TeleportService teleportService;

    public TpAcceptCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        TeleportService.TeleportRequest request = teleportService.getRequest(target);
        Player requester = teleportService.getRequester(target);
        if (request == null || requester == null) {
            target.sendMessage(ChatColor.RED + "You have no pending teleport requests.");
            return true;
        }
        if (request.here()) {
            teleportService.rememberBack(target);
            target.teleport(requester.getLocation());
        } else {
            teleportService.rememberBack(requester);
            requester.teleport(target.getLocation());
        }
        teleportService.clearRequest(target);
        target.sendMessage(ChatColor.GREEN + "Teleport request accepted.");
        requester.sendMessage(ChatColor.GREEN + target.getName() + " accepted your teleport request.");
        return true;
    }
}
