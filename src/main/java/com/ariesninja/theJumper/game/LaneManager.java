package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.config.GameConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LaneManager {
    private final GameConfig config;
    private final Map<UUID, Integer> playerToLaneIndex = new HashMap<>();
    private int nextLaneIndex = 0;

    public LaneManager(GameConfig config) {
        this.config = config;
    }

    public int allocateLane(Player player) {
        return playerToLaneIndex.computeIfAbsent(player.getUniqueId(), id -> nextLaneIndex++);
    }

    public void freeLane(UUID playerId) {
        playerToLaneIndex.remove(playerId);
    }

    public Integer getLaneIndex(UUID playerId) {
        return playerToLaneIndex.get(playerId);
    }

    public Location getLaneOrigin(World world, int laneIndex) {
        int x = config.getLaneStartX();
        int y = config.getLaneBaseY();
        int z = laneIndex * config.getLaneZSpacing();
        return new Location(world, x, y, z);
    }
}


