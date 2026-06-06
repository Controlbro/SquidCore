package com.controlbro.besteconomy.integration;

import java.lang.reflect.Method;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Optional bridge to CoreProtect's public API.
 *
 * <p>The bridge uses reflection so SquidCore never hard-depends on CoreProtect at class-loading time.
 * Servers without CoreProtect (or with an incompatible API) simply receive a disabled hook.</p>
 */
public final class CoreProtectHook {
    private static final int MINIMUM_BLOCK_STATE_API_VERSION = 10;

    private final Object api;
    private final Method logRemoval;
    private final Method logPlacement;

    public CoreProtectHook(JavaPlugin plugin) {
        Object detectedApi = null;
        Method detectedLogRemoval = null;
        Method detectedLogPlacement = null;

        Plugin coreProtect = plugin.getServer().getPluginManager().getPlugin("CoreProtect");
        if (coreProtect != null && coreProtect.isEnabled()) {
            try {
                Object candidateApi = coreProtect.getClass().getMethod("getAPI").invoke(coreProtect);
                if (candidateApi != null) {
                    boolean apiEnabled = (boolean) candidateApi.getClass().getMethod("isEnabled").invoke(candidateApi);
                    int apiVersion = (int) candidateApi.getClass().getMethod("APIVersion").invoke(candidateApi);

                    if (apiEnabled && apiVersion >= MINIMUM_BLOCK_STATE_API_VERSION) {
                        detectedLogRemoval = candidateApi.getClass().getMethod("logRemoval", String.class, BlockState.class);
                        detectedLogPlacement = candidateApi.getClass().getMethod("logPlacement", String.class, BlockState.class);
                        detectedApi = candidateApi;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // CoreProtect is optional; an absent or incompatible API must never affect SquidCore startup.
            }
        }

        api = detectedApi;
        logRemoval = detectedLogRemoval;
        logPlacement = detectedLogPlacement;
    }

    public boolean isAvailable() {
        return api != null;
    }

    /**
     * Records a custom block removal using a snapshot of the block's current, original state.
     *
     * <p>Mass-break systems must call this before setting the block to air or breaking it naturally.
     * CoreProtect cannot produce a correct lookup or rollback if it only sees the resulting air state.</p>
     */
    public void logBlockRemoval(Player player, Block block) {
        logBlockRemoval(player, block == null ? null : block.getState());
    }

    /**
     * Records a previously captured original state. Call this before modifying the corresponding block.
     */
    public void logBlockRemoval(Player player, BlockState originalState) {
        invoke(logRemoval, player, originalState);
    }

    /**
     * Records a custom block placement using a snapshot of the placed block state.
     */
    public void logBlockPlacement(Player player, Block block) {
        logBlockPlacement(player, block == null ? null : block.getState());
    }

    public void logBlockPlacement(Player player, BlockState placedState) {
        invoke(logPlacement, player, placedState);
    }

    private void invoke(Method method, Player player, BlockState state) {
        if (api == null || player == null || state == null) {
            return;
        }

        try {
            method.invoke(api, player.getName(), state);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Logging integrations must not interrupt drops, XP, protection checks, or tool damage.
        }
    }
}
