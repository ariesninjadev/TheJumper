package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.TheJumper;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpectateManager {
    private final TheJumper plugin;
    private final Map<UUID, UUID> spectatorToTarget = new HashMap<>();
    private final Map<UUID, Double> spectatorOffsetZ = new HashMap<>();
    private BukkitTask task;
    private static final double SMOOTH_ALPHA = 0.35; // 0..1 smoothing factor per tick

    public SpectateManager(TheJumper plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) task.cancel();
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        spectatorToTarget.clear();
        spectatorOffsetZ.clear();
    }

    public void addSpectator(Player spectator, Player target) {
        spectatorToTarget.put(spectator.getUniqueId(), target.getUniqueId());
        // default side offset
        spectatorOffsetZ.put(spectator.getUniqueId(), 7.0);
        spectator.setGameMode(GameMode.SPECTATOR);
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
    }

    public void removeSpectator(Player spectator) {
        spectatorToTarget.remove(spectator.getUniqueId());
        spectatorOffsetZ.remove(spectator.getUniqueId());
        spectator.setFlying(false);
        spectator.setAllowFlight(false);
        // Send to spawn via console to ensure safe state
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + spectator.getName());
    }

    public void removeSpectatorById(UUID spectatorId) {
        spectatorToTarget.remove(spectatorId);
        spectatorOffsetZ.remove(spectatorId);
    }

    public void handleTargetQuit(UUID targetId) {
        java.util.List<UUID> toRemove = new java.util.ArrayList<>();
        for (Map.Entry<UUID, UUID> e : spectatorToTarget.entrySet()) {
            if (targetId.equals(e.getValue())) toRemove.add(e.getKey());
        }
        for (UUID specId : toRemove) {
            Player spec = Bukkit.getPlayer(specId);
            spectatorToTarget.remove(specId);
            spectatorOffsetZ.remove(specId);
            if (spec != null) {
                spec.setGameMode(GameMode.ADVENTURE);
                spec.setFlying(false);
                spec.setAllowFlight(false);
                // Send to spawn via console (compat with spawn plugins)
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + spec.getName());
                spec.sendMessage(org.bukkit.ChatColor.YELLOW + "Target left. Stopped spectating.");
            }
        }
    }

    public boolean isSpectating(UUID spectatorId) {
        return spectatorToTarget.containsKey(spectatorId);
    }

    private void tick() {
        for (Map.Entry<UUID, UUID> e : spectatorToTarget.entrySet()) {
            Player spec = Bukkit.getPlayer(e.getKey());
            Player target = Bukkit.getPlayer(e.getValue());
            if (spec == null) continue;
            if (target == null) {
                // Target vanished: stop spectating now
                spec.setGameMode(GameMode.ADVENTURE);
                spec.setFlying(false);
                spec.setAllowFlight(false);
                spectatorToTarget.remove(e.getKey());
                spectatorOffsetZ.remove(e.getKey());
                // Send to spawn to avoid falling/fly-kick
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + spec.getName());
                spec.sendMessage(org.bukkit.ChatColor.YELLOW + "Target left. Stopped spectating.");
                continue;
            }
            // Stop if target is not in the event anymore or not in event world
            if (TheJumper.getInstance().getGameManager() == null
                    || !TheJumper.getInstance().getGameManager().isGameActive()
                    || TheJumper.getInstance().getGameConfig().getGameWorld() == null) {
                continue; // let command /unspec handle state when event inactive
            }
            com.ariesninja.theJumper.game.PlayerSession tSess = TheJumper.getInstance().getGameManager().getSessionManager().getSession(target.getUniqueId());
            org.bukkit.World eventWorld = TheJumper.getInstance().getGameConfig().getGameWorld();
            if (tSess == null || !tSess.isActiveInEvent() || !target.getWorld().equals(eventWorld)) {
                spectatorToTarget.remove(e.getKey());
                spectatorOffsetZ.remove(e.getKey());
                spec.setGameMode(GameMode.ADVENTURE);
                spec.setFlying(false);
                spec.setAllowFlight(false);
                // Send to spawn to avoid falling/fly-kick
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + spec.getName());
                spec.sendMessage(org.bukkit.ChatColor.YELLOW + "Target is not in the event. Stopped spectating.");
                continue;
            }
            double dz = spectatorOffsetZ.getOrDefault(spec.getUniqueId(), 7.0);
            Location tLoc = target.getLocation();
            Location desired = new Location(tLoc.getWorld(), tLoc.getX(), tLoc.getY() + 1.5, tLoc.getZ() + dz);
            // Face the target
            desired.setDirection(tLoc.clone().add(0, 1.2, 0).toVector().subtract(desired.toVector()));

            // If changing worlds, snap immediately
            if (!spec.getWorld().equals(desired.getWorld())) {
                spec.setGameMode(GameMode.SPECTATOR);
                spec.setAllowFlight(true);
                spec.setFlying(true);
                spec.teleport(desired);
                continue;
            }

            Location current = spec.getLocation();
            if (spec.getGameMode() != GameMode.SPECTATOR) {
                spec.setGameMode(GameMode.SPECTATOR);
                spec.setAllowFlight(true);
                spec.setFlying(true);
            }
            Vector delta = desired.toVector().subtract(current.toVector());
            double distance = delta.length();
            Vector step = delta.multiply(SMOOTH_ALPHA);
            Location next = current.clone().add(step);
            // Smooth yaw/pitch toward target
            float nextYaw = lerpAngle(current.getYaw(), desired.getYaw(), (float) SMOOTH_ALPHA);
            float nextPitch = lerpAngle(current.getPitch(), desired.getPitch(), (float) SMOOTH_ALPHA);
            next.setYaw(nextYaw);
            next.setPitch(nextPitch);

            if (distance > 16.0) {
                // Large gap: snap to avoid long easing
                spec.teleport(desired);
            } else {
                spec.teleport(next);
            }
        }
    }

    private static float lerpAngle(float a, float b, float t) {
        float diff = wrapDegrees(b - a);
        return a + diff * t;
    }

    private static float wrapDegrees(float angle) {
        angle = angle % 360.0f;
        if (angle >= 180.0f) angle -= 360.0f;
        if (angle < -180.0f) angle += 360.0f;
        return angle;
    }
}


