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
        if (plugin.getGameManager() == null) return;
        plugin.getGameManager().leave(event.getPlayer());
    }
}


