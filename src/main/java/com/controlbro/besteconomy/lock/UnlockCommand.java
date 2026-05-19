package com.controlbro.besteconomy.lock;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class UnlockCommand implements CommandExecutor {
    private final LockService lockService;
    private final MessageManager messageManager;

    public UnlockCommand(LockService lockService, MessageManager messageManager) {
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
        lockService.toggleUnlockMode(player);
        return true;
    }
}
