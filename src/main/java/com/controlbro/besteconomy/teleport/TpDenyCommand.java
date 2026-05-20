package com.controlbro.besteconomy.teleport;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpDenyCommand implements CommandExecutor {
    private final TeleportService teleportService;

    public TpDenyCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player target)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        Player requester = teleportService.getRequester(target);
        teleportService.clearRequest(target);
        if (requester != null) {
            requester.sendMessage(ChatColor.RED + target.getName() + " denied your teleport request.");
        }
        target.sendMessage(ChatColor.YELLOW + "Teleport request denied.");
        return true;
    }
}
