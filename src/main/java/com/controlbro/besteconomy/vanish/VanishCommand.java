package com.controlbro.besteconomy.vanish;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {
    private final VanishService vanishService;
    private final MessageManager messageManager;

    public VanishCommand(VanishService vanishService, MessageManager messageManager) {
        this.vanishService = vanishService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "players-only", null);
            return true;
        }
        if (!player.hasPermission("besteconomy.vanish.use")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        vanishService.toggle(player);
        return true;
    }
}
