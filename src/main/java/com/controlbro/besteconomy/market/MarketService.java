package com.controlbro.besteconomy.market;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import com.controlbro.besteconomy.util.NumberUtil;
import com.controlbro.besteconomy.util.DiscordWebhookNotifier;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.plugin.java.JavaPlugin;

public class MarketService implements Listener {
    private static final int MARKET_SIZE = 54;
    private static final int CONTENT_SLOTS = 45;
    private static final int DEFAULT_STALL_SLOTS = 10;
    private static final int MAX_STOCK_SLOTS = 27;
    private static final int PREVIOUS_SLOT = 45;
    private static final int VIEW_ALL_SLOT = 46;
    private static final int REFRESH_SLOT = 47;
    private static final int SEARCH_SLOT = 49;
    private static final int MANAGE_SLOT = 51;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 45;
    private static final int RENAME_STALL_SLOT = 47;
    private static final int CREATE_LISTING_SLOT = 49;
    private static final int ITEM_PREVIEW_SLOT = 13;
    private static final int STOCK_SLOT = 22;
    private static final int PRICE_SLOT = 29;
    private static final int STACK_SIZE_SLOT = 31;
    private static final int REMOVE_SLOT = 33;
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final DiscordWebhookNotifier webhookNotifier;
    private final File file;
    private final Map<UUID, MarketStall> stalls = new LinkedHashMap<>();
    private final Map<UUID, ChatInput> chatInputs = new HashMap<>();
    private final Set<UUID> suppressedMarketCloses = new HashSet<>();

    public MarketService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager, MessageManager messageManager, DiscordWebhookNotifier webhookNotifier) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
        this.webhookNotifier = webhookNotifier;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        load();
    }

    public void openMarket(Player player, int page, String search) {
        List<MarketStall> visibleStalls = filteredStalls(search);
        int maxPage = maxPage(visibleStalls.size());
        int safePage = clampPage(page, maxPage);
        Inventory inventory = Bukkit.createInventory(new MarketHolder(safePage, search), MARKET_SIZE, color(search == null ? "&8Market" : "&8Market Search: " + search));
        fillBottom(inventory);
        int start = safePage * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < visibleStalls.size(); i++) {
            MarketStall stall = visibleStalls.get(start + i);
            inventory.setItem(i, stallIcon(stall, search));
        }
        inventory.setItem(PREVIOUS_SLOT, navItem("ARROW", "&ePrevious Page", "&7Go to page " + safePage));
        inventory.setItem(VIEW_ALL_SLOT, navItem("BOOK", "&bView All Listings", "&7Browse every listing instead of stalls."));
        inventory.setItem(REFRESH_SLOT, navItem("CLOCK", "&aRefresh Market", "&7Click to reload the latest market stalls."));
        inventory.setItem(SEARCH_SLOT, navItem("COMPASS", "&bSearch Items", "&7Click and type an item name in chat."));
        inventory.setItem(MANAGE_SLOT, navItem("CHEST", "&aManage Your Stall", "&7Create or edit your own stall."));
        inventory.setItem(NEXT_SLOT, navItem("ARROW", "&eNext Page", "&7Go to page " + (safePage + 2)));
        openMarketInventory(player, inventory);
    }

    public void createStall(Player player, String name) {
        MarketStall stall = stall(player.getUniqueId(), player.getName());
        stall.name = trimName(name);
        save();
        playDing(player);
        messageManager.send(player, "market.stall-created", Map.of("name", stall.name));
        openManage(player);
    }

    public void renameStall(Player player, String name) {
        MarketStall stall = stall(player.getUniqueId(), player.getName());
        stall.name = trimName(name);
        save();
        playDing(player);
        messageManager.send(player, "market.stall-renamed", Map.of("name", stall.name));
        openManage(player);
    }

    public void openManage(Player player) {
        openManage(player, 0);
    }

    private void openManage(Player player, int page) {
        MarketStall stall = stall(player.getUniqueId(), player.getName());
        int maxPage = maxPage(stall.slots);
        int safePage = clampPage(page, maxPage);
        Inventory inventory = Bukkit.createInventory(new ManageHolder(stall.owner, safePage), MARKET_SIZE, color("&8Manage Stall"));
        fillBottom(inventory);
        List<MarketListing> listings = sortedListings(stall);
        int start = safePage * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < stall.slots; i++) {
            if (start + i < listings.size()) {
                inventory.setItem(i, listingIcon(listings.get(start + i), true));
            } else {
                inventory.setItem(i, navItem("GRAY_STAINED_GLASS_PANE", "&7Empty Listing Slot", "&7Click with an item on your cursor to list it."));
            }
        }
        inventory.setItem(BACK_SLOT, navItem("ARROW", safePage > 0 ? "&ePrevious Page" : "&eBack to Market", safePage > 0 ? "&7Go to the previous stall slots." : "&7Return to all stalls."));
        inventory.setItem(RENAME_STALL_SLOT, navItem("NAME_TAG", "&bRename Stall", "&7Current: &f" + stall.name, "&7Click and type a new market name."));
        inventory.setItem(CREATE_LISTING_SLOT, navItem("EMERALD", "&aAdd Held Item", "&7Hold an item and click to create a listing.", "&7You have &e" + stall.slots + " &7listing slots."));
        inventory.setItem(NEXT_SLOT, navItem("ARROW", "&eNext Page", "&7Go to the next stall slots."));
        openMarketInventory(player, inventory);
    }

    public void giveSlots(OfflinePlayer target, int amount) {
        MarketStall stall = stall(target.getUniqueId(), target.getName() == null ? "Unknown" : target.getName());
        stall.slots += amount;
        save();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("stalls");
        for (MarketStall stall : stalls.values()) {
            ConfigurationSection section = root.createSection(stall.owner.toString());
            section.set("name", stall.name);
            section.set("owner-name", stall.ownerName);
            section.set("slots", stall.slots);
            section.set("offline-earnings", stall.offlineEarnings.toPlainString());
            ConfigurationSection listingsSection = section.createSection("listings");
            for (MarketListing listing : stall.listings.values()) {
                ConfigurationSection listingSection = listingsSection.createSection(listing.id.toString());
                listingSection.set("display-item", listing.displayItem);
                listingSection.set("price", listing.price.toPlainString());
                listingSection.set("stack-size", listing.stackSize);
                listingSection.set("announced", listing.announced);
                ConfigurationSection stockSection = listingSection.createSection("stock");
                for (int i = 0; i < listing.stock.length; i++) {
                    if (isSellable(listing.stock[i])) {
                        stockSection.set(String.valueOf(i), listing.stock[i]);
                    }
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException ignored) {
            // ignored
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MarketStall stall = stalls.get(event.getPlayer().getUniqueId());
        if (stall == null || stall.offlineEarnings.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        messageManager.send(event.getPlayer(), "market.offline-earnings", Map.of("amount", NumberUtil.format(stall.offlineEarnings)));
        stall.offlineEarnings = BigDecimal.ZERO;
        save();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        ChatInput input = chatInputs.remove(event.getPlayer().getUniqueId());
        if (input == null) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage();
        Bukkit.getScheduler().runTask(plugin, () -> handleChatInput(event.getPlayer(), input, message));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StockHolder stockHolder) {
            handleStockClick(event, player, stockHolder);
            return;
        }
        if (holder instanceof MarketHolder marketHolder) {
            handleMarketClick(event, player, marketHolder);
        } else if (holder instanceof StallHolder stallHolder) {
            handleStallClick(event, player, stallHolder);
        } else if (holder instanceof ManageHolder manageHolder) {
            handleManageClick(event, player, manageHolder);
        } else if (holder instanceof ListingHolder listingHolder) {
            handleListingClick(event, player, listingHolder);
        } else if (holder instanceof AllListingsHolder allListingsHolder) {
            handleAllListingsClick(event, player, allListingsHolder);
        } else if (holder instanceof PurchaseHolder purchaseHolder) {
            handlePurchaseClick(event, player, purchaseHolder);
        } else if (holder instanceof ShulkerPreviewHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ShulkerPreviewHolder) {
            event.setCancelled(true);
            return;
        }
        if (!(holder instanceof StockHolder stockHolder)) {
            if (isMarketHolder(holder)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getRawSlots().stream().noneMatch(slot -> slot >= 0 && slot < MAX_STOCK_SLOTS)) {
            return;
        }
        MarketListing listing = listing(stockHolder.owner, stockHolder.listing);
        if (listing == null || event.getNewItems().values().stream().anyMatch(item -> !isMatchingStockItem(item, listing))) {
            event.setCancelled(true);
            messageManager.send(player, "market.stock-item-mismatch", null);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (holder instanceof StockHolder stockHolder) {
            MarketListing listing = listing(stockHolder.owner, stockHolder.listing);
            if (listing == null) {
                returnItems(player, event.getInventory().getContents());
                reopenPreviousMarketPage(player, holder);
                return;
            }
            ItemStack[] contents = event.getInventory().getContents();
            for (int i = 0; i < Math.min(MAX_STOCK_SLOTS, contents.length); i++) {
                ItemStack item = contents[i];
                if (isMatchingStockItem(item, listing)) {
                    listing.stock[i] = item.clone();
                } else {
                    listing.stock[i] = null;
                    returnItem(player, item);
                }
            }
            save();
            playDing(player);
            reopenPreviousMarketPage(player, holder);
            return;
        }
        if (isMarketHolder(holder)) {
            reopenPreviousMarketPage(player, holder);
        }
    }

    private void handleStockClick(InventoryClickEvent event, Player player, StockHolder holder) {
        MarketListing listing = listing(holder.owner, holder.listing());
        if (listing == null) {
            event.setCancelled(true);
            messageManager.send(player, "market.stock-item-mismatch", null);
            return;
        }
        ItemStack attempted = null;
        if (event.getClick() == ClickType.NUMBER_KEY && event.getRawSlot() >= 0 && event.getRawSlot() < MAX_STOCK_SLOTS) {
            attempted = event.getView().getBottomInventory().getItem(event.getHotbarButton());
        } else if (event.getClick() == ClickType.SWAP_OFFHAND && event.getRawSlot() >= 0 && event.getRawSlot() < MAX_STOCK_SLOTS) {
            attempted = player.getInventory().getItemInOffHand();
        } else if (event.isShiftClick() && event.getRawSlot() >= MAX_STOCK_SLOTS) {
            attempted = event.getCurrentItem();
        } else if (event.getRawSlot() >= 0 && event.getRawSlot() < MAX_STOCK_SLOTS) {
            attempted = event.getCursor();
        }
        if (isSellable(attempted) && !isMatchingStockItem(attempted, listing)) {
            event.setCancelled(true);
            messageManager.send(player, "market.stock-item-mismatch", null);
        }
    }

    private void handleMarketClick(InventoryClickEvent event, Player player, MarketHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() == PREVIOUS_SLOT) {
            openMarket(player, holder.page - 1, holder.search);
            return;
        }
        if (event.getRawSlot() == NEXT_SLOT) {
            openMarket(player, holder.page + 1, holder.search);
            return;
        }
        if (event.getRawSlot() == VIEW_ALL_SLOT) {
            openAllListings(player, 0, holder.search);
            return;
        }
        if (event.getRawSlot() == REFRESH_SLOT) {
            openMarket(player, holder.page, holder.search);
            playDing(player);
            return;
        }
        if (event.getRawSlot() == SEARCH_SLOT) {
            chatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.SEARCH, null));
            suppressNextMarketClose(player);
            player.closeInventory();
            messageManager.send(player, "market.search-prompt", null);
            return;
        }
        if (event.getRawSlot() == MANAGE_SLOT) {
            openManage(player);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= CONTENT_SLOTS) {
            return;
        }
        List<MarketStall> visibleStalls = filteredStalls(holder.search);
        int index = holder.page * CONTENT_SLOTS + event.getRawSlot();
        if (index < visibleStalls.size()) {
            openStall(player, visibleStalls.get(index), 0, holder.search);
        }
    }

    private void handleStallClick(InventoryClickEvent event, Player player, StallHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() == BACK_SLOT) {
            openMarket(player, 0, holder.search);
            return;
        }
        MarketStall stall = stalls.get(holder.owner());
        if (stall == null || event.getRawSlot() < 0 || event.getRawSlot() >= CONTENT_SLOTS) {
            return;
        }
        List<MarketListing> listings = searchableListings(stall, holder.search);
        int index = holder.page * CONTENT_SLOTS + event.getRawSlot();
        if (index < listings.size()) {
            MarketListing listing = listings.get(index);
            if (event.getClick().isRightClick() && openShulkerPreview(player, listing.displayItem)) {
                return;
            }
            openPurchaseConfirmation(player, stall, listing, listing.stackSize, new StallReturn(holder.owner, holder.page, holder.search));
        }
    }


    private void handleAllListingsClick(InventoryClickEvent event, Player player, AllListingsHolder holder) {
        event.setCancelled(true);
        if (event.getRawSlot() == PREVIOUS_SLOT) {
            if (holder.page > 0) {
                openAllListings(player, holder.page - 1, holder.search);
            } else {
                openMarket(player, 0, holder.search);
            }
            return;
        }
        if (event.getRawSlot() == NEXT_SLOT) {
            openAllListings(player, holder.page + 1, holder.search);
            return;
        }
        if (event.getRawSlot() == REFRESH_SLOT) {
            openAllListings(player, holder.page, holder.search);
            playDing(player);
            return;
        }
        if (event.getRawSlot() == SEARCH_SLOT) {
            chatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.SEARCH, null));
            suppressNextMarketClose(player);
            player.closeInventory();
            messageManager.send(player, "market.search-prompt", null);
            return;
        }
        if (event.getRawSlot() == MANAGE_SLOT) {
            openManage(player);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= CONTENT_SLOTS) {
            return;
        }
        List<ListingView> listings = allListings(holder.search);
        int index = holder.page * CONTENT_SLOTS + event.getRawSlot();
        if (index >= listings.size()) {
            return;
        }
        ListingView view = listings.get(index);
        if (event.getClick().isRightClick() && openShulkerPreview(player, view.listing.displayItem)) {
            return;
        }
        openPurchaseConfirmation(player, view.stall, view.listing, view.listing.stackSize, new AllListingsReturn(holder.page, holder.search));
    }

    private void handlePurchaseClick(InventoryClickEvent event, Player player, PurchaseHolder holder) {
        event.setCancelled(true);
        MarketStall stall = stalls.get(holder.owner());
        MarketListing listing = listing(holder.owner, holder.listing());
        if (stall == null || listing == null) {
            openMarket(player, 0, null);
            return;
        }
        int amount = holder.amount();
        int increment = Math.max(1, listing.stackSize);
        if (event.getRawSlot() == 20) {
            amount -= increment;
        } else if (event.getRawSlot() == 24) {
            amount += increment;
        } else if (event.getRawSlot() == 22) {
            buyListing(player, stall, listing, amount);
            returnAfterPurchase(player, holder.marketReturn);
            return;
        } else if (event.getRawSlot() == BACK_SLOT || event.getRawSlot() == 49) {
            returnAfterPurchase(player, holder.marketReturn);
            return;
        } else {
            return;
        }
        int maxAmount = Math.max(increment, (stockCount(listing) / increment) * increment);
        amount = Math.max(increment, Math.min(maxAmount, amount));
        openPurchaseConfirmation(player, stall, listing, amount, holder.marketReturn);
    }

    private void returnAfterPurchase(Player player, MarketReturn marketReturn) {
        if (marketReturn instanceof StallReturn stallReturn) {
            MarketStall stall = stalls.get(stallReturn.owner);
            if (stall != null) {
                openStall(player, stall, stallReturn.page, stallReturn.search);
            } else {
                openMarket(player, 0, stallReturn.search);
            }
            return;
        }
        if (marketReturn instanceof AllListingsReturn allReturn) {
            openAllListings(player, allReturn.page, allReturn.search);
            return;
        }
        openMarket(player, 0, null);
    }

    private void handleManageClick(InventoryClickEvent event, Player player, ManageHolder holder) {
        event.setCancelled(true);
        if (!player.getUniqueId().equals(holder.owner())) {
            return;
        }
        if (event.getRawSlot() == BACK_SLOT) {
            if (holder.page > 0) {
                openManage(player, holder.page - 1);
            } else {
                openMarket(player, 0, null);
            }
            return;
        }
        if (event.getRawSlot() == NEXT_SLOT) {
            openManage(player, holder.page + 1);
            return;
        }
        if (event.getRawSlot() == RENAME_STALL_SLOT) {
            chatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.NAME, null));
            suppressNextMarketClose(player);
            player.closeInventory();
            messageManager.send(player, "market.name-prompt", null);
            return;
        }
        if (event.getRawSlot() == CREATE_LISTING_SLOT) {
            createListingFromCursorOrHand(player, event.getCursor());
            return;
        }
        MarketStall stall = stall(player.getUniqueId(), player.getName());
        if (event.getRawSlot() >= 0 && event.getRawSlot() < CONTENT_SLOTS) {
            int listingIndex = holder.page * CONTENT_SLOTS + event.getRawSlot();
            if (listingIndex >= stall.slots) {
                return;
            }
            List<MarketListing> listings = sortedListings(stall);
            if (listingIndex < listings.size()) {
                openListingManagement(player, listings.get(listingIndex));
            } else {
                createListingFromCursorOrHand(player, event.getCursor());
            }
        }
    }

    private void handleListingClick(InventoryClickEvent event, Player player, ListingHolder holder) {
        event.setCancelled(true);
        if (!player.getUniqueId().equals(holder.owner())) {
            return;
        }
        MarketListing listing = listing(holder.owner, holder.listing());
        if (listing == null) {
            openManage(player);
            return;
        }
        if (event.getRawSlot() == STOCK_SLOT) {
            openStock(player, listing);
            return;
        }
        if (event.getRawSlot() == PRICE_SLOT) {
            chatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.PRICE, listing.id));
            suppressNextMarketClose(player);
            player.closeInventory();
            messageManager.send(player, "market.price-prompt", null);
            return;
        }
        if (event.getRawSlot() == STACK_SIZE_SLOT) {
            chatInputs.put(player.getUniqueId(), new ChatInput(ChatInputType.STACK_SIZE, listing.id));
            suppressNextMarketClose(player);
            player.closeInventory();
            messageManager.send(player, "market.stack-size-prompt", null);
            return;
        }
        if (event.getRawSlot() == REMOVE_SLOT) {
            removeListing(player, listing);
            return;
        }
        if (event.getRawSlot() == BACK_SLOT) {
            openManage(player);
        }
    }

    private void handleChatInput(Player player, ChatInput input, String message) {
        if (message.equalsIgnoreCase("cancel")) {
            messageManager.send(player, "market.input-cancelled", null);
            return;
        }
        if (input.type == ChatInputType.SEARCH) {
            openMarket(player, 0, message);
            return;
        }
        if (input.type == ChatInputType.NAME) {
            MarketStall stall = stall(player.getUniqueId(), player.getName());
            stall.name = trimName(message);
            save();
            playDing(player);
            messageManager.send(player, "market.stall-renamed", Map.of("name", stall.name));
            openManage(player);
            return;
        }
        MarketListing listing = listing(player.getUniqueId(), input.listingId);
        if (listing == null) {
            return;
        }
        if (input.type == ChatInputType.PRICE) {
            BigDecimal price = NumberUtil.parsePositiveAmount(message);
            if (price == null) {
                messageManager.send(player, "invalid-amount", null);
                openListingManagement(player, listing);
                return;
            }
            BigDecimal oldPrice = listing.price;
            listing.price = price;
            save();
            playDing(player);
            messageManager.send(player, "market.price-set", Map.of("price", NumberUtil.format(price)));
            notifyListingPrice(player, listing, oldPrice);
            openListingManagement(player, listing);
            return;
        }
        if (input.type == ChatInputType.STACK_SIZE) {
            try {
                int size = Integer.parseInt(message);
                if (size <= 0 || size > Math.max(1, listing.displayItem.getMaxStackSize())) {
                    messageManager.send(player, "invalid-amount", null);
                    openListingManagement(player, listing);
                    return;
                }
                int oldSize = listing.stackSize;
                listing.stackSize = size;
                save();
                playDing(player);
                messageManager.send(player, "market.stack-size-set", Map.of("size", String.valueOf(size)));
                notifyListingQuantity(player, listing, oldSize);
            } catch (NumberFormatException ex) {
                messageManager.send(player, "invalid-amount", null);
            }
            openListingManagement(player, listing);
        }
    }

    private void openStall(Player player, MarketStall stall, int page, String search) {
        List<MarketListing> listings = searchableListings(stall, search);
        int maxPage = maxPage(listings.size());
        int safePage = clampPage(page, maxPage);
        Inventory inventory = Bukkit.createInventory(new StallHolder(stall.owner, safePage, search), MARKET_SIZE, color("&8" + stall.name));
        fillBottom(inventory);
        int start = safePage * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < listings.size(); i++) {
            inventory.setItem(i, listingIcon(listings.get(start + i), false));
        }
        inventory.setItem(BACK_SLOT, navItem("ARROW", "&eBack to Market", "&7Return to all stalls."));
        openMarketInventory(player, inventory);
    }

    private void openAllListings(Player player, int page, String search) {
        List<ListingView> listings = allListings(search);
        int maxPage = maxPage(listings.size());
        int safePage = clampPage(page, maxPage);
        Inventory inventory = Bukkit.createInventory(new AllListingsHolder(safePage, search), MARKET_SIZE, color(search == null ? "&8All Market Listings" : "&8All Listings: " + search));
        fillBottom(inventory);
        int start = safePage * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < listings.size(); i++) {
            inventory.setItem(i, listingIcon(listings.get(start + i).listing, false));
        }
        inventory.setItem(PREVIOUS_SLOT, navItem("ARROW", safePage > 0 ? "&ePrevious Page" : "&eBack to Stalls", safePage > 0 ? "&7Go to page " + safePage : "&7Return to stall view."));
        inventory.setItem(REFRESH_SLOT, navItem("CLOCK", "&aRefresh Listings", "&7Click to reload listings."));
        inventory.setItem(SEARCH_SLOT, navItem("COMPASS", "&bSearch Items", "&7Click and type an item name in chat."));
        inventory.setItem(MANAGE_SLOT, navItem("CHEST", "&aManage Your Stall", "&7Create or edit your own stall."));
        inventory.setItem(NEXT_SLOT, navItem("ARROW", "&eNext Page", "&7Go to page " + (safePage + 2)));
        openMarketInventory(player, inventory);
    }

    private void openListingManagement(Player player, MarketListing listing) {
        Inventory inventory = Bukkit.createInventory(new ListingHolder(player.getUniqueId(), listing.id), MARKET_SIZE, color("&8Manage Listing"));
        fillAll(inventory);
        inventory.setItem(ITEM_PREVIEW_SLOT, listingIcon(listing, true));
        inventory.setItem(STOCK_SLOT, navItem("CHEST", "&aManage Stock", "&7Click to open the 27-slot stock chest.", "&7Current stock: &e" + stockCount(listing)));
        inventory.setItem(PRICE_SLOT, navItem("GOLD_INGOT", "&eSet Price Per Stack", "&7Current: &a$" + NumberUtil.format(listing.price), "&7Click and type a new price."));
        inventory.setItem(STACK_SIZE_SLOT, navItem("PAPER", "&eSet Stack Size", "&7Current: &a" + listing.stackSize, "&7Click and type a new stack size."));
        inventory.setItem(REMOVE_SLOT, navItem("BARRIER", "&cRemove Listing", "&7Returns all stock if you have space."));
        inventory.setItem(BACK_SLOT, navItem("ARROW", "&eBack", "&7Return to your stall manager."));
        openMarketInventory(player, inventory);
    }


    private void openPurchaseConfirmation(Player player, MarketStall stall, MarketListing listing, int amount, MarketReturn marketReturn) {
        int increment = Math.max(1, listing.stackSize);
        int maxAmount = Math.max(increment, (stockCount(listing) / increment) * increment);
        int safeAmount = Math.max(increment, Math.min(maxAmount, amount));
        Inventory inventory = Bukkit.createInventory(new PurchaseHolder(stall.owner, listing.id, safeAmount, marketReturn), MARKET_SIZE, color("&8Confirm Purchase"));
        fillAll(inventory);
        BigDecimal total = listing.price.multiply(BigDecimal.valueOf(safeAmount / increment));
        inventory.setItem(13, listingIcon(listing, false));
        inventory.setItem(20, navItem("RED_STAINED_GLASS_PANE", "&c-" + increment, "&7Decrease by the listing quantity."));
        inventory.setItem(24, navItem("GREEN_STAINED_GLASS_PANE", "&a+" + increment, "&7Increase by the listing quantity."));
        inventory.setItem(22, navItem("LIME_STAINED_GLASS_PANE", "&aConfirm Purchase", "&7Quantity: &e" + safeAmount, "&7Seller: &f" + stall.ownerName, "&7Total: &a$" + NumberUtil.format(total)));
        inventory.setItem(BACK_SLOT, navItem("ARROW", "&eBack", "&7Return without buying."));
        inventory.setItem(49, navItem("BARRIER", "&cCancel", "&7Return without buying."));
        openMarketInventory(player, inventory);
    }

    private boolean openShulkerPreview(Player player, ItemStack item) {
        if (!(item.getItemMeta() instanceof BlockStateMeta blockStateMeta) || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return false;
        }
        Inventory inventory = Bukkit.createInventory(new ShulkerPreviewHolder(), 27, color("&8Shulker Preview"));
        inventory.setContents(shulkerBox.getInventory().getContents());
        openMarketInventory(player, inventory);
        return true;
    }

    private void openStock(Player player, MarketListing listing) {
        Inventory inventory = Bukkit.createInventory(new StockHolder(player.getUniqueId(), listing.id), MAX_STOCK_SLOTS, color("&8Listing Stock"));
        inventory.setContents(copyContents(listing.stock));
        openMarketInventory(player, inventory);
    }

    private void createListingFromCursorOrHand(Player player, ItemStack cursor) {
        ItemStack source = isSellable(cursor) ? cursor : player.getInventory().getItemInMainHand();
        if (!isSellable(source)) {
            messageManager.send(player, "market.hold-item", null);
            return;
        }
        MarketStall stall = stall(player.getUniqueId(), player.getName());
        if (stall.listings.size() >= stall.slots) {
            messageManager.send(player, "market.no-listing-slots", null);
            return;
        }
        ItemStack display = source.clone();
        display.setAmount(1);
        MarketListing listing = new MarketListing(UUID.randomUUID(), display, BigDecimal.ONE, Math.min(source.getMaxStackSize(), Math.max(1, source.getAmount())));
        listing.announced = false;
        stall.listings.put(listing.id, listing);
        save();
        playDing(player);
        messageManager.send(player, "market.listing-created", null);
        openListingManagement(player, listing);
    }

    private void removeListing(Player player, MarketListing listing) {
        MarketStall stall = stall(player.getUniqueId(), player.getName());
        for (ItemStack item : listing.stock) {
            if (isSellable(item)) {
                player.getInventory().addItem(item.clone()).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }
        stall.listings.remove(listing.id);
        save();
        playDing(player);
        messageManager.send(player, "market.listing-removed", null);
        openManage(player);
    }

    private void buyListing(Player buyer, MarketStall stall, MarketListing listing) {
        buyListing(buyer, stall, listing, listing.stackSize);
    }

    private void buyListing(Player buyer, MarketStall stall, MarketListing listing, int amount) {
        if (buyer.getUniqueId().equals(stall.owner)) {
            messageManager.send(buyer, "market.cannot-buy-own", null);
            return;
        }
        int increment = Math.max(1, listing.stackSize);
        amount = Math.max(increment, amount);
        if (amount % increment != 0) {
            amount = (amount / increment) * increment;
        }
        if (stockCount(listing) < amount) {
            messageManager.send(buyer, "market.out-of-stock", null);
            return;
        }
        Currency money = currencyManager.getCurrency("money");
        if (money == null) {
            money = currencyManager.getDefaultCurrency();
        }
        BigDecimal totalPrice = listing.price.multiply(BigDecimal.valueOf(amount / increment));
        if (economyManager.getAvailableToSpend(buyer.getUniqueId(), money).compareTo(totalPrice) < 0) {
            messageManager.send(buyer, "insufficient-money", null);
            return;
        }
        List<ItemStack> purchased = takeStock(listing, amount);
        if (!canFit(buyer.getInventory().getStorageContents(), purchased)) {
            for (ItemStack item : purchased) {
                addStock(listing, item);
            }
            messageManager.send(buyer, "market.inventory-full", null);
            return;
        }
        for (ItemStack item : purchased) {
            buyer.getInventory().addItem(item);
        }
        economyManager.subtractBalance(buyer.getUniqueId(), money, totalPrice);
        economyManager.addBalance(stall.owner, money, totalPrice);
        notifySeller(stall, buyer, listing, amount, totalPrice);
        playDing(buyer);
        Player seller = Bukkit.getPlayer(stall.owner);
        if (seller != null) {
            playDing(seller);
        }
        save();
        messageManager.send(buyer, "market.purchase-success", Map.of(
            "item", displayName(listing.displayItem),
            "amount", String.valueOf(amount),
            "price", NumberUtil.format(totalPrice),
            "seller", stall.ownerName));
    }

    private void notifySeller(MarketStall stall, Player buyer, MarketListing listing, int amount, BigDecimal totalPrice) {
        Player seller = Bukkit.getPlayer(stall.owner);
        Map<String, String> placeholders = Map.of(
            "buyer", buyer.getName(),
            "item", displayName(listing.displayItem),
            "amount", String.valueOf(amount),
            "price", NumberUtil.format(totalPrice));
        if (seller != null) {
            messageManager.send(seller, "market.seller-sale", placeholders);
            return;
        }
        stall.offlineEarnings = stall.offlineEarnings.add(totalPrice);
    }

    private List<ItemStack> takeStock(MarketListing listing, int amount) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (int i = 0; i < listing.stock.length && remaining > 0; i++) {
            ItemStack stock = listing.stock[i];
            if (!isMatchingStockItem(stock, listing)) {
                continue;
            }
            int taken = Math.min(stock.getAmount(), remaining);
            ItemStack clone = stock.clone();
            clone.setAmount(taken);
            result.add(clone);
            stock.setAmount(stock.getAmount() - taken);
            if (stock.getAmount() <= 0) {
                listing.stock[i] = null;
            }
            remaining -= taken;
        }
        return result;
    }

    private boolean canFit(ItemStack[] storage, List<ItemStack> items) {
        Inventory inventory = Bukkit.createInventory(null, 36);
        inventory.setStorageContents(storage);
        for (ItemStack item : items) {
            if (!inventory.addItem(item.clone()).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean addStock(MarketListing listing, ItemStack item) {
        if (!isMatchingStockItem(item, listing)) {
            return false;
        }
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        Inventory inventory = Bukkit.createInventory(null, MAX_STOCK_SLOTS);
        inventory.setContents(copyContents(listing.stock));
        leftovers.putAll(inventory.addItem(item.clone()));
        listing.stock = copyContents(inventory.getContents());
        return leftovers.isEmpty();
    }


    private void notifyListingPrice(Player player, MarketListing listing, BigDecimal oldPrice) {
        if (!listing.announced) {
            listing.announced = true;
            save();
            webhookNotifier.sendEmbed("webhooks.market-listings", "Market Listing Created", player.getName() + " listed " + displayName(listing.displayItem) + " for $" + NumberUtil.format(listing.price) + " (x" + listing.stackSize + ").", 0x57F287);
            return;
        }
        webhookNotifier.sendEmbed("webhooks.market-listings", "Market Price Updated", player.getName() + " updated " + displayName(listing.displayItem) + " from $" + NumberUtil.format(oldPrice) + " to $" + NumberUtil.format(listing.price) + " (x" + listing.stackSize + ").", 0xFEE75C);
    }

    private void notifyListingQuantity(Player player, MarketListing listing, int oldSize) {
        if (!listing.announced) {
            return;
        }
        webhookNotifier.sendEmbed("webhooks.market-listings", "Market Quantity Updated", player.getName() + " updated " + displayName(listing.displayItem) + " from x" + oldSize + " to x" + listing.stackSize + " at $" + NumberUtil.format(listing.price) + ".", 0x3498DB);
    }

    private List<MarketStall> filteredStalls(String search) {
        String normalized = normalize(search);
        return stalls.values().stream()
            .filter(stall -> !stall.listings.isEmpty())
            .filter(stall -> normalized.isEmpty() || searchableListings(stall, search).size() > 0)
            .sorted(Comparator.comparing(stall -> stall.name.toLowerCase(Locale.US)))
            .toList();
    }


    private List<ListingView> allListings(String search) {
        String normalized = normalize(search);
        List<ListingView> views = new ArrayList<>();
        for (MarketStall stall : stalls.values()) {
            for (MarketListing listing : stall.listings.values()) {
                if (normalized.isEmpty() || matches(listing.displayItem, normalized)) {
                    views.add(new ListingView(stall, listing));
                }
            }
        }
        views.sort(Comparator
            .comparing((ListingView view) -> displayName(view.listing.displayItem).toLowerCase(Locale.US))
            .thenComparing(view -> view.stall.name.toLowerCase(Locale.US)));
        return views;
    }

    private List<MarketListing> searchableListings(MarketStall stall, String search) {
        String normalized = normalize(search);
        return sortedListings(stall).stream()
                        .filter(listing -> normalized.isEmpty() || matches(listing.displayItem, normalized))
            .toList();
    }

    private boolean matches(ItemStack item, String search) {
        return normalize(displayName(item)).contains(search) || item.getType().name().toLowerCase(Locale.US).contains(search);
    }

    private List<MarketListing> sortedListings(MarketStall stall) {
        return stall.listings.values().stream()
            .sorted(Comparator.comparing(listing -> displayName(listing.displayItem).toLowerCase(Locale.US)))
            .toList();
    }

    private MarketStall stall(UUID owner, String ownerName) {
        MarketStall stall = stalls.computeIfAbsent(owner, id -> new MarketStall(id, ownerName, ownerName + "'s Stall", DEFAULT_STALL_SLOTS));
        stall.ownerName = ownerName;
        return stall;
    }

    private MarketListing listing(UUID owner, UUID listing) {
        MarketStall stall = stalls.get(owner);
        if (stall == null) {
            return null;
        }
        return stall.listings.get(listing);
    }

    private ItemStack stallIcon(MarketStall stall, String search) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(stall.owner));
            meta = skullMeta;
        }
        meta.displayName(color("&a" + stall.name));
        List<Component> lore = new ArrayList<>();
        lore.add(color("&7Owner: &f" + stall.ownerName));
        lore.add(color("&7Items: &e" + searchableListings(stall, search).size()));
        lore.add(color("&7Click to browse."));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack listingIcon(MarketListing listing, boolean management) {
        ItemStack item = listing.displayItem.clone();
        item.setAmount(Math.min(Math.max(1, listing.stackSize), item.getMaxStackSize()));
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(color("&8&m----------------"));
        lore.add(color("&7Price per stack: &a$" + NumberUtil.format(listing.price)));
        lore.add(color("&7Stack size: &e" + listing.stackSize));
        int stock = stockCount(listing);
        lore.add(color("&7Stock: " + (stock >= listing.stackSize ? "&a" + stock : "&cOut of stock")));
        lore.add(color(management ? "&7Click to manage." : stock >= listing.stackSize ? "&7Click to buy." : "&cUnavailable until restocked."));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navItem(String material, String name, String... lore) {
        Material type = Material.matchMaterial(material);
        ItemStack item = new ItemStack(type == null ? Material.STONE : type);
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

    private void fillBottom(Inventory inventory) {
        ItemStack filler = navItem(FILLER.name(), " ");
        for (int i = CONTENT_SLOTS; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void fillAll(Inventory inventory) {
        ItemStack filler = navItem(FILLER.name(), " ");
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private void openMarketInventory(Player player, Inventory inventory) {
        if (isMarketHolder(player.getOpenInventory().getTopInventory().getHolder())) {
            suppressNextMarketClose(player);
        }
        player.openInventory(inventory);
    }

    private void suppressNextMarketClose(Player player) {
        suppressedMarketCloses.add(player.getUniqueId());
    }

    private void reopenPreviousMarketPage(Player player, InventoryHolder holder) {
        if (suppressedMarketCloses.remove(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> openPreviousMarketPage(player, holder));
    }

    private void openPreviousMarketPage(Player player, InventoryHolder holder) {
        if (!player.isOnline()) {
            return;
        }
        if (holder instanceof MarketHolder marketHolder) {
            if (marketHolder.page > 0) {
                openMarket(player, marketHolder.page - 1, marketHolder.search);
            }
            return;
        }
        if (holder instanceof StallHolder stallHolder) {
            MarketStall stall = stalls.get(stallHolder.owner);
            if (stall != null && stallHolder.page > 0) {
                openStall(player, stall, stallHolder.page - 1, stallHolder.search);
            } else {
                openMarket(player, 0, stallHolder.search);
            }
            return;
        }
        if (holder instanceof ManageHolder manageHolder) {
            if (manageHolder.page > 0) {
                openManage(player, manageHolder.page - 1);
            } else {
                openMarket(player, 0, null);
            }
            return;
        }
        if (holder instanceof ListingHolder) {
            openManage(player);
            return;
        }
        if (holder instanceof StockHolder stockHolder) {
            MarketListing listing = listing(stockHolder.owner, stockHolder.listing);
            if (listing != null) {
                openListingManagement(player, listing);
            } else {
                openManage(player);
            }
        }
    }

    private boolean isMarketHolder(InventoryHolder holder) {
        return holder instanceof MarketHolder
            || holder instanceof StallHolder
            || holder instanceof ManageHolder
            || holder instanceof ListingHolder
            || holder instanceof StockHolder
            || holder instanceof AllListingsHolder
            || holder instanceof PurchaseHolder
            || holder instanceof ShulkerPreviewHolder;
    }

    private void returnItems(Player player, ItemStack[] items) {
        for (ItemStack item : items) {
            returnItem(player, item);
        }
    }

    private void returnItem(Player player, ItemStack item) {
        if (!isSellable(item)) {
            return;
        }
        player.getInventory().addItem(item.clone()).values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private boolean isMatchingStockItem(ItemStack item, MarketListing listing) {
        return isSellable(item) && item.isSimilar(listing.displayItem);
    }

    private int stockCount(MarketListing listing) {
        int total = 0;
        for (ItemStack item : listing.stock) {
            if (isMatchingStockItem(item, listing)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private boolean isSellable(ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getType() != Material.BEDROCK && item.getAmount() > 0;
    }

    private String displayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }
        String[] parts = item.getType().name().toLowerCase(Locale.US).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String trimName(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "Market Stall";
        }
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US).trim();
    }

    private int maxPage(int size) {
        return Math.max(1, (int) Math.ceil(size / (double) CONTENT_SLOTS));
    }

    private int clampPage(int page, int maxPage) {
        return Math.max(0, Math.min(page, maxPage - 1));
    }

    private void playDing(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.2F);
    }

    private Component color(String text) {
        return ColorUtil.colorize(text == null ? "" : text);
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return isSellable(item) ? item.clone() : null;
    }

    private ItemStack[] copyContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[MAX_STOCK_SLOTS];
        for (int i = 0; i < Math.min(MAX_STOCK_SLOTS, contents.length); i++) {
            copy[i] = cloneOrNull(contents[i]);
        }
        return copy;
    }

    private void load() {
        stalls.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("stalls");
        if (root == null) {
            return;
        }
        for (String uuidString : root.getKeys(false)) {
            UUID owner;
            try {
                owner = UUID.fromString(uuidString);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(uuidString);
            if (section == null) {
                continue;
            }
            MarketStall stall = new MarketStall(owner,
                section.getString("owner-name", "Unknown"),
                section.getString("name", "Market Stall"),
                section.getInt("slots", DEFAULT_STALL_SLOTS));
            stall.offlineEarnings = new BigDecimal(section.getString("offline-earnings", "0"));
            ConfigurationSection listingsSection = section.getConfigurationSection("listings");
            if (listingsSection != null) {
                for (String listingId : listingsSection.getKeys(false)) {
                    MarketListing listing = loadListing(listingsSection.getConfigurationSection(listingId), listingId);
                    if (listing != null) {
                        stall.listings.put(listing.id, listing);
                    }
                }
            }
            stalls.put(owner, stall);
        }
    }

    private MarketListing loadListing(ConfigurationSection section, String idString) {
        if (section == null) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(idString);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        ItemStack displayItem = section.getItemStack("display-item");
        if (!isSellable(displayItem)) {
            return null;
        }
        MarketListing listing = new MarketListing(id, displayItem, new BigDecimal(section.getString("price", "1")), section.getInt("stack-size", 1));
        listing.announced = section.getBoolean("announced", true);
        ConfigurationSection stockSection = section.getConfigurationSection("stock");
        if (stockSection != null) {
            for (String slot : stockSection.getKeys(false)) {
                try {
                    int index = Integer.parseInt(slot);
                    if (index >= 0 && index < MAX_STOCK_SLOTS) {
                        ItemStack stockItem = stockSection.getItemStack(slot);
                        listing.stock[index] = isMatchingStockItem(stockItem, listing) ? stockItem.clone() : null;
                    }
                } catch (NumberFormatException ignored) {
                    // ignored
                }
            }
        }
        return listing;
    }

    private static final class MarketStall {
        private final UUID owner;
        private String ownerName;
        private String name;
        private int slots;
        private BigDecimal offlineEarnings = BigDecimal.ZERO;
        private final Map<UUID, MarketListing> listings = new LinkedHashMap<>();

        private MarketStall(UUID owner, String ownerName, String name, int slots) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.name = name;
            this.slots = slots;
        }
    }

    private static final class MarketListing {
        private final UUID id;
        private final ItemStack displayItem;
        private BigDecimal price;
        private int stackSize;
        private boolean announced = true;
        private ItemStack[] stock = new ItemStack[MAX_STOCK_SLOTS];

        private MarketListing(UUID id, ItemStack displayItem, BigDecimal price, int stackSize) {
            this.id = id;
            this.displayItem = displayItem;
            this.price = price;
            this.stackSize = stackSize;
        }
    }

    private record MarketHolder(int page, String search) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record StallHolder(UUID owner, int page, String search) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ManageHolder(UUID owner, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ListingHolder(UUID owner, UUID listing) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record StockHolder(UUID owner, UUID listing) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record AllListingsHolder(int page, String search) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record PurchaseHolder(UUID owner, UUID listing, int amount, MarketReturn marketReturn) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ShulkerPreviewHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private sealed interface MarketReturn permits StallReturn, AllListingsReturn {}
    private record StallReturn(UUID owner, int page, String search) implements MarketReturn {}
    private record AllListingsReturn(int page, String search) implements MarketReturn {}
    private record ListingView(MarketStall stall, MarketListing listing) {}

    private record ChatInput(ChatInputType type, UUID listingId) {
    }

    private enum ChatInputType {
        SEARCH,
        NAME,
        PRICE,
        STACK_SIZE
    }
}
