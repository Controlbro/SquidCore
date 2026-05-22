package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.BestEconomyPlugin;
import com.controlbro.besteconomy.message.MessageManager;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {
    private final BestEconomyPlugin plugin;
    private final MessageManager messageManager;

    public ReloadCommand(BestEconomyPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.reload")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            messageManager.send(sender, "usage-reload", Map.of("command", label));
            return true;
        }
        plugin.reloadEverything();
        messageManager.send(sender, "reload-complete", null);
        return true;
    }
}
