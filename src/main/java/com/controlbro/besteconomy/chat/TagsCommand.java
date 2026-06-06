package com.controlbro.besteconomy.chat;

import com.controlbro.besteconomy.util.ColorUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class TagsCommand implements CommandExecutor, Listener {
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
        int size = chatService.guiSize();
        TagsHolder holder = new TagsHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.colorize(chatService.guiTitle()));
        holder.inventory = inventory;

        int tagSlots = size - 9;
        for (int i = 0; i < Math.min(tags.size(), tagSlots); i++) {
            ChatService.TagOption tag = tags.get(i);
            boolean equipped = tag.key().equals(chatService.selectedTag(player));
            ItemStack item = item(Material.NAME_TAG, equipped ? "&a&l" + tag.key() : "&f&l" + tag.key(), List.of(
                tag.display(),
                "",
                equipped ? "&aCurrently equipped" : "&eClick to equip"
            ));
            inventory.setItem(i, item);
            holder.actions.put(i, "tag:" + tag.key());
        }

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = tagSlots; slot < size; slot++) inventory.setItem(slot, filler);

        int removeSlot = size - 6;
        inventory.setItem(removeSlot, item(Material.RED_DYE, "&c&lRemove Equipped Tag", List.of("&7Show no tag beside your name.")));
        holder.actions.put(removeSlot, "remove");

        int closeSlot = size - 4;
        inventory.setItem(closeSlot, item(Material.BARRIER, "&c&lClose", List.of("&7Close the tags menu.")));
        holder.actions.put(closeSlot, "close");
        player.openInventory(inventory);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.colorize(name));
        meta.lore(lore.stream().map(ColorUtil::colorize).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof TagsHolder holder)) return;
        event.setCancelled(true);
        String action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        if (action.equals("close")) {
            player.closeInventory();
            return;
        }
        if (action.equals("remove")) {
            chatService.clearTag(player);
            player.closeInventory();
            player.sendMessage(ColorUtil.colorize("&aYour equipped tag was removed."));
            return;
        }
        String tag = action.substring("tag:".length());
        chatService.setTag(player, tag);
        player.closeInventory();
        player.sendMessage(ColorUtil.colorize("&aSelected tag: &f" + tag));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof TagsHolder) event.setCancelled(true);
    }

    private static final class TagsHolder implements InventoryHolder {
        private final Map<Integer, String> actions = new HashMap<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
