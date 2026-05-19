package com.controlbro.besteconomy.lock;

import com.controlbro.besteconomy.message.MessageManager;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

public class LockService implements Listener {
    private final JavaPlugin plugin;
    private final MessageManager messageManager;
    private final File file;
    private final Map<String, LockEntry> locks = new HashMap<>();
    private final Set<UUID> lockMode = new HashSet<>();
    private final Set<UUID> unlockMode = new HashSet<>();
    private final Map<UUID, UUID> trustMode = new HashMap<>();
    private final Map<UUID, Set<UUID>> globalTrust = new HashMap<>();

    public LockService(JavaPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
        this.file = new File(plugin.getDataFolder(), "locks.yml");
        load();
    }

    public void toggleUnlockMode(Player player) {
        if (unlockMode.remove(player.getUniqueId())) {
            messageManager.send(player, "lock.unlock-mode-disabled", null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            return;
        }
        unlockMode.add(player.getUniqueId());
        lockMode.remove(player.getUniqueId());
        messageManager.send(player, "lock.unlock-mode-enabled", null);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
    }

    public boolean isTrustModeActive(Player player) {
        return trustMode.containsKey(player.getUniqueId());
    }

    public boolean toggleTrustMode(Player player, UUID targetId, String targetName) {
        if (trustMode.containsKey(player.getUniqueId())) {
            trustMode.remove(player.getUniqueId());
            messageManager.send(player, "lock.trust-mode-disabled", null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            return false;
        }
        trustMode.put(player.getUniqueId(), targetId);
        messageManager.send(player, "lock.trust-mode-enabled", Map.of("player", targetName));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
        return true;
    }

    public void trustAll(Player player, UUID targetId, String targetName) {
        globalTrust.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(targetId);
        save();
        messageManager.send(player, "lock.trust-all-set", Map.of("player", targetName));
    }

    public void toggleLockMode(Player player) {
        if (lockMode.remove(player.getUniqueId())) {
            messageManager.send(player, "lock.mode-disabled", null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            return;
        }
        lockMode.add(player.getUniqueId());
        messageManager.send(player, "lock.mode-enabled", null);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("locks");
        for (Map.Entry<String, LockEntry> entry : locks.entrySet()) {
            ConfigurationSection section = root.createSection(entry.getKey());
            section.set("owner", entry.getValue().owner.toString());
            section.set("owner-name", entry.getValue().ownerName);
            section.set("trusted", entry.getValue().trusted.stream().map(UUID::toString).toList());
        }
        ConfigurationSection globalTrustRoot = config.createSection("global-trust");
        for (Map.Entry<UUID, Set<UUID>> entry : globalTrust.entrySet()) {
            globalTrustRoot.set(entry.getKey().toString(), entry.getValue().stream().map(UUID::toString).toList());
        }
        try {
            config.save(file);
        } catch (IOException ignored) {
            // ignored
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (!isLockable(block)) {
            return;
        }
        String key = key(block);
        LockEntry lock = locks.get(key);
        UUID truster = trustMode.get(player.getUniqueId());
        if (truster != null) {
            event.setCancelled(true);
            if (lock == null) {
                messageManager.send(player, "lock.not-locked", null);
                return;
            }
            if (!lock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
                messageManager.send(player, "lock.trust-not-owner", null);
                return;
            }
            lock.trusted.add(truster);
            save();
            messageManager.send(player, "lock.trusted", Map.of("player", lockDisplayName(truster)));
            return;
        }
        if (lockMode.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (lock == null) {
                locks.put(key, new LockEntry(player.getUniqueId(), player.getName()));
                save();
                messageManager.send(player, "lock.locked", null);
                player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 1.2F);
                return;
            }
            if (lock.owner.equals(player.getUniqueId())) {
                messageManager.send(player, "lock.already-owned", null);
                return;
            }
            messageManager.send(player, "lock.already-locked", Map.of("owner", lock.ownerName));
            return;
        }
        if (unlockMode.contains(player.getUniqueId())) {
            event.setCancelled(true);
            if (lock == null) {
                messageManager.send(player, "lock.not-locked", null);
                return;
            }
            if (!lock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
                messageManager.send(player, "lock.trust-not-owner", null);
                return;
            }
            locks.remove(key);
            save();
            messageManager.send(player, "lock.removed", null);
            return;
        }
        if (lock != null && !lock.owner.equals(player.getUniqueId()) && !isTrusted(lock, player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
            event.setCancelled(true);
            messageManager.send(player, "lock.no-access", Map.of("owner", lock.ownerName));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0F, 1.0F);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isLockable(block)) {
            return;
        }
        String key = key(block);
        LockEntry lock = locks.get(key);
        if (lock == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!lock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
            event.setCancelled(true);
            messageManager.send(player, "lock.no-break", Map.of("owner", lock.ownerName));
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.0F, 1.0F);
            return;
        }
        locks.remove(key);
        save();
        messageManager.send(player, "lock.removed", null);
    }

    private boolean isLockable(Block block) {
        if (block.getState() instanceof Container) {
            return true;
        }
        Material type = block.getType();
        String name = type.name();
        return name.endsWith("_DOOR")
            || name.endsWith("_TRAPDOOR")
            || name.endsWith("_FENCE_GATE")
            || name.endsWith("_SHULKER_BOX")
            || type == Material.CHEST
            || type == Material.TRAPPED_CHEST
            || type == Material.ENDER_CHEST
            || type == Material.FURNACE
            || type == Material.BLAST_FURNACE
            || type == Material.SMOKER
            || type == Material.BARREL
            || type == Material.HOPPER
            || type == Material.DISPENSER
            || type == Material.DROPPER
            || type == Material.BREWING_STAND;
    }

    private String key(Block block) {
        Location location = canonicalBlock(block).getLocation();
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private Block canonicalBlock(Block block) {
        if (block.getState() instanceof Chest chest && chest.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest doubleChest) {
            Location l1 = holderLocation(doubleChest.getLeftSide());
            Location l2 = holderLocation(doubleChest.getRightSide());
            if (l1 == null || l2 == null) {
                return block;
            }
            if (l1.getBlockX() < l2.getBlockX() || (l1.getBlockX() == l2.getBlockX() && l1.getBlockZ() <= l2.getBlockZ())) {
                return l1.getBlock();
            }
            return l2.getBlock();
        }
        if (block.getBlockData() instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    private Location holderLocation(InventoryHolder holder) {
        if (holder instanceof BlockState state) {
            return state.getLocation();
        }
        if (holder instanceof Entity entity) {
            return entity.getLocation();
        }
        return null;
    }

    private void load() {
        locks.clear();
        globalTrust.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("locks");
        if (root != null) {
        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String owner = section.getString("owner");
            if (owner == null) {
                continue;
            }
            try {
                LockEntry entry = new LockEntry(UUID.fromString(owner), section.getString("owner-name", "Unknown"));
                for (String trusted : section.getStringList("trusted")) {
                    try {
                        entry.trusted.add(UUID.fromString(trusted));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                locks.put(key, entry);
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }
        }
        ConfigurationSection trustRoot = config.getConfigurationSection("global-trust");
        if (trustRoot == null) {
            return;
        }
        for (String owner : trustRoot.getKeys(false)) {
            try {
                UUID ownerId = UUID.fromString(owner);
                Set<UUID> trustedSet = new HashSet<>();
                for (String trusted : trustRoot.getStringList(owner)) {
                    try {
                        trustedSet.add(UUID.fromString(trusted));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                globalTrust.put(ownerId, trustedSet);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private boolean isTrusted(LockEntry lock, UUID playerId) {
        return lock.trusted.contains(playerId) || globalTrust.getOrDefault(lock.owner, Set.of()).contains(playerId);
    }

    private String lockDisplayName(UUID uuid) {
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private record LockEntry(UUID owner, String ownerName, Set<UUID> trusted) {
        private LockEntry(UUID owner, String ownerName) {
            this(owner, ownerName, new HashSet<>());
        }
    }
}
