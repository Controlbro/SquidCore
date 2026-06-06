package com.controlbro.besteconomy.command;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class GameModeCommand implements CommandExecutor, TabCompleter {
    private static final Map<String, GameMode> MODES = Map.ofEntries(
        Map.entry("survival", GameMode.SURVIVAL),
        Map.entry("s", GameMode.SURVIVAL),
        Map.entry("creative", GameMode.CREATIVE),
        Map.entry("c", GameMode.CREATIVE),
        Map.entry("spectator", GameMode.SPECTATOR),
        Map.entry("sp", GameMode.SPECTATOR),
        Map.entry("adventure", GameMode.ADVENTURE),
        Map.entry("a", GameMode.ADVENTURE)
    );

    private final MessageManager messageManager;

    public GameModeCommand(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        GameMode mode = shortcutMode(command.getName());
        if (mode == null) {
            if (args.length != 1) {
                player.sendMessage("§cUsage: /" + label + " <survival|creative|spectator|adventure>");
                return true;
            }
            mode = MODES.get(args[0].toLowerCase(Locale.ROOT));
        }
        if (mode == null) {
            player.sendMessage("§cUnknown game mode. Use survival, creative, spectator, or adventure.");
            return true;
        }
        if (!player.hasPermission("besteconomy.gamemode." + mode.name().toLowerCase(Locale.ROOT))) {
            messageManager.send(player, "no-permission", null);
            return true;
        }

        player.setGameMode(mode);
        player.sendMessage("§aGame mode set to §f" + mode.name().toLowerCase(Locale.ROOT) + "§a.");
        return true;
    }

    private GameMode shortcutMode(String commandName) {
        return switch (commandName.toLowerCase(Locale.ROOT)) {
            case "gms" -> GameMode.SURVIVAL;
            case "gmc" -> GameMode.CREATIVE;
            case "gmsp" -> GameMode.SPECTATOR;
            case "gma" -> GameMode.ADVENTURE;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("gamemode") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("survival", "creative", "spectator", "adventure", "s", "c", "sp", "a").stream()
            .filter(option -> option.startsWith(prefix))
            .toList();
    }
}
