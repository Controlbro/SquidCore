package com.controlbro.besteconomy.rtp;

import com.controlbro.besteconomy.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SetOnboardingSpawnCommand implements CommandExecutor {
    private final RtpService rtpService;

    public SetOnboardingSpawnCommand(RtpService rtpService) {
        this.rtpService = rtpService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players can set the onboarding spawn."));
            return true;
        }
        if (!player.hasPermission("besteconomy.onboarding.setspawn")) {
            sender.sendMessage(ColorUtil.colorize("&cYou do not have permission."));
            return true;
        }
        rtpService.setOnboardingSpawn(player.getLocation());
        sender.sendMessage(ColorUtil.colorize("&aOnboarding spawn set to your current location."));
        return true;
    }
}
