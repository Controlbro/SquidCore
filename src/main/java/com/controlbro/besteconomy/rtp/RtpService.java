package com.controlbro.besteconomy.rtp;

import com.controlbro.besteconomy.util.ColorUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class RtpService {
    private static final int MAX_ATTEMPTS = 80;

    private final JavaPlugin plugin;
    private final File file;
    private final Random random = new Random();
    private final Map<UUID, Integer> uses = new HashMap<>();
    private final Set<UUID> agreedPlayers = new HashSet<>();
    private Location onboardingSpawn;

    public RtpService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rtp.yml");
        load();
    }

    public void rtp(Player player) {
        if (!hasAgreed(player)) {
            send(player, plugin.getConfig().getString("rtp.messages.must-agree", "&cYou must use /agree before you can use /rtp."));
            return;
        }
        rtpInternal(player);
    }

    public void agreeAndUseFirstRtp(Player player) {
        if (hasAgreed(player)) {
            send(player, plugin.getConfig().getString("rtp.messages.already-agreed", "&eYou already agreed. You can use /rtp normally."));
            return;
        }
        agreedPlayers.add(player.getUniqueId());
        save();
        send(player, plugin.getConfig().getString("rtp.messages.agreed", "&aThanks for agreeing! Sending you to your first RTP now..."));
        rtpInternal(player);
    }

    public void teleportToOnboardingSpawn(Player player) {
        if (onboardingSpawn == null) {
            return;
        }
        if (!hasAgreed(player)) {
            player.teleport(onboardingSpawn);
        }
    }

    public void setOnboardingSpawn(Location location) {
        onboardingSpawn = location.clone();
        save();
    }

    public boolean hasAgreed(Player player) {
        return agreedPlayers.contains(player.getUniqueId());
    }

    public void resetAgreement(Player player) {
        agreedPlayers.remove(player.getUniqueId());
        uses.remove(player.getUniqueId());
        save();
    }

    private void rtpInternal(Player player) {
        if (!player.hasPermission("besteconomy.rtp.use")) {
            send(player, plugin.getConfig().getString("rtp.messages.no-permission", "&cYou do not have permission to use RTP."));
            return;
        }
        int maxUses = Math.max(0, plugin.getConfig().getInt("rtp.max-uses", 3));
        int used = uses.getOrDefault(player.getUniqueId(), 0);
        if (used >= maxUses) {
            send(player, plugin.getConfig().getString("rtp.messages.no-uses-left", "&cYou have used all of your RTPs. Ask an admin if you would like more."));
            return;
        }
        Location safeLocation = findSafeLocation(player.getWorld());
        if (safeLocation == null) {
            send(player, plugin.getConfig().getString("rtp.messages.failed", "&cCould not find a safe RTP spot. Please try again later."));
            return;
        }
        uses.put(player.getUniqueId(), used + 1);
        save();
        player.teleport(safeLocation);
        player.playSound(safeLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        int remaining = Math.max(0, maxUses - used - 1);
        send(player, plugin.getConfig().getString("rtp.messages.success", "&aTeleported randomly! &7RTP uses remaining: &e%remaining%&7. Ask an admin if you need more.")
            .replace("%remaining%", String.valueOf(remaining))
            .replace("%used%", String.valueOf(used + 1))
            .replace("%max%", String.valueOf(maxUses)));
    }

    public void reset(CommandSender sender, OfflinePlayer target) {
        uses.remove(target.getUniqueId());
        save();
        sender.sendMessage(ColorUtil.colorize(plugin.getConfig().getString("rtp.messages.reset", "&aReset RTP uses for &e%player%&a.")
            .replace("%player%", target.getName() == null ? target.getUniqueId().toString() : target.getName())));
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("uses");
        for (Map.Entry<UUID, Integer> entry : uses.entrySet()) {
            root.set(entry.getKey().toString(), entry.getValue());
        }
        config.set("agreed", agreedPlayers.stream().map(UUID::toString).toList());
        if (onboardingSpawn != null) {
            config.set("onboarding-spawn.world", onboardingSpawn.getWorld() == null ? null : onboardingSpawn.getWorld().getName());
            config.set("onboarding-spawn.x", onboardingSpawn.getX());
            config.set("onboarding-spawn.y", onboardingSpawn.getY());
            config.set("onboarding-spawn.z", onboardingSpawn.getZ());
            config.set("onboarding-spawn.yaw", onboardingSpawn.getYaw());
            config.set("onboarding-spawn.pitch", onboardingSpawn.getPitch());
        }
        try {
            config.save(file);
        } catch (IOException ignored) {
            // ignored
        }
    }

    private Location findSafeLocation(World world) {
        int minRadius = Math.max(0, plugin.getConfig().getInt("rtp.min-range", 0));
        int maxRadius = Math.max(minRadius, plugin.getConfig().getInt("rtp.range", 5000));
        int centerX = plugin.getConfig().getInt("rtp.center.x", world.getSpawnLocation().getBlockX());
        int centerZ = plugin.getConfig().getInt("rtp.center.z", world.getSpawnLocation().getBlockZ());
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = centerX + randomCoordinate(minRadius, maxRadius);
            int z = centerZ + randomCoordinate(minRadius, maxRadius);
            Location candidate = candidateLocation(world, x, z);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private int randomCoordinate(int minRadius, int maxRadius) {
        int value = random.nextInt((maxRadius * 2) + 1) - maxRadius;
        if (Math.abs(value) >= minRadius || minRadius == 0) {
            return value;
        }
        return value < 0 ? -minRadius : minRadius;
    }

    private Location candidateLocation(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            world.loadChunk(x >> 4, z >> 4, true);
        }
        int y = world.getHighestBlockYAt(x, z);
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
            return null;
        }
        Block ground = world.getBlockAt(x, y, z);
        Block feet = world.getBlockAt(x, y + 1, z);
        Block head = world.getBlockAt(x, y + 2, z);
        if (!isSafeGround(ground) || !isSafeAir(feet.getType()) || !isSafeAir(head.getType())) {
            return null;
        }
        return new Location(world, x + 0.5D, y + 1.0D, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
    }

    private boolean isSafeGround(Block block) {
        Material material = block.getType();
        if (material == Material.WATER || material == Material.LAVA || material == Material.MAGMA_BLOCK || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE || material == Material.FIRE || material == Material.SOUL_FIRE) {
            return false;
        }
        if (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return false;
        }
        return material.isSolid();
    }

    private boolean isSafeAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private void send(Player player, String message) {
        player.sendMessage(ColorUtil.colorize(message));
    }

    private void load() {
        uses.clear();
        agreedPlayers.clear();
        onboardingSpawn = null;
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("uses");
        if (root != null) {
            for (String key : root.getKeys(false)) {
            try {
                uses.put(UUID.fromString(key), Math.max(0, root.getInt(key, 0)));
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
            }
        }

        for (String agreed : config.getStringList("agreed")) {
            try {
                agreedPlayers.add(UUID.fromString(agreed));
            } catch (IllegalArgumentException ignored) {
                // ignored
            }
        }
        String worldName = config.getString("onboarding-spawn.world");
        World world = worldName == null ? null : plugin.getServer().getWorld(worldName);
        if (world != null) {
            onboardingSpawn = new Location(
                world,
                config.getDouble("onboarding-spawn.x"),
                config.getDouble("onboarding-spawn.y"),
                config.getDouble("onboarding-spawn.z"),
                (float) config.getDouble("onboarding-spawn.yaw"),
                (float) config.getDouble("onboarding-spawn.pitch")
            );
        }
    }
}

