package com.controlbro.besteconomy;

import com.controlbro.besteconomy.achievement.AchievementService;
import com.controlbro.besteconomy.blackjack.BlackjackCommand;
import com.controlbro.besteconomy.blackjack.BlackjackService;
import com.controlbro.besteconomy.coinflip.CoinflipCommand;
import com.controlbro.besteconomy.coinflip.CoinflipService;
import com.controlbro.besteconomy.chat.ChatService;
import com.controlbro.besteconomy.chat.ChatTagPlaceholderExpansion;
import com.controlbro.besteconomy.chat.TagsCommand;
import com.controlbro.besteconomy.chat.GiveTagCommand;
import com.controlbro.besteconomy.command.BaltopCommand;
import com.controlbro.besteconomy.command.BalanceCommand;
import com.controlbro.besteconomy.command.BugReportCommand;
import com.controlbro.besteconomy.command.CurrencyCommandHandler;
import com.controlbro.besteconomy.command.CurrencyCommandRegistrar;
import com.controlbro.besteconomy.command.EcoCommand;
import com.controlbro.besteconomy.command.PayCommand;
import com.controlbro.besteconomy.command.ReloadCommand;
import com.controlbro.besteconomy.command.GameModeCommand;
import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.DataStore;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.data.MySqlShardBalanceStore;
import com.controlbro.besteconomy.gui.SellCommand;
import com.controlbro.besteconomy.gui.ShopCommand;
import com.controlbro.besteconomy.gui.ShopGuiService;
import com.controlbro.besteconomy.gui.ValuesCommand;
import com.controlbro.besteconomy.home.DelHomeCommand;
import com.controlbro.besteconomy.home.GiveHomesCommand;
import com.controlbro.besteconomy.home.HomeCommand;
import com.controlbro.besteconomy.home.HomeRespawnListener;
import com.controlbro.besteconomy.home.HomeService;
import com.controlbro.besteconomy.home.HomesCommand;
import com.controlbro.besteconomy.home.SetHomeCommand;
import com.controlbro.besteconomy.integration.CoreProtectHook;
import com.controlbro.besteconomy.listener.PlayerJoinListener;
import com.controlbro.besteconomy.links.LinkCommand;
import com.controlbro.besteconomy.rtp.AgreeCommand;
import com.controlbro.besteconomy.rtp.ResetRtpCommand;
import com.controlbro.besteconomy.rtp.RtpCommand;
import com.controlbro.besteconomy.rtp.RtpService;
import com.controlbro.besteconomy.rtp.SetOnboardingSpawnCommand;
import com.controlbro.besteconomy.rtp.OnboardingTestCommand;
import com.controlbro.besteconomy.lock.LockCommand;
import com.controlbro.besteconomy.lock.LockService;
import com.controlbro.besteconomy.lock.TrustAllCommand;
import com.controlbro.besteconomy.lock.TrustCommand;
import com.controlbro.besteconomy.lock.UnlockCommand;
import com.controlbro.besteconomy.market.GiveMarketSlotsCommand;
import com.controlbro.besteconomy.market.MarketCommand;
import com.controlbro.besteconomy.market.MarketService;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.mines.MinesCommand;
import com.controlbro.besteconomy.mines.MinesService;
import com.controlbro.besteconomy.placeholder.InternalPlaceholderService;
import com.controlbro.besteconomy.settings.SettingsCommand;
import com.controlbro.besteconomy.settings.SettingsMenuService;
import com.controlbro.besteconomy.settings.UserSettingsService;
import com.controlbro.besteconomy.teleport.BackCommand;
import com.controlbro.besteconomy.teleport.TeleportService;
import com.controlbro.besteconomy.teleport.TpAcceptCommand;
import com.controlbro.besteconomy.teleport.TpDenyCommand;
import com.controlbro.besteconomy.teleport.TpaCommand;
import com.controlbro.besteconomy.util.DiscordWebhookNotifier;
import com.controlbro.besteconomy.shop.ShopAccountCommand;
import com.controlbro.besteconomy.shop.ShopAccountService;
import com.controlbro.besteconomy.shop.ShopAdminCommand;
import com.controlbro.besteconomy.shop.ShopDatabaseManager;
import com.controlbro.besteconomy.shop.ShopPendingCommandService;
import com.controlbro.besteconomy.shop.ShardAnnounceCommand;
import com.controlbro.besteconomy.shop.ShopTables;
import com.controlbro.besteconomy.vault.VaultEconomyProvider;
import com.controlbro.besteconomy.vanish.VanishCommand;
import com.controlbro.besteconomy.vanish.VanishService;
import com.controlbro.besteconomy.visual.ScoreboardService;
import com.controlbro.besteconomy.visual.TabListService;
import java.math.BigDecimal;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class BestEconomyPlugin extends JavaPlugin {
    private CurrencyManager currencyManager;
    private AchievementService achievementService;
    private EconomyManager economyManager;
    private MySqlShardBalanceStore mySqlShardBalanceStore;
    private MessageManager messageManager;
    private InternalPlaceholderService placeholderService;
    private CurrencyCommandHandler commandHandler;
    private CurrencyCommandRegistrar commandRegistrar;
    private ShopDatabaseManager shopDatabaseManager;
    private ShopAccountService shopAccountService;
    private ShopPendingCommandService shopPendingCommandService;
    private ShopGuiService shopGuiService;
    private MarketService marketService;
    private MinesService minesService;
    private BlackjackService blackjackService;
    private CoinflipService coinflipService;
    private UserSettingsService userSettingsService;
    private SettingsMenuService settingsMenuService;
    private LockService lockService;
    private HomeService homeService;
    private RtpService rtpService;
    private TeleportService teleportService;
    private DiscordWebhookNotifier webhookNotifier;
    private ShopAccountCommand registeredShopAccountCommand;
    private ScoreboardService scoreboardService;
    private TabListService tabListService;
    private BukkitTask autosaveTask;
    private BukkitTask shardRewardTask;
    private BukkitTask autoAnnounceTask;
    private ChatService chatService;
    private VanishService vanishService;
    private CoreProtectHook coreProtectHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureConfigDefaults();
        coreProtectHook = new CoreProtectHook(this);
        messageManager = new MessageManager(this);
        webhookNotifier = new DiscordWebhookNotifier(this);
        currencyManager = new CurrencyManager(this);
        mySqlShardBalanceStore = new MySqlShardBalanceStore(this);
        economyManager = new EconomyManager(currencyManager, new DataStore(this), mySqlShardBalanceStore);
        placeholderService = new InternalPlaceholderService(economyManager, currencyManager);
        messageManager.setPlaceholderService(placeholderService);
        commandHandler = new CurrencyCommandHandler(this, currencyManager, economyManager, messageManager);
        commandRegistrar = new CurrencyCommandRegistrar(this, currencyManager, commandHandler);
        achievementService = new AchievementService(this, economyManager, currencyManager);

        registerCommands();
        commandRegistrar.registerAll();
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this, economyManager, messageManager, rtpService), this);
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                placeholderService.startSession(event.getPlayer());
            }

            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                placeholderService.endSession(event.getPlayer());
            }
        }, this);
        hookVault();
        startAutoSave();
        startShardRewardTask();
        startAutoAnnouncements();
        startAutoAnnouncements();
        startWebshopIntegration();
        startShopGui();
        startMarket();
        startMines();
        startBlackjack();
        startCoinflip();
        startSettings();
        startLocks();
        startHomes();
        startVisuals();
        startChat();
        startVanish();
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (shardRewardTask != null) {
            shardRewardTask.cancel();
        }
        if (autoAnnounceTask != null) {
            autoAnnounceTask.cancel();
        }
        if (shopPendingCommandService != null) {
            shopPendingCommandService.stop();
        }
        if (marketService != null) {
            marketService.save();
        }
        if (minesService != null) {
            minesService.refundActiveGames();
        }
        if (blackjackService != null) {
            blackjackService.refundActiveGames();
        }
        if (coinflipService != null) {
            coinflipService.refundActiveGames();
        }
        if (userSettingsService != null) {
            userSettingsService.save();
        }
        if (lockService != null) {
            lockService.save();
        }
        if (homeService != null) {
            homeService.save();
        }
        if (chatService != null) {
            chatService.saveData();
        }
        if (rtpService != null) {
            rtpService.save();
        }
        stopVisuals();
        if (achievementService != null) {
            achievementService.stop();
        }
        economyManager.save();
        economyManager.shutdown();
        commandRegistrar.unregisterAll();
        HandlerList.unregisterAll(this);
    }

    public void reloadEverything() {
        if (shopPendingCommandService != null) {
            shopPendingCommandService.stop();
        }
        if (marketService != null) {
            marketService.save();
            marketService = null;
        }
        if (minesService != null) {
            minesService.refundActiveGames();
            minesService = null;
        }
        if (blackjackService != null) {
            blackjackService.refundActiveGames();
            blackjackService = null;
        }
        if (coinflipService != null) {
            coinflipService.refundActiveGames();
            coinflipService = null;
        }
        if (userSettingsService != null) {
            userSettingsService.save();
            userSettingsService = null;
        }
        if (lockService != null) {
            lockService.save();
            lockService = null;
        }
        if (homeService != null) {
            homeService.save();
            homeService = null;
        }
        if (chatService != null) {
            chatService.saveData();
        }
        if (rtpService != null) {
            rtpService.save();
            rtpService = null;
        }
        settingsMenuService = null;
        if (achievementService != null) {
            achievementService.stop();
            achievementService = null;
        }
        if (economyManager != null) {
            economyManager.save();
            economyManager.shutdown();
        }
        if (commandRegistrar != null) {
            commandRegistrar.unregisterAll();
        }
        stopVisuals();
        HandlerList.unregisterAll(this);
        reloadConfig();
        ensureConfigDefaults();
        messageManager.reload();
        currencyManager.reload();
        mySqlShardBalanceStore = new MySqlShardBalanceStore(this);
        economyManager = new EconomyManager(currencyManager, new DataStore(this), mySqlShardBalanceStore);
        placeholderService = new InternalPlaceholderService(economyManager, currencyManager);
        messageManager.setPlaceholderService(placeholderService);
        commandHandler = new CurrencyCommandHandler(this, currencyManager, economyManager, messageManager);
        commandRegistrar = new CurrencyCommandRegistrar(this, currencyManager, commandHandler);
        achievementService = new AchievementService(this, economyManager, currencyManager);
        registerCommands();
        commandRegistrar.registerAll();
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this, economyManager, messageManager, rtpService), this);
        Bukkit.getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                placeholderService.startSession(event.getPlayer());
            }

            @org.bukkit.event.EventHandler
            public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                placeholderService.endSession(event.getPlayer());
            }
        }, this);
        Bukkit.getOnlinePlayers().forEach(player -> economyManager.ensurePlayer(player.getUniqueId()));
        startAutoSave();
        startShardRewardTask();
        startWebshopIntegration();
        startShopGui();
        startMarket();
        startMines();
        startBlackjack();
        startCoinflip();
        startSettings();
        startLocks();
        startHomes();
        startVisuals();
        startChat();
        startVanish();
    }

    private void startVisuals() {
        stopVisuals();
        scoreboardService = new ScoreboardService(this, placeholderService, userSettingsService, rtpService);
        tabListService = new TabListService(this, placeholderService);
        scoreboardService.start();
        tabListService.start();
    }

    private void stopVisuals() {
        if (scoreboardService != null) {
            scoreboardService.stop();
            scoreboardService = null;
        }
        if (tabListService != null) {
            tabListService.stop();
            tabListService = null;
        }
    }

    public CoreProtectHook getCoreProtectHook() {
        return coreProtectHook;
    }

    private void registerCommands() {
        Currency defaultCurrency = currencyManager.getDefaultCurrency();
        Currency shardCurrency = currencyManager.getCurrency("shards");
        PluginCommand achievements = getCommand("achievements");
        if (achievements != null && achievementService != null) {
            achievements.setExecutor(achievementService);
        }
        if (defaultCurrency == null) {
            getLogger().severe("Default currency not found in config.yml.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        PluginCommand eco = getCommand("eco");
        if (eco != null) {
            EcoCommand ecoCommand = new EcoCommand(commandHandler, defaultCurrency);
            eco.setExecutor(ecoCommand);
            eco.setTabCompleter(ecoCommand);
        }
        PluginCommand balance = getCommand("balance");
        if (balance != null) {
            BalanceCommand balanceCommand = new BalanceCommand(economyManager, messageManager, defaultCurrency);
            balance.setExecutor(balanceCommand);
            balance.setTabCompleter(balanceCommand);
        }
        PluginCommand pay = getCommand("pay");
        if (pay != null) {
            PayCommand payCommand = new PayCommand(this, economyManager, messageManager, defaultCurrency);
            pay.setExecutor(payCommand);
            pay.setTabCompleter(payCommand);
        }
        PluginCommand shardPay = getCommand("shardpay");
        if (shardPay != null && shardCurrency != null) {
            PayCommand shardPayCommand = new PayCommand(this, economyManager, messageManager, shardCurrency);
            shardPay.setExecutor(shardPayCommand);
            shardPay.setTabCompleter(shardPayCommand);
        }
        PluginCommand baltop = getCommand("baltop");
        if (baltop != null) {
            BaltopCommand baltopCommand = new BaltopCommand(economyManager, messageManager, defaultCurrency);
            baltop.setExecutor(baltopCommand);
            baltop.setTabCompleter(baltopCommand);
        }
        PluginCommand shardtop = getCommand("shardtop");
        if (shardtop != null && shardCurrency != null) {
            BaltopCommand shardTopCommand = new BaltopCommand(economyManager, messageManager, shardCurrency);
            shardtop.setExecutor(shardTopCommand);
            shardtop.setTabCompleter(shardTopCommand);
        }
        PluginCommand reload = getCommand("sheepsquid");
        if (reload != null) {
            reload.setExecutor(new ReloadCommand(this, messageManager));
        }
        PluginCommand bugReport = getCommand("bugreport");
        if (bugReport != null) {
            bugReport.setExecutor(new BugReportCommand(webhookNotifier, messageManager));
        }
        GameModeCommand gameModeCommand = new GameModeCommand(messageManager);
        for (String commandName : java.util.List.of("gamemode", "gms", "gmc", "gmsp", "gma")) {
            PluginCommand gameMode = getCommand(commandName);
            if (gameMode != null) {
                gameMode.setExecutor(gameModeCommand);
                gameMode.setTabCompleter(gameModeCommand);
            }
        }
        registerRtpCommands();
        registerLinkCommands();
    }

    private void registerRtpCommands() {
        if (rtpService != null) {
            rtpService.save();
        }
        rtpService = new RtpService(this);
        PluginCommand rtp = getCommand("rtp");
        if (rtp != null) {
            rtp.setExecutor(new RtpCommand(rtpService));
        }
        PluginCommand agree = getCommand("agree");
        if (agree != null) {
            agree.setExecutor(new AgreeCommand(rtpService));
        }
        PluginCommand onboardingSpawn = getCommand("setonboardingspawn");
        if (onboardingSpawn != null) {
            onboardingSpawn.setExecutor(new SetOnboardingSpawnCommand(rtpService));
        }
        PluginCommand onboardingTest = getCommand("onboardingtest");
        if (onboardingTest != null) {
            onboardingTest.setExecutor(new OnboardingTestCommand(rtpService));
        }
        PluginCommand resetRtp = getCommand("resetrtp");
        if (resetRtp != null) {
            ResetRtpCommand resetRtpCommand = new ResetRtpCommand(rtpService);
            resetRtp.setExecutor(resetRtpCommand);
            resetRtp.setTabCompleter(resetRtpCommand);
        }
    }

    private void registerLinkCommands() {
        registerLinkCommand("discord", "links.discord");
        registerLinkCommand("web", "links.website");
        registerLinkCommand("bans", "links.bans");
        registerLinkCommand("vote", "links.vote");
    }

    private void registerLinkCommand(String commandName, String configPath) {
        PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(new LinkCommand(this, configPath));
        }
    }


    private void registerShopCommands() {
        if (shopAccountService == null || shopPendingCommandService == null) {
            return;
        }
        PluginCommand shardShop = getCommand("shardshop");
        PluginCommand shardAnnounce = getCommand("shardannounce");
        if (shardAnnounce != null) {
            shardAnnounce.setExecutor(new ShardAnnounceCommand(this));
        }
        if (shardShop != null) {
            ShopAccountCommand shopAccountCommand = new ShopAccountCommand(this, shopAccountService, messageManager);
            shardShop.setExecutor(shopAccountCommand);
            shardShop.setTabCompleter(shopAccountCommand);
            Bukkit.getPluginManager().registerEvents(shopAccountCommand, this);
            registeredShopAccountCommand = shopAccountCommand;
        }
        PluginCommand shopAdmin = getCommand("shopadmin");
        if (shopAdmin != null) {
            ShopAdminCommand shopAdminCommand = new ShopAdminCommand(shopPendingCommandService, messageManager);
            shopAdmin.setExecutor(shopAdminCommand);
            shopAdmin.setTabCompleter(shopAdminCommand);
        }
    }

    private void startMarket() {
        if (marketService != null) {
            HandlerList.unregisterAll(marketService);
            marketService.save();
        }
        marketService = new MarketService(this, economyManager, currencyManager, messageManager, webhookNotifier, achievementService);
        Bukkit.getPluginManager().registerEvents(marketService, this);
        PluginCommand market = getCommand("market");
        if (market != null) {
            MarketCommand marketCommand = new MarketCommand(marketService, messageManager);
            market.setExecutor(marketCommand);
            market.setTabCompleter(marketCommand);
        }
        PluginCommand giveMarketSlots = getCommand("givemarketslots");
        if (giveMarketSlots != null) {
            GiveMarketSlotsCommand giveMarketSlotsCommand = new GiveMarketSlotsCommand(marketService, messageManager);
            giveMarketSlots.setExecutor(giveMarketSlotsCommand);
            giveMarketSlots.setTabCompleter(giveMarketSlotsCommand);
        }
    }

    private void startMines() {
        if (minesService != null) {
            HandlerList.unregisterAll(minesService);
            minesService.refundActiveGames();
        }
        minesService = new MinesService(this, economyManager, currencyManager, messageManager);
        Bukkit.getPluginManager().registerEvents(minesService, this);
        PluginCommand mines = getCommand("mines");
        if (mines != null) {
            MinesCommand minesCommand = new MinesCommand(minesService, messageManager);
            mines.setExecutor(minesCommand);
            mines.setTabCompleter(minesCommand);
        }
    }

    private void startBlackjack() {
        if (blackjackService != null) {
            HandlerList.unregisterAll(blackjackService);
            blackjackService.refundActiveGames();
        }
        blackjackService = new BlackjackService(this, economyManager, currencyManager, messageManager);
        Bukkit.getPluginManager().registerEvents(blackjackService, this);
        PluginCommand blackjack = getCommand("bj");
        if (blackjack != null) {
            BlackjackCommand blackjackCommand = new BlackjackCommand(blackjackService, messageManager);
            blackjack.setExecutor(blackjackCommand);
            blackjack.setTabCompleter(blackjackCommand);
        }
    }

    private void startCoinflip() {
        if (coinflipService != null) {
            HandlerList.unregisterAll(coinflipService);
            coinflipService.refundActiveGames();
        }
        coinflipService = new CoinflipService(this, economyManager, currencyManager, messageManager);
        Bukkit.getPluginManager().registerEvents(coinflipService, this);
        PluginCommand coinflip = getCommand("coinflip");
        if (coinflip != null) {
            CoinflipCommand coinflipCommand = new CoinflipCommand(coinflipService, messageManager);
            coinflip.setExecutor(coinflipCommand);
            coinflip.setTabCompleter(coinflipCommand);
        }
    }

    private void startSettings() {
        if (settingsMenuService != null) {
            HandlerList.unregisterAll(settingsMenuService);
        }
        userSettingsService = new UserSettingsService(this);
        settingsMenuService = new SettingsMenuService(userSettingsService, messageManager);
        Bukkit.getPluginManager().registerEvents(settingsMenuService, this);
        PluginCommand settings = getCommand("settings");
        if (settings != null) {
            settings.setExecutor(new SettingsCommand(settingsMenuService, messageManager));
        }
    }


    private void startChat() {
        if (chatService != null) {
            HandlerList.unregisterAll(chatService);
            chatService.saveData();
        }
        chatService = new ChatService(this);
        Bukkit.getPluginManager().registerEvents(chatService, this);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new ChatTagPlaceholderExpansion(chatService).register();
        }
        PluginCommand tags = getCommand("tags");
        if (tags != null) {
            TagsCommand tagsCommand = new TagsCommand(chatService);
            tags.setExecutor(tagsCommand);
            Bukkit.getPluginManager().registerEvents(tagsCommand, this);
        }
        PluginCommand giveTag = getCommand("givetag");
        if (giveTag != null) {
            GiveTagCommand giveTagCommand = new GiveTagCommand(chatService, messageManager);
            giveTag.setExecutor(giveTagCommand);
            giveTag.setTabCompleter(giveTagCommand);
        }
    }

    private void startVanish() {
        if (vanishService != null) {
            HandlerList.unregisterAll(vanishService);
        }
        vanishService = new VanishService(this, messageManager);
        Bukkit.getPluginManager().registerEvents(vanishService, this);
        PluginCommand vanish = getCommand("vanish");
        if (vanish != null) {
            vanish.setExecutor(new VanishCommand(vanishService, messageManager));
        }
    }

    private void startLocks() {
        if (lockService != null) {
            HandlerList.unregisterAll(lockService);
            lockService.save();
        }
        lockService = new LockService(this, messageManager, userSettingsService);
        Bukkit.getPluginManager().registerEvents(lockService, this);
        PluginCommand lock = getCommand("lock");
        if (lock != null) {
            lock.setExecutor(new LockCommand(lockService, messageManager));
        }
        PluginCommand unlock = getCommand("unlock");
        if (unlock != null) {
            unlock.setExecutor(new UnlockCommand(lockService, messageManager));
        }
        PluginCommand trust = getCommand("trust");
        if (trust != null) {
            TrustCommand trustCommand = new TrustCommand(lockService, messageManager);
            trust.setExecutor(trustCommand);
            trust.setTabCompleter(trustCommand);
        }
        PluginCommand trustAll = getCommand("trustall");
        if (trustAll != null) {
            trustAll.setExecutor(new TrustAllCommand(lockService, messageManager));
        }
    }

    private void startHomes() {
        if (homeService != null) {
            homeService.save();
        }
        homeService = new HomeService(this);
        teleportService = new TeleportService(this);
        Bukkit.getPluginManager().registerEvents(teleportService, this);
        Bukkit.getPluginManager().registerEvents(new HomeRespawnListener(homeService), this);

        PluginCommand setHome = getCommand("sethome");
        if (setHome != null) {
            setHome.setExecutor(new SetHomeCommand(homeService));
        }
        PluginCommand home = getCommand("home");
        if (home != null) {
            HomeCommand homeCommand = new HomeCommand(homeService, teleportService);
            home.setExecutor(homeCommand);
            home.setTabCompleter(homeCommand);
        }
        PluginCommand homes = getCommand("homes");
        if (homes != null) {
            homes.setExecutor(new HomesCommand(homeService));
        }
        PluginCommand delHome = getCommand("delhome");
        if (delHome != null) {
            DelHomeCommand delHomeCommand = new DelHomeCommand(homeService);
            delHome.setExecutor(delHomeCommand);
            delHome.setTabCompleter(delHomeCommand);
        }

        PluginCommand tpa = getCommand("tpa");
        if (tpa != null) {
            TpaCommand tpaCommand = new TpaCommand(teleportService, false);
            tpa.setExecutor(tpaCommand);
            tpa.setTabCompleter(tpaCommand);
        }
        PluginCommand tpaHere = getCommand("tpahere");
        if (tpaHere != null) {
            TpaCommand tpahereCommand = new TpaCommand(teleportService, true);
            tpaHere.setExecutor(tpahereCommand);
            tpaHere.setTabCompleter(tpahereCommand);
        }
        PluginCommand tpaccept = getCommand("tpaccept");
        if (tpaccept != null) {
            tpaccept.setExecutor(new TpAcceptCommand(teleportService));
        }
        PluginCommand tpdeny = getCommand("tpdeny");
        if (tpdeny != null) {
            tpdeny.setExecutor(new TpDenyCommand(teleportService));
        }
        PluginCommand back = getCommand("back");
        if (back != null) {
            back.setExecutor(new BackCommand(teleportService));
        }

                PluginCommand giveHomes = getCommand("givehomes");
        if (giveHomes != null) {
            GiveHomesCommand giveHomesCommand = new GiveHomesCommand(homeService);
            giveHomes.setExecutor(giveHomesCommand);
            giveHomes.setTabCompleter(giveHomesCommand);
        }
    }

    private void startShopGui() {
        if (shopGuiService != null) {
            HandlerList.unregisterAll(shopGuiService);
        }
        shopGuiService = new ShopGuiService(this, economyManager, currencyManager, messageManager);
        Bukkit.getPluginManager().registerEvents(shopGuiService, this);
        PluginCommand shop = getCommand("shop");
        if (shop != null) {
            shop.setExecutor(new ShopCommand(shopGuiService, messageManager));
        }
        PluginCommand sell = getCommand("sell");
        if (sell != null) {
            sell.setExecutor(new SellCommand(shopGuiService, messageManager));
        }
        PluginCommand values = getCommand("values");
        if (values != null) {
            values.setExecutor(new ValuesCommand(shopGuiService, messageManager));
        }
    }

    private void startWebshopIntegration() {
        if (shopPendingCommandService != null) {
            shopPendingCommandService.stop();
        }
        if (registeredShopAccountCommand != null) {
            HandlerList.unregisterAll(registeredShopAccountCommand);
            registeredShopAccountCommand = null;
        }
        shopDatabaseManager = new ShopDatabaseManager(this);
        shopAccountService = new ShopAccountService(this, shopDatabaseManager);
        shopPendingCommandService = new ShopPendingCommandService(this, shopDatabaseManager);
        registerShopCommands();
        if (!getConfig().getBoolean("webshop.enabled", true)) {
            return;
        }
        if (!shopDatabaseManager.isEnabled()) {
            return;
        }
        new ShopTables(this, shopDatabaseManager).createAsync(shopPendingCommandService::start);
    }

    private void ensureConfigDefaults() {
        getConfig().addDefault("currency-symbol", "$");
        getConfig().addDefault("default-currency", "money");
        getConfig().addDefault("currencies.Money.symbol", "$");
        getConfig().addDefault("currencies.Money.command-alias", "money");
        getConfig().addDefault("currencies.Money.starting-balance", 0);
        getConfig().addDefault("currencies.Money.max-money", 10000000000000L);
        getConfig().addDefault("currencies.Money.min-money", 0);
        getConfig().addDefault("currencies.Shards.symbol", "✦");
        getConfig().addDefault("currencies.Shards.command-alias", "shards");
        getConfig().addDefault("currencies.Shards.starting-balance", 0);
        getConfig().addDefault("currencies.Shards.max-money", 10000000000000L);
        getConfig().addDefault("currencies.Shards.min-money", 0);
        if (!getConfig().getString("default-currency", "money").equalsIgnoreCase("money")) {
            getConfig().set("default-currency", "money");
        }
        getConfig().set("currency-symbol", "$");
        getConfig().set("currencies.Money.symbol", "$");
        getConfig().set("currencies.Money.command-alias", "money");
        getConfig().set("currencies.Shards.symbol", "✦");
        getConfig().set("currencies.Shards.command-alias", "shards");
        getConfig().addDefault("mysql.enabled", false);
        getConfig().addDefault("mysql.host", "localhost");
        getConfig().addDefault("mysql.port", 3306);
        getConfig().addDefault("mysql.database", "besteconomy");
        getConfig().addDefault("mysql.username", "CHANGE_THIS_USERNAME");
        getConfig().addDefault("mysql.password", "CHANGE_THIS_PASSWORD");
        getConfig().addDefault("mysql.use-ssl", false);
        getConfig().addDefault("mysql.connection-timeout-ms", 10000);
        getConfig().addDefault("mysql.shard-balances.table", "player_balances");
        getConfig().addDefault("mysql.shard-balances.uuid-column", "uuid");
        getConfig().addDefault("mysql.shard-balances.currency-column", "currency");
        getConfig().addDefault("mysql.shard-balances.amount-column", "amount");
        getConfig().addDefault("mysql.shard-balances.currency-value", "Shards");
        getConfig().addDefault("webshop.enabled", true);
        getConfig().addDefault("webshop.pending-check-seconds", 60);
        getConfig().addDefault("webshop.max-commands-per-check", 50);
        getConfig().addDefault("webshop.api-key", "CHANGE_THIS_TO_A_LONG_RANDOM_SECRET");
        getConfig().addDefault("mines.mine-count", 5);
        getConfig().addDefault("mines.multiplier-increase", "0.25");
        getConfig().addDefault("blackjack.blackjack-payout-multiplier", "2.5");
        getConfig().addDefault("blackjack.win-payout-multiplier", "2");
        getConfig().addDefault("settings.keep-inventory-default", false);
        getConfig().addDefault("settings.pvp-default", true);
        getConfig().addDefault("rtp.max-uses", 3);
        getConfig().addDefault("rtp.range", 5000);
        getConfig().addDefault("rtp.min-range", 0);
        getConfig().addDefault("rtp.messages.no-permission", "&cYou do not have permission to use RTP.");
        getConfig().addDefault("rtp.messages.no-uses-left", "&cYou have used all of your RTPs. Ask an admin if you would like more.");
        getConfig().addDefault("rtp.messages.failed", "&cCould not find a safe RTP spot. Please try again later.");
        getConfig().addDefault("rtp.messages.success", "&aTeleported randomly! &7RTP uses remaining: &e%remaining%&7. Ask an admin if you need more.");
        getConfig().addDefault("rtp.messages.reset", "&aReset RTP uses for &e%player%&a.");
        getConfig().addDefault("links.discord", java.util.List.of("&bDiscord: https://discord.example.com"));
        getConfig().addDefault("links.website", java.util.List.of("&bWebsite: https://www.example.com"));
        getConfig().addDefault("links.bans", java.util.List.of("&bBans: https://bans.example.com"));
        getConfig().addDefault("links.vote", java.util.List.of("&eVote Links:", "", "https://vote.com", "https://vote.com", "https://vote.com", "https://vote.com", "", "&aVoting helps others find the server, and rewards you with $1000!"));
        getConfig().addDefault("webshop.shardannounce-format", "&#A855F7✦ &d{player} &fpurchased &d{item} &fwith their shards!");
        getConfig().addDefault("webhooks.market-listings", "");
        getConfig().addDefault("webhooks.bug-reports", "");
        getConfig().addDefault("auto-announcements.enabled", true);
        getConfig().addDefault("auto-announcements.min-seconds", 300);
        getConfig().addDefault("auto-announcements.max-seconds", 480);
        getConfig().addDefault("auto-announcements.lines", java.util.List.of("&d✦ &fSpend your shards at &d/shardshop&f.", "&d✦ &fJoin our Discord with &d/discord&f.", "&d✦ &fVote for rewards using &d/vote&f.", "&d✦ &fVisit our website at &d/website&f."));
        getConfig().addDefault("chat.formatting-enabled", true);
        getConfig().addDefault("chat.format", "{luckperms_prefix}&r{username}{tag} &7>> &f{message}");
        getConfig().addDefault("chat.fallback-prefix", "");
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void startAutoSave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long intervalSeconds = getConfig().getLong("auto-save-interval-seconds", 300);
        if (intervalSeconds <= 0) {
            return;
        }
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(this,
            economyManager::save, intervalSeconds * 20L, intervalSeconds * 20L);
    }


    private void startShardRewardTask() {
        if (shardRewardTask != null) {
            shardRewardTask.cancel();
        }
        if (autoAnnounceTask != null) {
            autoAnnounceTask.cancel();
        }
        if (!getConfig().getBoolean("online-shard-reward.enabled", true)) {
            return;
        }
        Currency shardCurrency = currencyManager.getCurrency("shards");
        if (shardCurrency == null) {
            getLogger().warning("Unable to start online Shards reward task because the default currency is missing.");
            return;
        }
        long intervalSeconds = getConfig().getLong("online-shard-reward.interval-seconds", 60);
        if (intervalSeconds <= 0) {
            return;
        }
        BigDecimal rewardAmount = new BigDecimal(getConfig().getString("online-shard-reward.amount", "1"));
        shardRewardTask = Bukkit.getScheduler().runTaskTimer(this, () ->
            Bukkit.getOnlinePlayers().forEach(player ->
                economyManager.addBalance(player.getUniqueId(), shardCurrency, rewardAmount)),
            intervalSeconds * 20L, intervalSeconds * 20L);
    }


    private void startAutoAnnouncements() {
        if (autoAnnounceTask != null) {
            autoAnnounceTask.cancel();
        }
        if (!getConfig().getBoolean("auto-announcements.enabled", true)) {
            return;
        }
        scheduleAutoAnnouncement();
    }

    private void scheduleAutoAnnouncement() {
        long minSeconds = getConfig().getLong("auto-announcements.min-seconds", 300);
        long maxSeconds = getConfig().getLong("auto-announcements.max-seconds", 600);
        if (minSeconds <= 0) {
            minSeconds = 300;
        }
        if (maxSeconds < minSeconds) {
            maxSeconds = minSeconds;
        }
        long delaySeconds = minSeconds == maxSeconds ? minSeconds : java.util.concurrent.ThreadLocalRandom.current().nextLong(minSeconds, maxSeconds + 1);
        autoAnnounceTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            java.util.List<String> lines = getConfig().getStringList("auto-announcements.lines");
            if (!lines.isEmpty()) {
                int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(lines.size());
                Bukkit.broadcast(com.controlbro.besteconomy.util.ColorUtil.colorize(lines.get(index)));
            }
            scheduleAutoAnnouncement();
        }, delaySeconds * 20L);
    }

    private void hookVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        Currency defaultCurrency = currencyManager.getDefaultCurrency();
        if (defaultCurrency == null) {
            return;
        }
        Economy provider = new VaultEconomyProvider(economyManager, defaultCurrency);
        Bukkit.getServicesManager().register(Economy.class, provider, this, ServicePriority.High);
    }
}
