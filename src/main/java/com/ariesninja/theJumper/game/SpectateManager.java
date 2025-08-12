package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.TheJumper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpectateManager {
    private final TheJumper plugin;
    private final Map<UUID, UUID> spectatorToTarget = new HashMap<>();
    private final Map<UUID, Double> spectatorOffsetZ = new HashMap<>();
    private BukkitTask task;

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
        }.runTaskTimer(plugin, 2L, 2L);
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
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
    }

    public void removeSpectator(Player spectator) {
        spectatorToTarget.remove(spectator.getUniqueId());
        spectatorOffsetZ.remove(spectator.getUniqueId());
        spectator.setFlying(false);
        spectator.setAllowFlight(false);
    }

    public boolean isSpectating(UUID spectatorId) {
        return spectatorToTarget.containsKey(spectatorId);
    }

    private void tick() {
        for (Map.Entry<UUID, UUID> e : spectatorToTarget.entrySet()) {
            Player spec = Bukkit.getPlayer(e.getKey());
            Player target = Bukkit.getPlayer(e.getValue());
            if (spec == null || target == null) continue;
            double dz = spectatorOffsetZ.getOrDefault(spec.getUniqueId(), 7.0);
            Location tLoc = target.getLocation();
            Location view = new Location(tLoc.getWorld(), tLoc.getX(), tLoc.getY() + 1.5, tLoc.getZ() + dz);
            // Face the target
            view.setDirection(tLoc.clone().add(0, 1.2, 0).toVector().subtract(view.toVector()));
            spec.teleport(view);
        }
    }
}


