package com.controlbro.besteconomy.lock;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TrustAllCommand implements CommandExecutor {
    private final LockService lockService;
    private final MessageManager messageManager;

    public TrustAllCommand(LockService lockService, MessageManager messageManager) {
        this.lockService = lockService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.lock.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        if (args.length != 1) {
            messageManager.send(player, "lock.trustall-usage", null);
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messageManager.send(player, "lock.cannot-trust-self", null);
            return true;
        }
        lockService.trustAll(player, target.getUniqueId(), target.getName() == null ? args[0] : target.getName());
        return true;
    }
}
