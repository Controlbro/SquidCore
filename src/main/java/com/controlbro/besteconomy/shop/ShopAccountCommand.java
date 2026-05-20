package com.controlbro.besteconomy.shop;

import com.controlbro.besteconomy.message.MessageManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopAccountCommand implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final ShopAccountService accountService;
    private final MessageManager messageManager;
    private final Set<UUID> awaitingPassword = ConcurrentHashMap.newKeySet();

    public ShopAccountCommand(JavaPlugin plugin, ShopAccountService accountService, MessageManager messageManager) {
        this.plugin = plugin;
        this.accountService = accountService;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messageManager.send(sender, "shop.players-only", null);
            return true;
        }
        if (!accountService.isEnabled()) {
            messageManager.send(player, "shop.disabled", null);
            return true;
        }
        if (args.length == 0) {
            messageManager.send(player, "shop.shop-link", null);
            messageManager.send(player, "shop.create-instruction", null);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("create")) {
            startAccountSetup(player);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetpassword")) {
            resetPassword(player, args[1]);
            return true;
        }
        messageManager.send(player, "shop.usage", null);
        messageManager.send(player, "shop.create-instruction", null);
        return true;
    }

    private void startAccountSetup(Player player) {
        if (!player.hasPermission("shop.account.use")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        accountService.accountExists(player.getUniqueId()).thenAccept(exists ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (exists) {
                    messageManager.send(player, "shop.account-exists", null);
                    return;
                }
                awaitingPassword.add(player.getUniqueId());
                messageManager.send(player, "shop.password-prompt", null);
            }));
    }

    private void resetPassword(Player player, String password) {
        if (!player.hasPermission("shop.account.reset")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        accountService.resetPassword(player.getUniqueId(), player.getName(), password).thenAccept(success ->
            plugin.getServer().getScheduler().runTask(plugin, () ->
                messageManager.send(player, success ? "shop.password-reset" : "shop.password-reset-failed", null)));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!awaitingPassword.remove(player.getUniqueId())) {
            return;
        }
        // The password is captured from chat and the event is cancelled so it is never broadcast publicly.
        event.setCancelled(true);
        String password = PlainTextComponentSerializer.plainText().serialize(event.message());
        accountService.createAccount(player.getUniqueId(), player.getName(), password).thenAccept(success ->
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    messageManager.send(player, success ? "shop.account-created" : "shop.account-create-failed", null);
                }
            }));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (awaitingPassword.remove(event.getPlayer().getUniqueId())) {
            messageManager.send(event.getPlayer(), "shop.password-cancelled", null);
        }
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shop.account.reset")) {
            return java.util.List.of("create", "resetpassword");
        }
        return java.util.List.of();
    }
}
