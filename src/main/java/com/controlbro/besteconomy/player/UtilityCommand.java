package com.controlbro.besteconomy.player;

import com.controlbro.besteconomy.message.MessageManager;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class UtilityCommand implements CommandExecutor {
    private final MessageManager messageManager;

    public UtilityCommand(MessageManager messageManager) {
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        return switch (command.getName().toLowerCase()) {
            case "workbench" -> openWorkbench(player);
            case "enderchest" -> openEnderChest(player);
            case "hat" -> equipHat(player);
            default -> false;
        };
    }

    private boolean openWorkbench(Player player) {
        if (!checkPermission(player, "besteconomy.workbench")) {
            return true;
        }
        player.openInventory(Bukkit.createInventory(player, InventoryType.WORKBENCH));
        return true;
    }

    private boolean openEnderChest(Player player) {
        if (!checkPermission(player, "besteconomy.enderchest")) {
            return true;
        }
        player.openInventory(player.getEnderChest());
        return true;
    }

    private boolean equipHat(Player player) {
        if (!checkPermission(player, "besteconomy.hat")) {
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR || !held.getType().isBlock()) {
            player.sendMessage("§cHold a block in your main hand to use it as a hat.");
            return true;
        }
        ItemStack previousHelmet = player.getInventory().getHelmet();
        ItemStack hat = held.clone();
        hat.setAmount(1);
        if (held.getAmount() == 1) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        } else {
            held.setAmount(held.getAmount() - 1);
        }
        player.getInventory().setHelmet(hat);
        if (previousHelmet != null && previousHelmet.getType() != Material.AIR) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(previousHelmet);
            overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        player.sendMessage("§aEquipped the held block as your hat.");
        return true;
    }

    private boolean checkPermission(Player player, String permission) {
        if (player.hasPermission(permission)) {
            return true;
        }
        messageManager.send(player, "no-permission", null);
        return false;
    }
}
