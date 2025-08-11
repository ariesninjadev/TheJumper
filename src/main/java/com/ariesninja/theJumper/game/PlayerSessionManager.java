package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.library.Difficulty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerSessionManager {
    private final Map<UUID, PlayerSession> sessions = new HashMap<>();

    public PlayerSession createSession(UUID playerId, int laneIndex, Difficulty startDifficulty) {
        PlayerSession session = new PlayerSession(playerId, laneIndex, startDifficulty);
        sessions.put(playerId, session);
        return session;
    }

    public PlayerSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public PlayerSession removeSession(UUID playerId) {
        return sessions.remove(playerId);
    }

    public void clear() {
        sessions.clear();
    }

    public java.util.Collection<PlayerSession> getAllSessions() {
        return sessions.values();
    }
}


