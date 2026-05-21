package com.controlbro.besteconomy.chat;

import com.controlbro.besteconomy.util.ColorUtil;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TagsCommand implements CommandExecutor, Listener {
    private static final String TITLE = "&8Tags";
    private final ChatService chatService;

    public TagsCommand(ChatService chatService) { this.chatService = chatService; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        open(player);
        return true;
    }

    private void open(Player player) {
        List<ChatService.TagOption> tags = chatService.availableTags(player);
        int size = Math.max(9, ((tags.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(new TagsHolder(), size, ColorUtil.colorize(TITLE));
        for (int i = 0; i < tags.size(); i++) {
            ChatService.TagOption t = tags.get(i);
            ItemStack item = new ItemStack(Material.NAME_TAG);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(ColorUtil.colorize("&f" + t.key() + " &7- " + t.display()));
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof TagsHolder)) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta() || item.getItemMeta().displayName() == null) return;
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        String key = plain.split(" - ")[0].trim();
        chatService.setTag(player, key);
        player.closeInventory();
        player.sendMessage(ColorUtil.colorize("&aSelected tag: &f" + key));
    }

    private record TagsHolder() implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
}
