package com.controlbro.besteconomy.lock;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TrustCommand implements CommandExecutor, TabCompleter {
    private final LockService lockService;
    private final MessageManager messageManager;

    public TrustCommand(LockService lockService, MessageManager messageManager) {
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
        if (args.length == 0) {
            if (lockService.isTrustModeActive(player)) {
                lockService.toggleTrustMode(player, player.getUniqueId(), player.getName());
            } else {
                messageManager.send(player, "lock.trust-usage", null);
            }
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messageManager.send(player, "lock.cannot-trust-self", null);
            return true;
        }
        lockService.toggleTrustMode(player, target.getUniqueId(), target.getName() == null ? args[0] : target.getName());
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        List<String> completions = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.getName().toLowerCase().startsWith(prefix)) {
                completions.add(player.getName());
            }
        });
        return completions;
    }
}
