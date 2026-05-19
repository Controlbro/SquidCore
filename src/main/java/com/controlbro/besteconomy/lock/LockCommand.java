package com.controlbro.besteconomy.lock;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LockCommand implements CommandExecutor {
    private final LockService lockService;
    private final MessageManager messageManager;

    public LockCommand(LockService lockService, MessageManager messageManager) {
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
        String normalized = label.toLowerCase();
        if (normalized.equals("unlock")) {
            lockService.toggleUnlockMode(player);
            return true;
        }
        if (normalized.equals("trust")) {
            if (args.length != 1) {
                messageManager.send(player, "lock.trust-usage", null);
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                messageManager.send(player, "player-not-found", null);
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                messageManager.send(player, "lock.trust-self", null);
                return true;
            }
            lockService.startTrustMode(player, target.getUniqueId(), target.getName() == null ? args[0] : target.getName());
            return true;
        }
        if (normalized.equals("trustall")) {
            if (args.length != 1) {
                messageManager.send(player, "lock.trustall-usage", null);
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                messageManager.send(player, "player-not-found", null);
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                messageManager.send(player, "lock.trust-self", null);
                return true;
            }
            String targetName = target.getName() == null ? args[0] : target.getName();
            lockService.trustAll(player, target.getUniqueId(), targetName);
            return true;
        }

        lockService.toggleLockMode(player);
        return true;
    }
}
