package com.controlbro.besteconomy.rtp;

import com.controlbro.besteconomy.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResetRtpCommand implements CommandExecutor, TabCompleter {
    private final RtpService rtpService;

    public ResetRtpCommand(RtpService rtpService) {
        this.rtpService = rtpService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("besteconomy.rtp.reset")) {
            sender.sendMessage(ColorUtil.colorize("&cYou do not have permission to reset RTP uses."));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: /resetrtp <player>"));
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        rtpService.reset(sender, target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (args.length != 1 || !sender.hasPermission("besteconomy.rtp.reset")) {
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
