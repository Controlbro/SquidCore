package com.controlbro.besteconomy.shop;

import com.controlbro.besteconomy.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ShardAnnounceCommand implements CommandExecutor {
    private final JavaPlugin plugin;

    public ShardAnnounceCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("shop.admin")) {
            sender.sendMessage(ColorUtil.colorize("&cYou do not have permission."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&eUsage: /shardannounce <player> <item>"));
            return true;
        }
        String player = args[0];
        String item = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String format = plugin.getConfig().getString("webshop.shardannounce-format", "&#A855F7✦ &d{player} &fpurchased &d{item} &fwith their shards!");
        Bukkit.broadcast(ColorUtil.colorize(format.replace("{player}", player).replace("{item}", item)));
        return true;
    }
}
