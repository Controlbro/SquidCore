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
import org.bukkit.block.DoubleChest;
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
    private final MessageManager messageManager;
    private final File file;
    private final Map<String, LockEntry> locks = new HashMap<>();
    private final Set<UUID> lockMode = new HashSet<>();
    private final Set<UUID> unlockMode = new HashSet<>();
    private final Map<UUID, UUID> trustMode = new HashMap<>();
    private final Map<UUID, Set<UUID>> globalTrust = new HashMap<>();

    public LockService(JavaPlugin plugin, MessageManager messageManager) {
        this.messageManager = messageManager;
        this.file = new File(plugin.getDataFolder(), "locks.yml");
        load();
    }

    public void toggleLockMode(Player player) {
        UUID playerId = player.getUniqueId();
        unlockMode.remove(playerId);
        trustMode.remove(playerId);
        if (lockMode.remove(playerId)) {
            messageManager.send(player, "lock.mode-disabled", null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            return;
        }
        lockMode.add(playerId);
        messageManager.send(player, "lock.mode-enabled", null);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
    }

    public void toggleUnlockMode(Player player) {
        UUID playerId = player.getUniqueId();
        lockMode.remove(playerId);
        trustMode.remove(playerId);
        if (unlockMode.remove(playerId)) {
            messageManager.send(player, "lock.unlock-mode-disabled", null);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 0.8F);
            return;
        }
        unlockMode.add(playerId);
        messageManager.send(player, "lock.unlock-mode-enabled", null);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
    }

    public void startTrustMode(Player player, UUID trustedPlayerId, String trustedPlayerName) {
        UUID playerId = player.getUniqueId();
        lockMode.remove(playerId);
        unlockMode.remove(playerId);
        trustMode.put(playerId, trustedPlayerId);
        messageManager.send(player, "lock.trust-mode-enabled", Map.of("player", trustedPlayerName));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
    }

    public void trustAll(Player owner, UUID trustedPlayerId, String trustedPlayerName) {
        globalTrust.computeIfAbsent(owner.getUniqueId(), key -> new HashSet<>()).add(trustedPlayerId);
        save();
        messageManager.send(owner, "lock.trustall-success", Map.of("player", trustedPlayerName));
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
        ConfigurationSection globalTrustSection = config.createSection("global-trust");
        for (Map.Entry<UUID, Set<UUID>> entry : globalTrust.entrySet()) {
            globalTrustSection.set(entry.getKey().toString(), entry.getValue().stream().map(UUID::toString).toList());
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
        Set<String> affectedKeys = keysForInteraction(block);
        LockEntry lock = findLockEntry(affectedKeys);
        UUID playerId = player.getUniqueId();

        if (lockMode.contains(playerId)) {
            event.setCancelled(true);
            handleLockMode(player, affectedKeys, lock);
            return;
        }
        if (unlockMode.contains(playerId)) {
            event.setCancelled(true);
            handleUnlockMode(player, affectedKeys, lock);
            return;
        }
        if (trustMode.containsKey(playerId)) {
            event.setCancelled(true);
            handleTrustMode(player, affectedKeys, lock, trustMode.get(playerId));
            return;
        }

        if (lock != null && !canAccess(lock, playerId) && !player.hasPermission("besteconomy.lock.bypass")) {
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
        Set<String> affectedKeys = keysForInteraction(block);
        LockEntry lock = findLockEntry(affectedKeys);
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
        for (String key : affectedKeys) {
            locks.remove(key);
        }
        save();
        messageManager.send(player, "lock.removed", null);
    }

    private void handleLockMode(Player player, Set<String> keys, LockEntry existingLock) {
        if (existingLock == null) {
            LockEntry newEntry = new LockEntry(player.getUniqueId(), player.getName(), new HashSet<>());
            for (String key : keys) {
                locks.put(key, newEntry);
            }
            save();
            messageManager.send(player, "lock.locked", null);
            player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1.0F, 1.2F);
            return;
        }
        if (existingLock.owner.equals(player.getUniqueId())) {
            messageManager.send(player, "lock.already-owned", null);
            return;
        }
        messageManager.send(player, "lock.already-locked", Map.of("owner", existingLock.ownerName));
    }

    private void handleUnlockMode(Player player, Set<String> keys, LockEntry existingLock) {
        if (existingLock == null) {
            messageManager.send(player, "lock.unlock-not-locked", null);
            return;
        }
        if (!existingLock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
            messageManager.send(player, "lock.unlock-not-owner", Map.of("owner", existingLock.ownerName));
            return;
        }
        for (String key : keys) {
            locks.remove(key);
        }
        save();
        messageManager.send(player, "lock.unlock-success", null);
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1.0F, 1.2F);
    }

    private void handleTrustMode(Player player, Set<String> keys, LockEntry existingLock, UUID trustedPlayerId) {
        if (existingLock == null) {
            messageManager.send(player, "lock.trust-not-locked", null);
            return;
        }
        if (!existingLock.owner.equals(player.getUniqueId()) && !player.hasPermission("besteconomy.lock.bypass")) {
            messageManager.send(player, "lock.trust-not-owner", Map.of("owner", existingLock.ownerName));
            return;
        }
        for (String key : keys) {
            LockEntry entry = locks.get(key);
            if (entry != null) {
                entry.trusted.add(trustedPlayerId);
            }
        }
        save();
        messageManager.send(player, "lock.trust-success", null);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.1F);
    }

    private LockEntry findLockEntry(Set<String> keys) {
        for (String key : keys) {
            LockEntry entry = locks.get(key);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    private boolean canAccess(LockEntry lock, UUID playerId) {
        return lock.owner.equals(playerId)
            || lock.trusted.contains(playerId)
            || globalTrust.getOrDefault(lock.owner, Set.of()).contains(playerId);
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

    private Set<String> keysForInteraction(Block block) {
        Set<String> keys = new HashSet<>();
        Block canonical = canonicalBlock(block);
        keys.add(key(canonical));
        if (canonical.getState() instanceof Chest chest && chest.getInventory().getHolder() instanceof DoubleChest doubleChest) {
            Location left = doubleChest.getLeftSide().getLocation();
            Location right = doubleChest.getRightSide().getLocation();
            keys.add(key(left));
            keys.add(key(right));
        }
        return keys;
    }

    private String key(Block block) {
        return key(canonicalBlock(block).getLocation());
    }

    private String key(Location location) {
        return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
    }

    private Block canonicalBlock(Block block) {
        if (block.getBlockData() instanceof Bisected bisected && bisected.getHalf() == Bisected.Half.TOP) {
            return block.getRelative(BlockFace.DOWN);
        }
        return block;
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
                    Set<UUID> trusted = new HashSet<>();
                    for (String trustedId : section.getStringList("trusted")) {
                        try {
                            trusted.add(UUID.fromString(trustedId));
                        } catch (IllegalArgumentException ignored) {
                            // ignored
                        }
                    }
                    locks.put(key, new LockEntry(UUID.fromString(owner), section.getString("owner-name", "Unknown"), trusted));
                } catch (IllegalArgumentException ignored) {
                    // ignored
                }
            }
        }
        ConfigurationSection globalTrustSection = config.getConfigurationSection("global-trust");
        if (globalTrustSection != null) {
            for (String ownerId : globalTrustSection.getKeys(false)) {
                try {
                    UUID owner = UUID.fromString(ownerId);
                    Set<UUID> trusted = new HashSet<>();
                    for (String trustedId : globalTrustSection.getStringList(ownerId)) {
                        try {
                            trusted.add(UUID.fromString(trustedId));
                        } catch (IllegalArgumentException ignored) {
                            // ignored
                        }
                    }
                    globalTrust.put(owner, trusted);
                } catch (IllegalArgumentException ignored) {
                    // ignored
                }
            }
        }
    }

    private record LockEntry(UUID owner, String ownerName, Set<UUID> trusted) {
    }
}
