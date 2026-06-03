package com.controlbro.besteconomy.settings;

import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.ScoreboardManager;

public class SettingsMenuService implements Listener {
    private static final int SIZE = 27;
    private static final int SCOREBOARD_SLOT = 10;
    private static final int AUTO_LOCK_SLOT = 12;
    private static final int KEEP_INVENTORY_SLOT = 14;
    private static final int PVP_SLOT = 16;
    private final UserSettingsService userSettingsService;
    private final MessageManager messageManager;

    public SettingsMenuService(UserSettingsService userSettingsService, MessageManager messageManager) {
        this.userSettingsService = userSettingsService;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        userSettingsService.applyWorldSettings();
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new SettingsHolder(), SIZE, color("&8Settings"));
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(SCOREBOARD_SLOT, item(Material.MAP, "&aScoreboard", List.of(
            "&7Status: " + status(userSettingsService.isScoreboardEnabled(player.getUniqueId())),
            "&7Click to toggle your scoreboard.")));
        inventory.setItem(AUTO_LOCK_SLOT, item(Material.CHEST, "&6Auto Lock", List.of(
            "&7Status: " + status(userSettingsService.isAutoLockEnabled(player.getUniqueId())),
            "&7Automatically lock chests, doors, trapdoors,",
            "&7and other lockable blocks you place.")));
        inventory.setItem(KEEP_INVENTORY_SLOT, item(Material.TOTEM_OF_UNDYING, "&eKeep Inventory", List.of(
            "&7Status: " + status(userSettingsService.isKeepInventory()),
            "&7Click to toggle keep inventory.")));
        inventory.setItem(PVP_SLOT, item(Material.IRON_SWORD, "&cPVP", List.of(
            "&7Status: " + status(userSettingsService.isPvp()),
            "&7Click to toggle PVP.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof SettingsHolder)) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == SCOREBOARD_SLOT) {
            boolean enabled = userSettingsService.toggleScoreboard(player.getUniqueId());
            if (!enabled) {
                clearScoreboard(player);
            }
            messageManager.send(player, "settings.scoreboard", Map.of("status", enabled ? "enabled" : "disabled"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
            open(player);
            return;
        }
        if (slot == AUTO_LOCK_SLOT) {
            boolean enabled = userSettingsService.toggleAutoLock(player.getUniqueId());
            messageManager.send(player, "settings.auto-lock", Map.of("status", enabled ? "enabled" : "disabled"));
            playToggle(player);
            open(player);
            return;
        }
        if (slot == KEEP_INVENTORY_SLOT) {
            boolean enabled = userSettingsService.toggleKeepInventory();
            messageManager.send(player, "settings.keep-inventory", Map.of("status", enabled ? "enabled" : "disabled"));
            playToggle(player);
            open(player);
            return;
        }
        if (slot == PVP_SLOT) {
            boolean enabled = userSettingsService.togglePvp();
            messageManager.send(player, "settings.pvp", Map.of("status", enabled ? "enabled" : "disabled"));
            playToggle(player);
            open(player);
        }
    }

    private void clearScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getMainScoreboard());
        }
    }

    private void playToggle(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.4F);
    }

    private String status(boolean enabled) {
        return enabled ? "&aEnabled" : "&cDisabled";
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(color(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(color(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private Component color(String text) {
        return ColorUtil.colorize(text == null ? "" : text);
    }

    private record SettingsHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
