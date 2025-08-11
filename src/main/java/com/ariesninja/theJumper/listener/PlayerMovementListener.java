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
        PlayerSession session = plugin.getGameManager().getSessionManager().getSession(player.getUniqueId());
        if (session == null) return;

        // Detect fall below lane base Y - buffer
        int baseY = plugin.getGameConfig().getLaneBaseY();
        int buffer = plugin.getGameConfig().getFallYBuffer();
        if (event.getTo() != null && event.getTo().getY() < baseY - buffer) {
            plugin.getGameManager().handlePlayerFall(player);
            return;
        }

        // Detect stepping onto an end target block (top of the end block)
        Location to = event.getTo();
        if (to == null) return;
        for (Location targetBlock : session.getQueuedEndBlocks()) {
            // We consider the player to have completed the jump when standing on the end block (y equals block y)
            if (to.getBlockX() == targetBlock.getBlockX() && to.getBlockY() == targetBlock.getBlockY() + 1 && to.getBlockZ() == targetBlock.getBlockZ()) {
                plugin.getGameManager().handlePlayerReachedEnd(player, targetBlock);
                break;
            }
        }

        // Detect stepping onto the next start block to safely remove previous end
        for (Location startBlock : session.getQueuedStartBlocks()) {
            if (to.getBlockX() == startBlock.getBlockX() && to.getBlockY() == startBlock.getBlockY() + 1 && to.getBlockZ() == startBlock.getBlockZ()) {
                plugin.getGameManager().handlePlayerSteppedOnStart(player, startBlock);
                break;
            }
        }
    }
}


