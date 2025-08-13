package com.ariesninja.theJumper.listener;

import com.ariesninja.theJumper.TheJumper;
import com.ariesninja.theJumper.game.PlayerSession;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpawnInterceptListener implements Listener {
    private final TheJumper plugin;
    private final Map<UUID, Long> confirmUntil = new HashMap<>();

    public SpawnInterceptListener(TheJumper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        if (msg == null) return;
        String lower = msg.toLowerCase();
        // Extract first token
        int space = lower.indexOf(' ');
        String token = space == -1 ? lower : lower.substring(0, space);
        if (!"/spawn".equals(token)) return;

        if (plugin.getGameManager() == null || !plugin.getGameManager().isGameActive()) return;
        Player player = event.getPlayer();
        PlayerSession session = plugin.getGameManager().getSessionManager().getSession(player.getUniqueId());
        if (session == null || !session.isActiveInEvent()) return;
        World gameWorld = plugin.getGameConfig().getGameWorld();
        if (gameWorld == null || !player.getWorld().equals(gameWorld)) return;

        long now = System.currentTimeMillis();
        Long until = confirmUntil.get(player.getUniqueId());
        if (until != null && now <= until) {
            // Confirmed; mark session inactive and allow command to proceed
            session.setActiveInEvent(false);
            session.setLastKnownName(player.getName());
            session.setLastKnownLocation(player.getLocation());
            confirmUntil.remove(player.getUniqueId());
            player.sendMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.YELLOW + "Confirmed. Sending you to spawn...");
            return;
        }

        // Ask for confirmation
        confirmUntil.put(player.getUniqueId(), now + 5000L);
        player.sendMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.YELLOW + "Run /spawn again within 5s to confirm leaving the event.");
        event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        confirmUntil.remove(event.getPlayer().getUniqueId());
    }
}


