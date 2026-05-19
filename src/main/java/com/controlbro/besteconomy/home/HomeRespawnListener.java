package com.controlbro.besteconomy.home;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

public class HomeRespawnListener implements Listener {
    private final HomeService homeService;

    public HomeRespawnListener(HomeService homeService) {
        this.homeService = homeService;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location home = homeService.getDefaultRespawnHome(event.getPlayer().getUniqueId());
        if (home != null) {
            event.setRespawnLocation(home);
        }
    }
}
