package com.ariesninja.theJumper.listener;

import com.ariesninja.theJumper.TheJumper;
import com.ariesninja.theJumper.game.PlayerSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class PlayerMovementListener implements Listener {
    private final TheJumper plugin;

    public PlayerMovementListener(TheJumper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (plugin.getGameManager() == null || !plugin.getGameManager().isGameActive()) return;
        Player player = event.getPlayer();
        // Ignore movement outside the event world to prevent false fall detection (e.g., spawn on reconnect)
        org.bukkit.World eventWorld = plugin.getGameConfig().getGameWorld();
        if (eventWorld == null || !eventWorld.equals(player.getWorld())) return;
        PlayerSession session = plugin.getGameManager().getSessionManager().getSession(player.getUniqueId());
        if (session == null || !session.isActiveInEvent()) return;

        // Detect fall below lane base Y - buffer
        int baseY = plugin.getGameConfig().getLaneBaseY();
        int buffer = plugin.getGameConfig().getFallYBuffer();
        if (event.getTo() != null && event.getTo().getY() < baseY - buffer) {
            plugin.getGameManager().handlePlayerFall(player);
            return;
        }

        // Detect reaching the X-plane of the next end target (checkpoint)
        Location to = event.getTo();
        if (to == null) return;
        for (Location targetBlock : session.getQueuedEndBlocks()) {
            if (to.getBlockX() >= targetBlock.getBlockX()) {
                plugin.getGameManager().handlePlayerReachedEnd(player, targetBlock);
                break;
            }
        }

        // Detect reaching the X-plane of the next start block to safely remove previous end
        for (Location startBlock : session.getQueuedStartBlocks()) {
            if (to.getBlockX() >= startBlock.getBlockX()) {
                plugin.getGameManager().handlePlayerSteppedOnStart(player, startBlock);
                break;
            }
        }
    }
}


