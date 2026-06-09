package com.controlbro.besteconomy.player;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ResetNickCommand implements CommandExecutor, TabCompleter {
    private final NicknameService nicknameService;
    private final MessageManager messageManager;

    public ResetNickCommand(NicknameService nicknameService, MessageManager messageManager) {
        this.nicknameService = nicknameService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.nick.reset")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (args.length == 0 && sender instanceof Player player) {
            reset(sender, player, true);
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage("§cUsage: /" + label + " [player]");
            return true;
        }
        if (!sender.hasPermission("besteconomy.nick.reset.others")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cThat player has not played before.");
            return true;
        }
        reset(sender, target, sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId()));
        return true;
    }

    private void reset(CommandSender sender, OfflinePlayer target, boolean self) {
        if (!nicknameService.resetNickname(target)) {
            sender.sendMessage(self ? "§cYou do not have a nickname." : "§cThat player does not have a nickname.");
            return;
        }
        sender.sendMessage(self ? "§aYour nickname was reset." : "§aReset §f" + target.getName() + "§a's nickname.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("besteconomy.nick.reset.others") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase().startsWith(prefix)).sorted().toList();
    }
}
