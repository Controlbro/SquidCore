package com.controlbro.besteconomy.player;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class RealNameCommand implements CommandExecutor, TabCompleter {
    private final NicknameService nicknameService;
    private final MessageManager messageManager;

    public RealNameCommand(NicknameService nicknameService, MessageManager messageManager) {
        this.nicknameService = nicknameService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.realname")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /" + label + " <player>");
            return true;
        }
        Player target = nicknameService.findOnlinePlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cThat player is not online.");
            return true;
        }
        String nickname = nicknameService.nickname(target);
        if (nickname == null) {
            sender.sendMessage("§f" + target.getName() + " §7does not have a nickname.");
        } else {
            sender.sendMessage("§r" + nickname + " §7is §f" + target.getName() + "§7.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(prefix)).sorted().toList();
    }
}
