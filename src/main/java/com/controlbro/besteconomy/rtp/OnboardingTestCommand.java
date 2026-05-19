package com.controlbro.besteconomy.rtp;

import com.controlbro.besteconomy.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OnboardingTestCommand implements CommandExecutor {
    private final RtpService rtpService;

    public OnboardingTestCommand(RtpService rtpService) {
        this.rtpService = rtpService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.colorize("&cOnly players can run onboarding tests."));
            return true;
        }
        if (!player.hasPermission("besteconomy.onboarding.test")) {
            sender.sendMessage(ColorUtil.colorize("&cYou do not have permission to run onboarding tests."));
            return true;
        }
        rtpService.resetAgreement(player);
        rtpService.teleportToOnboardingSpawn(player);
        sender.sendMessage(ColorUtil.colorize("&aOnboarding test started. You are now treated as a new player for onboarding/RTP agreement."));
        return true;
    }
}
