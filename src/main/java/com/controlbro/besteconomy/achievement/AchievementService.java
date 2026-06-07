package com.controlbro.besteconomy.achievement;

import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.BalanceChangeListener;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.util.NumberUtil;
import java.io.File;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

@SuppressWarnings("deprecation")
public class AchievementService implements Listener, CommandExecutor, BalanceChangeListener {
    private static final String CRITERION = "complete";
    private static final String ROOT_ID = "achievements";
    private static final long DRAGON_SUMMON_WINDOW_MILLIS = 180_000;
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final AchievementStore store;
    private final Map<UUID, AchievementProgress> progressByPlayer;
    private final Map<String, AchievementDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, CrystalPlacement> recentCrystalPlacements = new HashMap<>();
    private BukkitTask saveTask;
    private BukkitTask nightTask;
    private boolean dirty;

    public AchievementService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.store = new AchievementStore(plugin);
        this.progressByPlayer = store.load();
        loadDefinitions();
        loadAdvancements();
        economyManager.addBalanceChangeListener(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        int saveSeconds = Math.max(30, plugin.getConfig().getInt("achievements.save-interval-seconds", 300));
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::saveIfDirty, saveSeconds * 20L, saveSeconds * 20L);
        nightTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateNights, 20L, 20L);
        Bukkit.getOnlinePlayers().forEach(this::syncPlayer);
    }

    private void loadDefinitions() {
        File file = new File(plugin.getDataFolder(), "achievements.yml");
        if (!file.exists()) plugin.saveResource("achievements.yml", false);
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("achievements");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            ConfigurationSection value = section.getConfigurationSection(id);
            if (value == null || !value.getBoolean("enabled", true)) continue;
            Material icon = Material.matchMaterial(value.getString("icon", "NETHER_STAR"));
            if (icon == null) icon = Material.NETHER_STAR;
            definitions.put(id, new AchievementDefinition(id, value.getString("name", id),
                value.getString("description", ""), icon, value.getString("frame", "task"),
                value.getString("parent"), value.getString("type", id),
                new BigDecimal(value.getString("target", "1"))));
        }
    }

    private void loadAdvancements() {
        NamespacedKey rootKey = key(ROOT_ID);
        Bukkit.getUnsafe().removeAdvancement(rootKey);
        Bukkit.getUnsafe().loadAdvancement(rootKey, "{\"display\":{\"icon\":{\"id\":\"minecraft:heart_of_the_sea\"},"
            + "\"title\":{\"text\":\"SquidCore Achievements\"},\"description\":{\"text\":\"Custom server achievements\"},"
            + "\"background\":\"minecraft:textures/gui/advancements/backgrounds/stone.png\",\"frame\":\"task\","
            + "\"show_toast\":false,\"announce_to_chat\":false,\"hidden\":false},\"criteria\":{\"" + CRITERION
            + "\":{\"trigger\":\"minecraft:impossible\"}}}");
        for (AchievementDefinition definition : definitions.values()) {
            NamespacedKey key = key(definition.id());
            Bukkit.getUnsafe().removeAdvancement(key);
            Bukkit.getUnsafe().loadAdvancement(key, advancementJson(definition));
        }
    }

    private String advancementJson(AchievementDefinition definition) {
        String parentId = definition.parent() == null || definition.parent().isBlank() ? ROOT_ID : definition.parent();
        String parent = "\"parent\":\"" + plugin.getName().toLowerCase() + ":" + escape(parentId) + "\",";
        return "{" + parent + "\"display\":{\"icon\":{\"id\":\"minecraft:" + definition.icon().getKey().getKey()
            + "\"},\"title\":{\"text\":\"" + escape(definition.name()) + "\"},\"description\":{\"text\":\""
            + escape(definition.description()) + "\"},\"frame\":\"" + escape(definition.frame())
            + "\",\"show_toast\":true,\"announce_to_chat\":false,\"hidden\":false},\"criteria\":{\"" + CRITERION
            + "\":{\"trigger\":\"minecraft:impossible\"}}}";
    }

    private String escape(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void stop() {
        economyManager.removeBalanceChangeListener(this);
        if (saveTask != null) saveTask.cancel();
        if (nightTask != null) nightTask.cancel();
        saveIfDirty();
        Bukkit.getUnsafe().removeAdvancement(key(ROOT_ID));
        definitions.keySet().forEach(id -> Bukkit.getUnsafe().removeAdvancement(key(id)));
    }

    public void recordMarketListing(Player player) {
        AchievementProgress progress = progress(player.getUniqueId());
        if (!progress.marketListingCreated) {
            progress.marketListingCreated = true;
            dirty = true;
        }
        evaluate(player.getUniqueId(), "entrepreneur");
    }

    @Override
    public void onBalanceChanged(UUID uuid, Currency currency, BigDecimal oldBalance, BigDecimal newBalance, BigDecimal earnedAmount) {
        if (currency.getName().equalsIgnoreCase("shards") && earnedAmount.signum() > 0) {
            AchievementProgress progress = progress(uuid);
            progress.lifetimeShards = progress.lifetimeShards.add(earnedAmount);
            dirty = true;
            evaluate(uuid, "squid_rich");
        }
        if (currency.getName().equalsIgnoreCase(currencyManager.getDefaultCurrency().getName())) {
            definitions.values().stream().filter(definition -> definition.type().equalsIgnoreCase("money"))
                .forEach(definition -> evaluate(uuid, definition.id()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        AchievementProgress progress = progress(event.getPlayer().getUniqueId());
        progress.blocksPlaced++;
        dirty = true;
        evaluate(event.getPlayer().getUniqueId(), "bob_the_builder");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCrystalPlace(EntityPlaceEvent event) {
        if (event.getEntity() instanceof EnderCrystal && event.getPlayer() != null) {
            recentCrystalPlacements.put(event.getPlayer().getUniqueId(),
                new CrystalPlacement(event.getEntity().getLocation(), System.currentTimeMillis()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDragonSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        long now = System.currentTimeMillis();
        recentCrystalPlacements.entrySet().removeIf(entry -> now - entry.getValue().time > DRAGON_SUMMON_WINDOW_MILLIS);
        // Bukkit does not expose the player who initiated an Ender Dragon respawn. Correlate the spawned dragon
        // with the nearest recent end-crystal placement at the exit portal instead of crediting natural spawns.
        Optional<Map.Entry<UUID, CrystalPlacement>> summoner = recentCrystalPlacements.entrySet().stream()
            .filter(entry -> sameWorldAndNear(entry.getValue().location, event.getLocation()))
            .min(Comparator.comparingDouble(entry -> entry.getValue().location.distanceSquared(event.getLocation())));
        if (summoner.isPresent()) {
            UUID uuid = summoner.get().getKey();
            AchievementProgress progress = progress(uuid);
            progress.dragonSummons++;
            recentCrystalPlacements.remove(uuid);
            dirty = true;
            evaluateDragonAchievements(uuid);
        }
    }

    private boolean sameWorldAndNear(Location first, Location second) {
        return first.getWorld() != null && first.getWorld().equals(second.getWorld()) && first.distanceSquared(second) <= 256 * 256;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon) || event.getEntity().getKiller() == null) return;
        UUID uuid = event.getEntity().getKiller().getUniqueId();
        progress(uuid).dragonKills++;
        dirty = true;
        evaluateDragonAchievements(uuid);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        AchievementProgress progress = progress(event.getPlayer().getUniqueId());
        progress.sleeplessNights = 0;
        progress.activeNight = -1;
        dirty = true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        syncPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Logging out means the player did not stay awake through the complete active night.
        AchievementProgress progress = progress(event.getPlayer().getUniqueId());
        if (progress.activeNight != -1 || progress.sleeplessNights != 0) {
            progress.activeNight = -1;
            progress.sleeplessNights = 0;
            dirty = true;
        }
    }

    private void updateNights() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            World world = player.getWorld();
            long time = world.getTime();
            long day = world.getFullTime() / 24000L;
            AchievementProgress progress = progress(player.getUniqueId());
            if (time >= 13000 && time < 13200 && progress.activeNight == -1) {
                progress.activeNight = day;
                dirty = true;
            } else if (progress.activeNight != -1 && time >= 23000) {
                // Count only when the end-of-night window is reached. A night skipped by sleeping or a time command
                // jumps directly to morning and therefore cannot count as a full survived night.
                if (progress.activeNight == day) progress.sleeplessNights++;
                progress.activeNight = -1;
                dirty = true;
                evaluate(player.getUniqueId(), "five_nights");
            } else if (progress.activeNight != -1 && time < 13000) {
                progress.activeNight = -1;
                progress.sleeplessNights = 0;
                dirty = true;
            }
        }
    }

    private void evaluateDragonAchievements(UUID uuid) {
        evaluate(uuid, "is_this_the_end");
        evaluate(uuid, "only_the_start");
    }

    private void syncPlayer(Player player) {
        grant(player, ROOT_ID);
        definitions.keySet().forEach(id -> evaluate(player.getUniqueId(), id));
        progress(player.getUniqueId()).completed.forEach(id -> grant(player, id));
    }

    private void evaluate(UUID uuid, String id) {
        AchievementDefinition definition = definitions.get(id);
        if (definition == null || progress(uuid).completed.contains(id)) return;
        AchievementProgress progress = progress(uuid);
        boolean reached = switch (definition.type().toLowerCase()) {
            case "money" -> {
                Currency money = currencyManager.getDefaultCurrency();
                yield money != null && economyManager.getBalance(uuid, money).compareTo(definition.target()) >= 0;
            }
            case "market_listing" -> progress.marketListingCreated;
            case "lifetime_shards" -> progress.lifetimeShards.compareTo(definition.target()) >= 0;
            case "blocks_placed" -> BigDecimal.valueOf(progress.blocksPlaced).compareTo(definition.target()) >= 0;
            case "dragons" -> BigDecimal.valueOf(progress.dragonSummons).compareTo(definition.target()) >= 0
                && BigDecimal.valueOf(progress.dragonKills).compareTo(definition.target()) >= 0;
            case "sleepless_nights" -> BigDecimal.valueOf(progress.sleeplessNights).compareTo(definition.target()) >= 0;
            default -> false;
        };
        if (reached && (definition.parent() == null || progress.completed.contains(definition.parent()))) complete(uuid, id);
    }

    private void complete(UUID uuid, String id) {
        if (!progress(uuid).completed.add(id)) return;
        dirty = true;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) grant(player, id);
        definitions.values().stream().filter(definition -> id.equals(definition.parent()))
            .forEach(definition -> evaluate(uuid, definition.id()));
    }

    private void grant(Player player, String id) {
        Advancement advancement = Bukkit.getAdvancement(key(id));
        if (advancement != null && !player.getAdvancementProgress(advancement).isDone()) {
            player.getAdvancementProgress(advancement).awardCriteria(CRITERION);
        }
    }

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id);
    }

    private AchievementProgress progress(UUID uuid) {
        return progressByPlayer.computeIfAbsent(uuid, ignored -> new AchievementProgress());
    }

    private void saveIfDirty() {
        if (!dirty) return;
        store.save(progressByPlayer);
        dirty = false;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        AchievementProgress progress = progress(player.getUniqueId());
        player.sendMessage("§6§lAchievements");
        for (AchievementDefinition definition : definitions.values()) {
            String marker = progress.completed.contains(definition.id()) ? "§a✔" : "§7•";
            player.sendMessage(marker + " §e" + definition.name() + " §7- " + displayProgress(player, progress, definition));
        }
        return true;
    }

    private String displayProgress(Player player, AchievementProgress progress, AchievementDefinition definition) {
        String target = NumberUtil.format(definition.target());
        return switch (definition.type().toLowerCase()) {
            case "money" -> NumberUtil.format(economyManager.getBalance(player.getUniqueId(), currencyManager.getDefaultCurrency())) + "/" + target;
            case "market_listing" -> progress.marketListingCreated ? "1/1" : "0/1";
            case "lifetime_shards" -> NumberUtil.format(progress.lifetimeShards) + "/" + target + " Shards earned";
            case "blocks_placed" -> NumberUtil.format(BigDecimal.valueOf(progress.blocksPlaced)) + "/" + target + " blocks";
            case "dragons" -> progress.dragonSummons + "/" + target + " summoned, " + progress.dragonKills + "/" + target + " killed";
            case "sleepless_nights" -> progress.sleeplessNights + "/" + target + " nights";
            default -> definition.description();
        };
    }

    private record CrystalPlacement(Location location, long time) { }
}
