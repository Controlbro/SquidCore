package com.controlbro.besteconomy.coinflip;

import com.controlbro.besteconomy.coinflip.CoinflipGame.Side;
import com.controlbro.besteconomy.coinflip.CoinflipGame.State;
import com.controlbro.besteconomy.currency.Currency;
import com.controlbro.besteconomy.currency.CurrencyManager;
import com.controlbro.besteconomy.data.EconomyManager;
import com.controlbro.besteconomy.message.MessageManager;
import com.controlbro.besteconomy.util.ColorUtil;
import com.controlbro.besteconomy.util.NumberUtil;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class CoinflipService implements Listener {
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final CurrencyManager currencyManager;
    private final MessageManager messageManager;
    private final Map<UUID, CoinflipGame> gamesByCreator = new HashMap<>();
    private final Map<UUID, CoinflipGame> gamesByPlayer = new HashMap<>();
    private final List<BukkitTask> animationTasks = new ArrayList<>();

    public CoinflipService(JavaPlugin plugin, EconomyManager economyManager, CurrencyManager currencyManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.currencyManager = currencyManager;
        this.messageManager = messageManager;
    }

    public void create(Player creator, BigDecimal bet) {
        Currency shards = shards();
        if (shards == null) {
            messageManager.send(creator, "coinflip.shards-missing", null);
            return;
        }
        if (gamesByPlayer.containsKey(creator.getUniqueId())) {
            messageManager.send(creator, "coinflip.already-active", null);
            return;
        }
        if (economyManager.getAvailableToSpend(creator.getUniqueId(), shards).compareTo(bet) < 0) {
            messageManager.send(creator, "insufficient-shards", null);
            return;
        }
        economyManager.subtractBalance(creator.getUniqueId(), shards, bet);
        CoinflipGame game = new CoinflipGame(creator.getUniqueId(), bet);
        gamesByCreator.put(creator.getUniqueId(), game);
        gamesByPlayer.put(creator.getUniqueId(), game);
        creator.playSound(creator.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.1F);
        messageManager.send(creator, "coinflip.created", Map.of("player", creator.getName(), "bet", NumberUtil.format(bet)));
        broadcast("coinflip.announce-created", Map.of("player", creator.getName(), "bet", NumberUtil.format(bet)));
    }

    public void join(Player challenger, Player requestedCreator) {
        Currency shards = shards();
        if (shards == null) {
            messageManager.send(challenger, "coinflip.shards-missing", null);
            return;
        }
        if (gamesByPlayer.containsKey(challenger.getUniqueId())) {
            messageManager.send(challenger, "coinflip.already-active", null);
            return;
        }
        CoinflipGame game = requestedCreator == null ? firstOpenGame(challenger) : gamesByCreator.get(requestedCreator.getUniqueId());
        if (game == null || game.state != State.OPEN) {
            messageManager.send(challenger, "coinflip.none-open", null);
            return;
        }
        Player creator = Bukkit.getPlayer(game.creator);
        if (creator == null) {
            refundAndRemove(game, "coinflip.cancelled-offline");
            messageManager.send(challenger, "coinflip.none-open", null);
            return;
        }
        if (creator.getUniqueId().equals(challenger.getUniqueId())) {
            messageManager.send(challenger, "coinflip.cannot-join-self", null);
            return;
        }
        if (economyManager.getAvailableToSpend(challenger.getUniqueId(), shards).compareTo(game.bet) < 0) {
            messageManager.send(challenger, "insufficient-shards", null);
            return;
        }
        economyManager.subtractBalance(challenger.getUniqueId(), shards, game.bet);
        game.challenger = challenger.getUniqueId();
        game.state = State.AWAITING_CREATOR_PICK;
        gamesByPlayer.put(challenger.getUniqueId(), game);
        challenger.playSound(challenger.getLocation(), Sound.UI_BUTTON_CLICK, 1.0F, 1.2F);
        creator.playSound(creator.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.4F);
        messageManager.send(challenger, "coinflip.joined", Map.of(
            "creator", creator.getName(),
            "bet", NumberUtil.format(game.bet)));
        messageManager.send(creator, "coinflip.creator-pick", Map.of(
            "challenger", challenger.getName(),
            "bet", NumberUtil.format(game.bet)));
    }

    public boolean isAwaitingCreatorPick(Player player) {
        CoinflipGame game = gamesByCreator.get(player.getUniqueId());
        return game != null && game.state == State.AWAITING_CREATOR_PICK;
    }

    public void pickAndStart(Player creator, Side side) {
        CoinflipGame game = gamesByCreator.get(creator.getUniqueId());
        if (game == null || game.state != State.AWAITING_CREATOR_PICK || game.challenger == null) {
            messageManager.send(creator, "coinflip.no-pick-waiting", null);
            return;
        }
        Player challenger = Bukkit.getPlayer(game.challenger);
        if (challenger == null) {
            refundAndRemove(game, "coinflip.cancelled-offline");
            return;
        }
        game.creatorSide = side;
        game.state = State.FLIPPING;
        broadcast("coinflip.announce-start", Map.of(
            "creator", creator.getName(),
            "creator_side", side.display(),
            "challenger", challenger.getName(),
            "challenger_side", side.opposite().display(),
            "pot", NumberUtil.format(game.bet.multiply(BigDecimal.valueOf(2)))));
        animate(game);
    }

    public void cancel(Player player) {
        CoinflipGame game = gamesByPlayer.get(player.getUniqueId());
        if (game == null) {
            messageManager.send(player, "coinflip.no-active", null);
            return;
        }
        if (game.state == State.FLIPPING) {
            messageManager.send(player, "coinflip.cannot-cancel-flipping", null);
            return;
        }
        refundAndRemove(game, "coinflip.cancelled");
    }

    public void list(Player player) {
        List<CoinflipGame> openGames = gamesByCreator.values().stream()
            .filter(game -> game.state == State.OPEN)
            .sorted(Comparator.comparing(game -> game.bet))
            .toList();
        if (openGames.isEmpty()) {
            messageManager.send(player, "coinflip.none-open", null);
            return;
        }
        messageManager.send(player, "coinflip.list-header", null);
        for (CoinflipGame game : openGames) {
            Player creator = Bukkit.getPlayer(game.creator);
            if (creator != null) {
                messageManager.send(player, "coinflip.list-entry", Map.of(
                    "player", creator.getName(),
                    "bet", NumberUtil.format(game.bet)));
            }
        }
    }

    public List<Player> openCreators() {
        List<Player> creators = new ArrayList<>();
        for (CoinflipGame game : gamesByCreator.values()) {
            if (game.state != State.OPEN) {
                continue;
            }
            Player creator = Bukkit.getPlayer(game.creator);
            if (creator != null) {
                creators.add(creator);
            }
        }
        return creators;
    }

    public void refundActiveGames() {
        for (BukkitTask task : animationTasks) {
            task.cancel();
        }
        animationTasks.clear();
        List<CoinflipGame> games = new ArrayList<>(gamesByCreator.values());
        for (CoinflipGame game : games) {
            refund(game);
        }
        gamesByCreator.clear();
        gamesByPlayer.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CoinflipGame game = gamesByPlayer.get(event.getPlayer().getUniqueId());
        if (game == null || game.state == State.FLIPPING) {
            return;
        }
        refundAndRemove(game, "coinflip.cancelled-offline");
    }

    private void animate(CoinflipGame game) {
        Player creator = Bukkit.getPlayer(game.creator);
        Player challenger = Bukkit.getPlayer(game.challenger);
        if (creator == null || challenger == null) {
            refundAndRemove(game, "coinflip.cancelled-offline");
            return;
        }
        Side winningSide = ThreadLocalRandom.current().nextBoolean() ? Side.HEADS : Side.TAILS;
        BukkitRunnable animation = new BukkitRunnable() {
            private int ticks;
            private Side shownSide = Side.TAILS;

            @Override
            public void run() {
                Player currentCreator = Bukkit.getPlayer(game.creator);
                Player currentChallenger = Bukkit.getPlayer(game.challenger);
                if (currentCreator == null || currentChallenger == null) {
                    refundAndRemove(game, "coinflip.cancelled-offline");
                    cancel();
                    return;
                }
                if (ticks >= 12) {
                    finish(game, winningSide);
                    cancel();
                    return;
                }
                shownSide = shownSide.opposite();
                showFlipFrame(game, currentCreator, currentChallenger, shownSide, ticks);
                ticks++;
            }
        };
        animationTasks.add(animation.runTaskTimer(plugin, 0L, 5L));
    }

    private void showFlipFrame(CoinflipGame game, Player creator, Player challenger, Side side, int step) {
        String color = side == Side.HEADS ? "&6" : "&b";
        Component title = ColorUtil.colorize(color + "&l" + side.display().toUpperCase());
        String dots = ".".repeat((step % 3) + 1);
        Component creatorSubtitle = ColorUtil.colorize("&7Flipping" + dots + " &8(" + game.creatorSide.display() + ")");
        Component challengerSubtitle = ColorUtil.colorize("&7Flipping" + dots + " &8(" + game.challengerSide().display() + ")");
        creator.showTitle(net.kyori.adventure.title.Title.title(title, creatorSubtitle));
        challenger.showTitle(net.kyori.adventure.title.Title.title(title, challengerSubtitle));
        creator.playSound(creator.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 0.8F + (step * 0.04F));
        challenger.playSound(challenger.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8F, 0.8F + (step * 0.04F));
    }

    private void finish(CoinflipGame game, Side winningSide) {
        Player creator = Bukkit.getPlayer(game.creator);
        Player challenger = Bukkit.getPlayer(game.challenger);
        if (creator == null || challenger == null) {
            refundAndRemove(game, "coinflip.cancelled-offline");
            return;
        }
        Player winner = winningSide == game.creatorSide ? creator : challenger;
        Player loser = winner.getUniqueId().equals(creator.getUniqueId()) ? challenger : creator;
        BigDecimal pot = game.bet.multiply(BigDecimal.valueOf(2));
        Currency shards = shards();
        if (shards != null) {
            economyManager.addBalance(winner.getUniqueId(), shards, pot);
        }
        remove(game);
        Component title = ColorUtil.colorize("&a&l" + winningSide.display().toUpperCase() + "!");
        Component subtitle = ColorUtil.colorize("&e" + winner.getName() + " &7wins &5✦" + NumberUtil.format(pot));
        creator.showTitle(net.kyori.adventure.title.Title.title(title, subtitle));
        challenger.showTitle(net.kyori.adventure.title.Title.title(title, subtitle));
        winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.3F);
        loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 0.8F);
        broadcast("coinflip.announce-win", Map.of(
            "winner", winner.getName(),
            "loser", loser.getName(),
            "side", winningSide.display(),
            "amount", NumberUtil.format(pot)));
    }

    private CoinflipGame firstOpenGame(Player challenger) {
        return gamesByCreator.values().stream()
            .filter(game -> game.state == State.OPEN)
            .filter(game -> !game.creator.equals(challenger.getUniqueId()))
            .min(Comparator.comparing(game -> game.bet))
            .orElse(null);
    }

    private void refundAndRemove(CoinflipGame game, String messagePath) {
        refund(game);
        notifyPlayers(game, messagePath);
        remove(game);
    }

    private void refund(CoinflipGame game) {
        Currency shards = shards();
        if (shards == null) {
            return;
        }
        economyManager.addBalance(game.creator, shards, game.bet);
        if (game.challenger != null) {
            economyManager.addBalance(game.challenger, shards, game.bet);
        }
    }

    private void notifyPlayers(CoinflipGame game, String messagePath) {
        Player creator = Bukkit.getPlayer(game.creator);
        Player challenger = game.challenger == null ? null : Bukkit.getPlayer(game.challenger);
        if (creator != null) {
            messageManager.send(creator, messagePath, null);
        }
        if (challenger != null) {
            messageManager.send(challenger, messagePath, null);
        }
    }

    private void remove(CoinflipGame game) {
        gamesByCreator.remove(game.creator);
        gamesByPlayer.remove(game.creator);
        if (game.challenger != null) {
            gamesByPlayer.remove(game.challenger);
        }
    }

    private Currency shards() {
        return currencyManager.getCurrency("shards");
    }

    private void broadcast(String path, Map<String, String> placeholders) {
        Bukkit.broadcast(messageManager.getMessage(path, placeholders));
    }
}
