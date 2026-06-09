package com.controlbro.besteconomy.player;

import com.controlbro.besteconomy.message.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NickCommand implements CommandExecutor {
    private final NicknameService nicknameService;
    private final MessageManager messageManager;

    public NickCommand(NicknameService nicknameService, MessageManager messageManager) {
        this.nicknameService = nicknameService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        if (!player.hasPermission("besteconomy.nick")) {
            messageManager.send(player, "no-permission", null);
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /" + label + " <nickname>");
            return true;
        }
        if (nicknameService.setNickname(player, args[0]) == NicknameService.NicknameResult.INVALID) {
            player.sendMessage("§cNicknames must contain 3-16 letters, numbers, or underscores after color codes are removed.");
            return true;
        }
        player.sendMessage("§aYour nickname is now §r" + args[0] + "§a.");
        return true;
    }
}
