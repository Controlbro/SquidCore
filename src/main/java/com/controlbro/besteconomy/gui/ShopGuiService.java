package com.controlbro.besteconomy.gui;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import com.controlbro.besteconomy.util.NumberUtil;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.plugin.java.JavaPlugin;

public class ShopGuiService implements Listener {
    private static final int[] REMOVE_SLOTS = {11, 20, 29};
    private static final int[] ADD_SLOTS = {15, 24, 33};
    private static final int BUY_SLOT = 22;
    private static final int CANCEL_SLOT = 49;
    private static final int DEFAULT_SELL_CANCEL_SLOT = 49;
    private static final int DEFAULT_SELL_TOTAL_SLOT = 53;
    private static final String DEFAULT_FILL_ITEM = "BLACK_STAINED_GLASS_PANE";
    private static final String DEFAULT_SHARD_SHOP_URL = "https://shop.controlbro.com";
    private static final String DEFAULT_SHARD_SHOP_MESSAGE = "&5Shard Shop: &d{url}";
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final File configFolder;
    private final Map<String, SectionConfig> sections = new HashMap<>();
    private final Set<UUID> suppressHomeOnClose = new HashSet<>();
    private YamlConfiguration homeConfig;
    private YamlConfiguration sellConfig;

    public ShopGuiService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
        this.configFolder = new File(plugin.getDataFolder(), "shopconfig");
        reload();
    }

    public void reload() {
        saveDefaultShopConfig("shopconfig/homepage.yml");
        saveDefaultShopConfig("shopconfig/sell.yml");
        saveDefaultShopConfig("shopconfig/sections/blocks.yml");
        saveDefaultShopConfig("shopconfig/sections/farming.yml");
        saveDefaultShopConfig("shopconfig/sections/food.yml");
        saveDefaultShopConfig("shopconfig/sections/ore.yml");
        homeConfig = YamlConfiguration.loadConfiguration(new File(configFolder, "homepage.yml"));
        ensureShardShopMessageDefaults();
        sellConfig = YamlConfiguration.loadConfiguration(new File(configFolder, "sell.yml"));
        sections.clear();
    }

    private void ensureShardShopMessageDefaults() {
        boolean changed = false;
        if (!homeConfig.isSet("shard-shop-button.url")) {
            homeConfig.set("shard-shop-button.url", DEFAULT_SHARD_SHOP_URL);
            changed = true;
        }
        if (!homeConfig.isSet("shard-shop-button.message")) {
            homeConfig.set("shard-shop-button.message", DEFAULT_SHARD_SHOP_MESSAGE);
            changed = true;
        }
        if (!changed) {
            return;
        }
        try {
            homeConfig.save(new File(configFolder, "homepage.yml"));
        } catch (IOException ignored) {
            // ignored
        }
    }

    public void openHome(Player player) {
        if (!player.hasPermission("shop.gui.use")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new HomeHolder(), validSize(homeConfig.getInt("size", 27)), color(homeConfig.getString("title", "&8Money Shop")));
        fillConfigured(inventory, homeConfig, true);
        ConfigurationSection sectionRoot = homeConfig.getConfigurationSection("sections");
        if (sectionRoot != null) {
            for (String key : sectionRoot.getKeys(false)) {
                ConfigurationSection section = sectionRoot.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }
                int slot = section.getInt("slot", -1);
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, item(section.getString("material", "CHEST"), section.getString("name", key), section.getStringList("lore")));
                    sections.put(key, loadSection(key, section.getString("file", "sections/" + key + ".yml")));
                }
            }
        }
        ConfigurationSection shardShop = homeConfig.getConfigurationSection("shard-shop-button");
        if (shardShop != null && shardShop.getBoolean("enabled", false)) {
            int slot = shardShop.getInt("slot", -1);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item(shardShop.getString("material", "AMETHYST_SHARD"), shardShop.getString("name", "&5Shard Shop"), shardShop.getStringList("lore")));
            }
        }
        player.openInventory(inventory);
    }

    public void openSell(Player player) {
        if (!player.hasPermission("shop.sell.use")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        Inventory inventory = Bukkit.createInventory(new SellHolder(), validSize(sellConfig.getInt("size", 54)), color(sellConfig.getString("title", "&8Sell Items")));
        updateSellControls(inventory);
        player.openInventory(inventory);
    }

    public void openValues(Player player, int page) {
        if (!player.hasPermission("shop.values.use")) {
            messageManager.send(player, "no-permission", null);
            return;
        }
        List<ValueItem> valueItems = loadValueItems();
        int maxPage = Math.max(1, (int) Math.ceil(valueItems.size() / 45.0));
        int safePage = Math.max(1, Math.min(maxPage, page));
        Inventory inventory = Bukkit.createInventory(new ValuesHolder(safePage), 54, color(sellConfig.getString("values-title", "&8Sell Values &7(Page {page}/{maxpage})")
            .replace("{page}", String.valueOf(safePage)).replace("{maxpage}", String.valueOf(maxPage))));
        int start = (safePage - 1) * 45;
        int end = Math.min(start + 45, valueItems.size());
        for (int i = start; i < end; i++) {
            ValueItem valueItem = valueItems.get(i);
            inventory.setItem(i - start, item(valueItem.material().name(), "&a" + prettyMaterialName(valueItem.material()), List.of("&7Sell Value: &a$" + NumberUtil.format(valueItem.value()))));
        }
        fillBottomBar(inventory);
        if (safePage > 1) {
            inventory.setItem(45, item("ARROW", "&ePrevious Page", List.of("&7Go to page " + (safePage - 1))));
        }
        inventory.setItem(49, item("BARRIER", "&cClose", List.of("&7Close this menu")));
        if (safePage < maxPage) {
            inventory.setItem(53, item("ARROW", "&eNext Page", List.of("&7Go to page " + (safePage + 1))));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getInventory().getHolder() == null) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof HomeHolder) {
            handleHomeClick(event, player);
        } else if (holder instanceof SectionHolder sectionHolder) {
            handleSectionClick(event, player, sectionHolder.sectionConfig());
        } else if (holder instanceof BuyHolder buyHolder) {
            handleBuyClick(event, player, buyHolder);
        } else if (holder instanceof SellHolder sellHolder) {
            handleSellClick(event, sellHolder);
        } else if (holder instanceof ValuesHolder valuesHolder) {
            handleValuesClick(event, player, valuesHolder.page());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (suppressHomeOnClose.remove(player.getUniqueId())) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof SellHolder sellHolder) {
            if (!sellHolder.handled()) {
                confirmSell(player, event.getInventory(), sellHolder);
            }
            return;
        }
        if (holder instanceof SectionHolder || holder instanceof BuyHolder) {
            Bukkit.getScheduler().runTask(plugin, () -> openHome(player));
        }
    }

    private void handleHomeClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        ConfigurationSection shardShop = homeConfig.getConfigurationSection("shard-shop-button");
        if (shardShop != null && shardShop.getBoolean("enabled", false) && event.getRawSlot() == shardShop.getInt("slot", -1)) {
            player.closeInventory();
            sendShardShopMessage(player, shardShop);
            return;
        }
        ConfigurationSection sectionRoot = homeConfig.getConfigurationSection("sections");
        if (sectionRoot == null) {
            return;
        }
        for (String key : sectionRoot.getKeys(false)) {
            ConfigurationSection section = sectionRoot.getConfigurationSection(key);
            if (section != null && event.getRawSlot() == section.getInt("slot", -1)) {
                SectionConfig sectionConfig = sections.computeIfAbsent(key, ignored -> loadSection(key, section.getString("file", "sections/" + key + ".yml")));
                suppressHomeOnClose.add(player.getUniqueId());
                openSection(player, sectionConfig);
                return;
            }
        }
    }

    private void sendShardShopMessage(Player player, ConfigurationSection shardShop) {
        String url = shardShop.getString("url", DEFAULT_SHARD_SHOP_URL);
        String message = shardShop.getString("message", DEFAULT_SHARD_SHOP_MESSAGE);
        String formatted = messageManager.applyPlaceholders(message, player, Map.of("url", url));
        player.sendMessage(ColorUtil.colorize(formatted));
    }

    private void handleSectionClick(InventoryClickEvent event, Player player, SectionConfig sectionConfig) {
        event.setCancelled(true);
        ShopItem clicked = sectionConfig.itemBySlot(event.getRawSlot());
        if (clicked != null) {
            suppressHomeOnClose.add(player.getUniqueId());
            openBuyMenu(player, clicked, 1);
        }
    }

    private void handleBuyClick(InventoryClickEvent event, Player player, BuyHolder holder) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        int amount = holder.amount();
        if (slot == REMOVE_SLOTS[0]) amount -= 1;
        if (slot == REMOVE_SLOTS[1]) amount -= 8;
        if (slot == REMOVE_SLOTS[2]) amount -= 16;
        if (slot == ADD_SLOTS[0]) amount += 1;
        if (slot == ADD_SLOTS[1]) amount += 8;
        if (slot == ADD_SLOTS[2]) amount += 16;
        amount = Math.max(1, Math.min(64, amount));
        if (slot == BUY_SLOT) {
            buy(player, holder.item(), amount);
            amount = holder.amount();
        } else if (slot == CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        suppressHomeOnClose.add(player.getUniqueId());
        openBuyMenu(player, holder.item(), amount);
    }

    private void handleSellClick(InventoryClickEvent event, SellHolder sellHolder) {
        int cancelSlot = sellCancelSlot();
        int totalSlot = sellTotalSlot();
        if (event.getRawSlot() == cancelSlot) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            sellHolder.handled(true);
            returnSellItems(player, event.getInventory());
            clearSellInventory(event.getInventory());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == totalSlot) {
            event.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateSellControls(event.getInventory()));
    }

    private void handleValuesClick(InventoryClickEvent event, Player player, int page) {
        event.setCancelled(true);
        if (event.getRawSlot() == 45 && page > 1) {
            openValues(player, page - 1);
        } else if (event.getRawSlot() == 49) {
            player.closeInventory();
        } else if (event.getRawSlot() == 53) {
            int maxPage = Math.max(1, (int) Math.ceil(loadValueItems().size() / 45.0));
            if (page < maxPage) {
                openValues(player, page + 1);
            }
        }
    }

    private void openSection(Player player, SectionConfig sectionConfig) {
        Inventory inventory = Bukkit.createInventory(new SectionHolder(sectionConfig), sectionConfig.size(), color(sectionConfig.title()));
        fillConfigured(inventory, sectionConfig.fillItem(), true);
        for (ShopItem shopItem : sectionConfig.items()) {
            List<String> lore = new ArrayList<>(shopItem.lore());
            lore.replaceAll(line -> line.replace("{price}", NumberUtil.format(shopItem.price())));
            if (shopItem.slot() >= 0 && shopItem.slot() < inventory.getSize()) {
                inventory.setItem(shopItem.slot(), item(shopItem.material().name(), shopItem.name(), lore));
            }
        }
        player.openInventory(inventory);
    }

    private void openBuyMenu(Player player, ShopItem shopItem, int amount) {
        Inventory inventory = Bukkit.createInventory(new BuyHolder(shopItem, amount), 54, color("&8Buy " + shopItem.name()));
        inventory.setItem(REMOVE_SLOTS[0], item("RED_STAINED_GLASS_PANE", "&c-1", List.of("&7Decrease by 1")));
        inventory.setItem(REMOVE_SLOTS[1], item("RED_STAINED_GLASS_PANE", "&c-8", List.of("&7Decrease by 8")));
        inventory.setItem(REMOVE_SLOTS[2], item("RED_STAINED_GLASS_PANE", "&c-16", List.of("&7Decrease by 16")));
        inventory.setItem(ADD_SLOTS[0], item("GREEN_STAINED_GLASS_PANE", "&a+1", List.of("&7Increase by 1")));
        inventory.setItem(ADD_SLOTS[1], item("GREEN_STAINED_GLASS_PANE", "&a+8", List.of("&7Increase by 8")));
        inventory.setItem(ADD_SLOTS[2], item("GREEN_STAINED_GLASS_PANE", "&a+16", List.of("&7Increase by 16")));
        BigDecimal total = shopItem.price().multiply(BigDecimal.valueOf(amount));
        inventory.setItem(13, item(shopItem.material().name(), shopItem.name(), List.of("&7Total: &a$" + NumberUtil.format(total)), amount));
        inventory.setItem(BUY_SLOT, item("LIME_STAINED_GLASS_PANE", "&aBuy", List.of("&7Click to buy", "&7Total: &a$" + NumberUtil.format(total))));
        inventory.setItem(CANCEL_SLOT, item("BARRIER", "&cCancel", List.of("&7Return to the main shop")));
        player.openInventory(inventory);
    }

    private void buy(Player player, ShopItem shopItem, int amount) {
        Currency money = getMoneyCurrency();
        BigDecimal total = shopItem.price().multiply(BigDecimal.valueOf(amount));
        if (economyManager.getAvailableToSpend(player.getUniqueId(), money).compareTo(total) < 0) {
            messageManager.send(player, "insufficient-money", null);
            return;
        }
        economyManager.subtractBalance(player.getUniqueId(), money, total);
        player.getInventory().addItem(new ItemStack(shopItem.material(), amount)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        playDing(player);
        messageManager.send(player, "shop.purchase-success", Map.of("amount", String.valueOf(amount), "item", shopItem.material().name(), "price", NumberUtil.format(total)));
    }

    private void playDing(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
    }

    private SectionConfig loadSection(String key, String filePath) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(configFolder, filePath));
        int size = validShopFrameSize(config.getInt("size", 27));
        String title = config.getString("title", "&8" + key);
        String fillItem = config.getString("fill-item", config.getString("filler", DEFAULT_FILL_ITEM));
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection itemRoot = config.getConfigurationSection("items");
        if (itemRoot != null) {
            for (String itemKey : itemRoot.getKeys(false)) {
                ConfigurationSection itemSection = itemRoot.getConfigurationSection(itemKey);
                if (itemSection == null) continue;
                Material material = material(itemSection.getString("material", "STONE"));
                BigDecimal price = new BigDecimal(itemSection.getString("price", "0"));
                items.add(new ShopItem(itemSection.getInt("slot", 0), material, itemSection.getString("name", material.name()), itemSection.getStringList("lore"), price));
            }
        }
        return new SectionConfig(key, title, size, fillItem, items);
    }

    private void updateSellControls(Inventory inventory) {
        int cancelSlot = sellCancelSlot();
        if (cancelSlot >= 0 && cancelSlot < inventory.getSize()) {
            inventory.setItem(cancelSlot, item("RED_STAINED_GLASS_PANE", "&cCancel", List.of("&7Return your items without selling.", "&cClick to cancel.")));
        }
        int totalSlot = sellTotalSlot();
        if (totalSlot >= 0 && totalSlot < inventory.getSize()) {
            BigDecimal total = calculateSellTotal(inventory);
            inventory.setItem(totalSlot, item("LIME_STAINED_GLASS_PANE", "&aYou Get: $" + NumberUtil.format(total), List.of("&7Press &eESC &7to confirm selling.", "&7Unsellable items will be returned.")));
        }
    }

    private void confirmSell(Player player, Inventory inventory, SellHolder sellHolder) {
        BigDecimal total = calculateSellTotal(inventory);
        sellHolder.handled(true);
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            Currency money = getMoneyCurrency();
            economyManager.addBalance(player.getUniqueId(), money, total);
            removeSellableItems(inventory);
            returnSellItems(player, inventory);
            clearSellInventory(inventory);
            playDing(player);
            messageManager.send(player, "shop.sell-complete", Map.of("amount", NumberUtil.format(total)));
            return;
        }
        returnSellItems(player, inventory);
        clearSellInventory(inventory);
    }

    private BigDecimal calculateSellTotal(Inventory inventory) {
        BigDecimal total = BigDecimal.ZERO;
        int cancelSlot = sellCancelSlot();
        int totalSlot = sellTotalSlot();
        ConfigurationSection values = sellConfig.getConfigurationSection("values");
        if (values == null) return total;
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == cancelSlot || i == totalSlot) continue;
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            total = total.add(sellValue(itemStack, values));
        }
        return total;
    }

    private void removeSellableItems(Inventory inventory) {
        int cancelSlot = sellCancelSlot();
        int totalSlot = sellTotalSlot();
        ConfigurationSection values = sellConfig.getConfigurationSection("values");
        if (values == null) {
            return;
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == cancelSlot || i == totalSlot) continue;
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack == null || itemStack.getType().isAir()) continue;
            BigDecimal value = new BigDecimal(values.getString(itemStack.getType().name(), "0"));
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                inventory.setItem(i, null);
                continue;
            }
            ItemStack cleaned = removeSellableShulkerContents(itemStack, values);
            inventory.setItem(i, cleaned);
        }
    }

    private BigDecimal sellValue(ItemStack itemStack, ConfigurationSection values) {
        BigDecimal total = new BigDecimal(values.getString(itemStack.getType().name(), "0")).multiply(BigDecimal.valueOf(itemStack.getAmount()));
        if (!(itemStack.getItemMeta() instanceof BlockStateMeta blockStateMeta) || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return total;
        }
        for (ItemStack contained : shulkerBox.getInventory().getContents()) {
            if (contained == null || contained.getType().isAir()) {
                continue;
            }
            total = total.add(sellValue(contained, values));
        }
        return total;
    }

    private ItemStack removeSellableShulkerContents(ItemStack itemStack, ConfigurationSection values) {
        if (!(itemStack.getItemMeta() instanceof BlockStateMeta blockStateMeta) || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return itemStack;
        }
        boolean changed = false;
        for (int slot = 0; slot < shulkerBox.getInventory().getSize(); slot++) {
            ItemStack contained = shulkerBox.getInventory().getItem(slot);
            if (contained == null || contained.getType().isAir()) {
                continue;
            }
            BigDecimal value = new BigDecimal(values.getString(contained.getType().name(), "0"));
            if (value.compareTo(BigDecimal.ZERO) > 0) {
                shulkerBox.getInventory().setItem(slot, null);
                changed = true;
                continue;
            }
            ItemStack cleaned = removeSellableShulkerContents(contained, values);
            if (cleaned != contained) {
                shulkerBox.getInventory().setItem(slot, cleaned);
                changed = true;
            }
        }
        if (!changed) {
            return itemStack;
        }
        ItemStack result = itemStack.clone();
        BlockStateMeta resultMeta = (BlockStateMeta) result.getItemMeta();
        resultMeta.setBlockState(shulkerBox);
        result.setItemMeta(resultMeta);
        return result;
    }

    private void clearSellInventory(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }
    }

    private void returnSellItems(Player player, Inventory inventory) {
        int cancelSlot = sellCancelSlot();
        int totalSlot = sellTotalSlot();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i == cancelSlot || i == totalSlot) continue;
            ItemStack itemStack = inventory.getItem(i);
            if (itemStack != null && !itemStack.getType().isAir()) {
                player.getInventory().addItem(itemStack).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                inventory.setItem(i, null);
            }
        }
    }

    private int sellCancelSlot() {
        return sellConfig.getInt("sell-cancel-slot", sellConfig.getInt("sell-button-slot", DEFAULT_SELL_CANCEL_SLOT));
    }

    private int sellTotalSlot() {
        return sellConfig.getInt("sell-total-slot", DEFAULT_SELL_TOTAL_SLOT);
    }

    private Currency getMoneyCurrency() {
        Currency money = currencyManager.getCurrency("money");
        return money == null ? currencyManager.getDefaultCurrency() : money;
    }

    private void saveDefaultShopConfig(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    private ItemStack item(String materialName, String name, List<String> lore) {
        return item(materialName, name, lore, 1);
    }

    private ItemStack item(String materialName, String name, List<String> lore, int amount) {
        ItemStack itemStack = new ItemStack(material(materialName), Math.max(1, Math.min(64, amount)));
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(color(name));
        meta.lore(lore.stream().map(this::color).toList());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private Material material(String materialName) {
        Material material = Material.matchMaterial(materialName == null ? "STONE" : materialName);
        return material == null ? Material.STONE : material;
    }

    private void fillConfigured(Inventory inventory, ConfigurationSection config, boolean edgesOnly) {
        if (!config.getBoolean("fill-empty", true)) {
            return;
        }
        fillConfigured(inventory, config.getString("fill-item", config.getString("filler", DEFAULT_FILL_ITEM)), edgesOnly);
    }

    private void fillConfigured(Inventory inventory, String materialName, boolean edgesOnly) {
        if (materialName == null || materialName.equalsIgnoreCase("none")) {
            return;
        }
        ItemStack filler = item(materialName, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            if ((!edgesOnly || isEdgeSlot(i, inventory.getSize())) && inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    private boolean isEdgeSlot(int slot, int size) {
        int rows = size / 9;
        int row = slot / 9;
        int column = slot % 9;
        return row == 0 || row == rows - 1 || column == 0 || column == 8;
    }

    private void fillBottomBar(Inventory inventory) {
        ItemStack filler = item(DEFAULT_FILL_ITEM, " ", List.of());
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }
    }

    private List<ValueItem> loadValueItems() {
        List<ValueItem> valueItems = new ArrayList<>();
        ConfigurationSection values = sellConfig.getConfigurationSection("values");
        if (values == null) {
            return valueItems;
        }
        for (String key : values.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material != null) {
                valueItems.add(new ValueItem(material, new BigDecimal(values.getString(key, "0"))));
            }
        }
        valueItems.sort(Comparator.comparing(valueItem -> valueItem.material().name()));
        return valueItems;
    }

    private String prettyMaterialName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private int validShopFrameSize(int size) {
        return Math.min(27, validSize(size));
    }

    private int validSize(int size) {
        if (size < 9) return 9;
        if (size > 54) return 54;
        return (size / 9) * 9;
    }

    private Component color(String text) {
        return ColorUtil.colorize(text == null ? "" : text);
    }

    private record HomeHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record SectionHolder(SectionConfig sectionConfig) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class BuyHolder implements InventoryHolder {
        private final ShopItem item;
        private final int amount;
        private BuyHolder(ShopItem item, int amount) { this.item = item; this.amount = amount; }
        @Override public Inventory getInventory() { return null; }
        private ShopItem item() { return item; }
        private int amount() { return amount; }
    }

    private static final class SellHolder implements InventoryHolder {
        private boolean handled;
        @Override public Inventory getInventory() { return null; }
        private boolean handled() { return handled; }
        private void handled(boolean handled) { this.handled = handled; }
    }

    private record ValuesHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record SectionConfig(String key, String title, int size, String fillItem, List<ShopItem> items) {
        private ShopItem itemBySlot(int slot) {
            return items.stream().filter(item -> item.slot() == slot).findFirst().orElse(null);
        }
    }

    private record ShopItem(int slot, Material material, String name, List<String> lore, BigDecimal price) {
    }

    private record ValueItem(Material material, BigDecimal value) {
    }
}
