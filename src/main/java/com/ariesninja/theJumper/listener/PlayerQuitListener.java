package com.ariesninja.theJumper.listener;

import com.ariesninja.theJumper.TheJumper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerQuitListener implements Listener {
    private final TheJumper plugin;

    public PlayerQuitListener(TheJumper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Do not remove session on quit; keep scores on the board. Free lane only.
        if (plugin.getGameManager() == null) return;
        plugin.getGameManager().handlePlayerQuitKeepScore(event.getPlayer());
        // Remember last known name for scoreboard and last location for auto-return
        com.ariesninja.theJumper.game.PlayerSession s = plugin.getGameManager().getSessionManager().getSession(event.getPlayer().getUniqueId());
        if (s != null) {
            s.setLastKnownName(event.getPlayer().getName());
            // If quitting outside the event world, don't record that location as their spot
            org.bukkit.World eventWorld = plugin.getGameConfig().getGameWorld();
            if (event.getPlayer().getWorld().equals(eventWorld)) {
                s.setLastKnownLocation(event.getPlayer().getLocation());
            }
        }
    }
}


