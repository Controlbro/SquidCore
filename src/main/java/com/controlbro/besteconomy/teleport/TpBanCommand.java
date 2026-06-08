package com.controlbro.besteconomy.teleport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class TpBanCommand implements CommandExecutor, TabCompleter {
    private final TeleportService teleportService;

    public TpBanCommand(TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("besteconomy.teleport.ban")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("unban")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (teleportService.unban(target.getUniqueId())) {
                sender.sendMessage(ChatColor.GREEN + (target.getName() == null ? args[0] : target.getName()) + " may use teleport requests again.");
            } else {
                sender.sendMessage(ChatColor.YELLOW + args[0] + " is not teleport banned.");
            }
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /tpban <player> <reason> <1h|1d|1w|1m|p>");
            sender.sendMessage(ChatColor.YELLOW + "Unban: /tpban <player> unban");
            return true;
        }

        Long duration = parseDuration(args[args.length - 1]);
        if (duration == null) {
            sender.sendMessage(ChatColor.RED + "Invalid time. Use a number followed by h, d, w, or m, or p for permanent.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        long expiresAt;
        try {
            expiresAt = duration == 0 ? 0 : Math.addExact(System.currentTimeMillis(), duration);
        } catch (ArithmeticException exception) {
            sender.sendMessage(ChatColor.RED + "That teleport ban duration is too large.");
            return true;
        }
        teleportService.ban(target.getUniqueId(), reason, expiresAt);
        String durationText = duration == 0 ? "permanently" : "for " + args[args.length - 1].toLowerCase(Locale.ROOT);
        String targetName = target.getName() == null ? args[0] : target.getName();
        sender.sendMessage(ChatColor.GREEN + "Teleport banned " + targetName + " " + durationText + ". Reason: " + reason);
        if (target.isOnline() && target.getPlayer() != null) {
            target.getPlayer().sendMessage(ChatColor.RED + "You have been banned from sending /tpa and /tpahere requests " + durationText + ". Reason: " + reason);
        }
        return true;
    }

    private Long parseDuration(String input) {
        if (input.equalsIgnoreCase("p")) {
            return 0L;
        }
        if (input.length() < 2) {
            return null;
        }
        long amount;
        try {
            amount = Long.parseLong(input.substring(0, input.length() - 1));
        } catch (NumberFormatException exception) {
            return null;
        }
        if (amount <= 0) {
            return null;
        }
        try {
            return switch (Character.toLowerCase(input.charAt(input.length() - 1))) {
                case 'h' -> Duration.ofHours(amount).toMillis();
                case 'd' -> Duration.ofDays(amount).toMillis();
                case 'w' -> Duration.ofDays(Math.multiplyExact(amount, 7)).toMillis();
                case 'm' -> Duration.ofDays(Math.multiplyExact(amount, 30)).toMillis();
                default -> null;
            };
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("besteconomy.teleport.ban")) {
            return List.of();
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> names = new ArrayList<>();
            Bukkit.getOnlinePlayers().stream().map(player -> player.getName())
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).forEach(names::add);
            return names;
        }
        if (args.length == 2) {
            return List.of("unban").stream().filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of("1h", "1d", "1w", "1m", "p").stream()
                .filter(value -> value.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
}
