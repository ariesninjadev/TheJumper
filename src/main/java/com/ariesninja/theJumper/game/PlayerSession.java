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
    private int totalScore;
    private int scoreAtDifficultyStart;
    private int bestScore;
    private long difficultyStartEpochMs;
    private final List<Location> queuedEndBlocks = new ArrayList<>();
    private final Map<Difficulty, Location> lastEndByDifficulty = new EnumMap<>(Difficulty.class);
    private Location nextStartAt;
    private final List<Location> queuedStartBlocks = new ArrayList<>();
    private Location pendingEndRemoval;
    private int maxGeneratedX;
    private final java.util.Set<Location> placedBlocks = new java.util.HashSet<>();
    private boolean activeInEvent = true;
	private String lastKnownName;
	private Location lastKnownLocation;
    public PlayerSession(UUID playerId, int laneIndex, Difficulty startDifficulty) {
        this.playerId = playerId;
        this.laneIndex = laneIndex;
        this.difficulty = startDifficulty;
        this.difficultyStartEpochMs = System.currentTimeMillis();
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
        // Persist totalScore across difficulties, but snapshot baseline for safe-death resets
        this.scoreAtDifficultyStart = this.totalScore;
        this.difficultyStartEpochMs = System.currentTimeMillis();
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

    public void resetCompletions() {
        this.completionsInDifficulty = 0;
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

    public int getTotalScore() {
        return totalScore;
    }

    public void incrementTotalScore() {
        this.totalScore += 1;
        if (this.totalScore > this.bestScore) {
            this.bestScore = this.totalScore;
        }
    }

    public void resetTotalScoreToDifficultyStart() {
        this.totalScore = this.scoreAtDifficultyStart;
    }

    public void resetAllScore() {
        this.totalScore = 0;
        this.scoreAtDifficultyStart = 0;
    }

    public int getBestScore() {
        return bestScore;
    }

    public void resetBestScore() {
        this.bestScore = 0;
    }

    public java.util.Set<Location> getPlacedBlocks() {
        return placedBlocks;
    }

    public void addPlacedBlock(Location loc) {
        if (loc != null) this.placedBlocks.add(loc.clone());
    }

    public void clearPlacedBlocks() {
        this.placedBlocks.clear();
    }

    public boolean isActiveInEvent() {
        return activeInEvent;
    }

    public void setActiveInEvent(boolean activeInEvent) {
        this.activeInEvent = activeInEvent;
    }

    public int getMaxGeneratedX() {
        return maxGeneratedX;
    }

    public void updateMaxGeneratedX(int x) {
        if (x > this.maxGeneratedX) {
            this.maxGeneratedX = x;
        }
    }

    public long getDifficultyStartEpochMs() {
        return difficultyStartEpochMs;
    }

    public void markDifficultyRestartedNow() {
        this.difficultyStartEpochMs = System.currentTimeMillis();
    }

	public String getLastKnownName() {
		return lastKnownName;
	}

	public void setLastKnownName(String lastKnownName) {
		this.lastKnownName = lastKnownName;
	}

	public Location getLastKnownLocation() {
		return lastKnownLocation == null ? null : lastKnownLocation.clone();
	}

	public void setLastKnownLocation(Location lastKnownLocation) {
		this.lastKnownLocation = lastKnownLocation == null ? null : lastKnownLocation.clone();
	}
}


