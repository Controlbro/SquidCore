package com.controlbro.besteconomy.chat;

import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class GiveTagCommand implements CommandExecutor, TabCompleter {
    private final ChatService chatService;
    private final MessageManager messageManager;

    public GiveTagCommand(ChatService chatService, MessageManager messageManager) {
        this.chatService = chatService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.tags.give")) {
            messageManager.send(sender, "no-permission", null);
            return true;
        }
        if (args.length != 2) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: /" + label + " <tag> <player>"));
            return true;
        }
        if (!chatService.tagExists(args[0])) {
            sender.sendMessage(ColorUtil.colorize("&cUnknown tag: &f" + args[0]));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        chatService.giveTag(target, args[0]);
        sender.sendMessage(ColorUtil.colorize("&aGave tag &f" + args[0].toLowerCase(Locale.ROOT) + " &ato &f" + args[1] + "&a."));
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ColorUtil.colorize("&aYou received the &f" + args[0].toLowerCase(Locale.ROOT) + " &atag! Use &f/tags &ato equip it."));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return chatService.tagKeys().stream().filter(tag -> tag.startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}
