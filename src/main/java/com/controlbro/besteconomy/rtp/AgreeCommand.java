package com.controlbro.besteconomy.rtp;

import com.controlbro.besteconomy.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AgreeCommand implements CommandExecutor {
    private final RtpService rtpService;

    public AgreeCommand(RtpService rtpService) {
        this.rtpService = rtpService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players can use /agree."));
            return true;
        }
        rtpService.agreeAndUseFirstRtp(player);
        return true;
    }
}
