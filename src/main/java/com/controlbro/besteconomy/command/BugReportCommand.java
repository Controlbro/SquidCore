package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.DiscordWebhookNotifier;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BugReportCommand implements CommandExecutor {
    private final DiscordWebhookNotifier webhookNotifier;
    private final MessageManager messageManager;

    public BugReportCommand(DiscordWebhookNotifier webhookNotifier, MessageManager messageManager) {
        this.webhookNotifier = webhookNotifier;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "players-only", null);
            return true;
        }
        if (args.length == 0) {
            messageManager.send(sender, "bugreport.usage", null);
            return true;
        }
        String report = String.join(" ", args).trim();
        if (report.isBlank()) {
            messageManager.send(sender, "bugreport.usage", null);
            return true;
        }
        String payload = "🐞 Bug report from " + player.getName() + " (" + player.getUniqueId() + "):\n" + report;
        webhookNotifier.send("webhooks.bug-reports", payload);
        messageManager.send(player, "bugreport.sent", Map.of("report", report));
        return true;
    }
}
