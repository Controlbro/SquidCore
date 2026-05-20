package com.controlbro.besteconomy.teleport;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor, TabCompleter {
    private final TeleportService teleportService;
    private final boolean here;

    public TpaCommand(TeleportService teleportService, boolean here) {
        this.teleportService = teleportService;
        this.here = here;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ChatColor.RED + "That player is not online.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(ChatColor.RED + "You cannot send a teleport request to yourself.");
            return true;
        }
        teleportService.sendRequest(player, target, here);
        target.sendMessage(ChatColor.GOLD + player.getName() + ChatColor.YELLOW + (here ? " requested you teleport to them." : " requested to teleport to you."));
        target.sendMessage(ChatColor.YELLOW + "Use /tpaccept to accept or /tpdeny to deny.");
        player.sendMessage(ChatColor.GREEN + "Teleport request sent to " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String query = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(query)).toList();
    }
}
