package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.library.Difficulty;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerSession {
    private final UUID playerId;
    private final int laneIndex;
    private Difficulty difficulty;
    private int completionsInDifficulty;
    private int strikesInDifficulty;
    private final List<Location> queuedEndBlocks = new ArrayList<>();
    private final Map<Difficulty, Location> lastEndByDifficulty = new EnumMap<>(Difficulty.class);
    private Location nextStartAt;
    private final List<Location> queuedStartBlocks = new ArrayList<>();
    private Location pendingEndRemoval;
    public PlayerSession(UUID playerId, int laneIndex, Difficulty startDifficulty) {
        this.playerId = playerId;
        this.laneIndex = laneIndex;
        this.difficulty = startDifficulty;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getLaneIndex() {
        return laneIndex;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.completionsInDifficulty = 0;
        this.strikesInDifficulty = 0;
        queuedEndBlocks.clear();
        this.nextStartAt = null;
        queuedStartBlocks.clear();
        pendingEndRemoval = null;
    }

    public int getCompletionsInDifficulty() {
        return completionsInDifficulty;
    }

    public void incrementCompletions() {
        this.completionsInDifficulty += 1;
    }

    public int getStrikesInDifficulty() {
        return strikesInDifficulty;
    }

    public void addStrike() {
        this.strikesInDifficulty += 1;
    }

    public void resetStrikes() {
        this.strikesInDifficulty = 0;
    }

    public List<Location> getQueuedEndBlocks() {
        return queuedEndBlocks;
    }

    public void recordEndForCurrentDifficulty(Location endTopLocation) {
        lastEndByDifficulty.put(difficulty, endTopLocation.clone());
    }

    public Location getLastEndFor(Difficulty difficulty) {
        return lastEndByDifficulty.get(difficulty);
    }

    public Location getNextStartAt() {
        return nextStartAt;
    }

    public void setNextStartAt(Location nextStartAt) {
        this.nextStartAt = nextStartAt == null ? null : nextStartAt.clone();
    }

    public List<Location> getQueuedStartBlocks() {
        return queuedStartBlocks;
    }

    public Location getPendingEndRemoval() {
        return pendingEndRemoval;
    }

    public void setPendingEndRemoval(Location loc) {
        this.pendingEndRemoval = loc == null ? null : loc.clone();
    }
}


