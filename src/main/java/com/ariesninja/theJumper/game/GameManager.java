package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.TheJumper;
import com.ariesninja.theJumper.config.GameConfig;
import com.ariesninja.theJumper.library.Difficulty;
import com.ariesninja.theJumper.library.LibraryIndex;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public final class GameManager {
    private final TheJumper plugin;
    private final GameConfig config;
    private final LaneManager laneManager;
    private final PlayerSessionManager sessionManager;
    private final LibraryIndex libraryIndex;
    private final JumpLoader jumpLoader;

    private boolean active = false;
    private long startEpochSeconds = 0L;
    private BukkitTask timerTask;
    private BukkitTask hudTask;

    public GameManager(TheJumper plugin) {
        this.plugin = plugin;
        this.config = plugin.getGameConfig();
        this.laneManager = new LaneManager(config);
        this.sessionManager = new PlayerSessionManager();
        this.libraryIndex = new LibraryIndex(config);
        this.jumpLoader = new JumpLoader(config);
        this.jumpLoader.setLibraryIndex(libraryIndex);
    }

    public boolean isGameActive() {
        return active;
    }

    public PlayerSessionManager getSessionManager() {
        return sessionManager;
    }

    public void startGame() {
        if (active) return;
        World world = config.getGameWorld();
        if (world == null) {
            plugin.getLogger().severe("Game world '" + config.getGameWorldName() + "' is not loaded.");
            return;
        }
        this.active = true;
        this.startEpochSeconds = System.currentTimeMillis() / 1000L;
        Bukkit.broadcastMessage(ChatColor.GREEN + "The Jumper event has started!");
        // Start timer to auto-stop after timeLimit
        if (timerTask != null) timerTask.cancel();
        timerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long elapsed = (System.currentTimeMillis() / 1000L) - startEpochSeconds;
                if (elapsed >= config.getTimeLimitSeconds()) {
                    stopGame();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Start actionbar HUD updater (once per second)
        if (hudTask != null) hudTask.cancel();
        hudTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active) return;
                long now = System.currentTimeMillis() / 1000L;
                long remaining = Math.max(0, (startEpochSeconds + config.getTimeLimitSeconds()) - now);
                String time = String.format("%02d:%02d:%02d", remaining / 3600, (remaining % 3600) / 60, remaining % 60);
                for (PlayerSession s : sessionManager.getAllSessions()) {
                    Player p = Bukkit.getPlayer(s.getPlayerId());
                    if (p == null) continue;
                    String status = s.getStrikesInDifficulty() == 0 ? ChatColor.GREEN + "SAFE" : ChatColor.RED + "DANGER";
                    String msg = ChatColor.AQUA + "Diff " + s.getDifficulty().getDisplayName() + ChatColor.DARK_GRAY + " | "
                            + ChatColor.YELLOW + Math.min(s.getCompletionsInDifficulty() + 1, config.getCompletionsPerDifficulty()) + "/" + config.getCompletionsPerDifficulty() + ChatColor.DARK_GRAY + " | "
                            + status + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + time;
                    p.spigot().sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg)
                    );
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void stopGame() {
        if (!active) return;
        this.active = false;
        Bukkit.broadcastMessage(ChatColor.RED + "The Jumper event has ended.");
        // Cleanup sessions
        sessionManager.clear();
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
    }

    public void shutdown() {
        stopGame();
    }

    public void reload() {
        libraryIndex.reload();
    }

    public void join(Player player) {
        if (!active) {
            player.sendMessage(ChatColor.RED + "The event is not active.");
            return;
        }
        if (config.getGameWorld() == null) {
            player.sendMessage(ChatColor.RED + "Game world not loaded.");
            return;
        }

        int laneIndex = laneManager.allocateLane(player);
        PlayerSession existing = sessionManager.getSession(player.getUniqueId());
        if (existing == null) {
            PlayerSession session = sessionManager.createSession(player.getUniqueId(), laneIndex, Difficulty.I);
            initializePlayerInLane(player, session);
        } else {
            initializePlayerInLane(player, existing);
        }
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        sessionManager.removeSession(id);
        laneManager.freeLane(id);
        player.sendMessage(ChatColor.YELLOW + "You left The Jumper event.");
    }

    private void initializePlayerInLane(Player player, PlayerSession session) {
        World world = config.getGameWorld();
        if (world == null) return;
        Location origin = laneManager.getLaneOrigin(world, session.getLaneIndex());
        jumpLoader.initializeLane(world, session);
        player.teleport(origin.clone().add(0.5, 0, 0.5));
        player.sendMessage(ChatColor.AQUA + "Starting at difficulty " + session.getDifficulty().getDisplayName());
    }

    public void handlePlayerReachedEnd(Player player, Location endBlock) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;
        session.incrementCompletions();
        session.recordEndForCurrentDifficulty(endBlock);
        jumpLoader.onJumpCompleted(config.getGameWorld(), session, endBlock);

        int needed = config.getCompletionsPerDifficulty();
        if (session.getCompletionsInDifficulty() >= needed) {
            // Progression: next available or continue hardest
            Difficulty current = session.getDifficulty();
            Difficulty next = libraryIndex.nextAvailableAfter(current);
            if (next == null) {
                Difficulty hardest = libraryIndex.hardestAvailableDifficulty();
                if (hardest == null) {
                    next = current.nextOrNull();
                } else {
                    next = hardest;
                }
            }
            if (next != null && next != current) {
                session.setDifficulty(next);
                player.sendMessage(ChatColor.GREEN + "Promoted to difficulty " + next.getDisplayName());
            } else {
                player.sendMessage(ChatColor.GOLD + "Continuing at difficulty " + current.getDisplayName());
            }
            // Teleport to safety before clearing/rebuilding lane to avoid falling
            teleportToDifficultyStart(player, session);
            jumpLoader.resetDifficulty(config.getGameWorld(), session);
        }
    }

    public void handlePlayerFall(Player player) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;
        session.addStrike();
        if (session.getStrikesInDifficulty() == 1) {
            // First fall: reset to start of current difficulty
            player.sendMessage(ChatColor.RED + "You fell! Resetting this difficulty.");
            jumpLoader.resetDifficulty(config.getGameWorld(), session);
            // Teleport to the start of the current difficulty
            teleportToDifficultyStart(player, session);
        } else {
            // Second fall: reset everything
            session.resetStrikes();
            Difficulty prev = Difficulty.I; // reset to base
            session.setDifficulty(prev);
            player.sendMessage(ChatColor.DARK_RED + "Second fall! Restarting from the beginning.");
            jumpLoader.resetAll(config.getGameWorld(), session);
            teleportToDifficultyStart(player, session);
        }
    }

    private void teleportToDifficultyStart(Player player, PlayerSession session) {
        World world = config.getGameWorld();
        if (world == null) return;
        Location origin = laneManager.getLaneOrigin(world, session.getLaneIndex());
        player.teleport(origin.clone().add(0.5, 0, 0.5));
    }

    public void setPlayerDifficulty(Player player, Difficulty difficulty) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.RED + "Join the event first.");
            return;
        }
        session.setDifficulty(difficulty);
        player.sendMessage(ChatColor.AQUA + "Difficulty set to " + difficulty.getDisplayName());
        jumpLoader.resetDifficulty(config.getGameWorld(), session);
        teleportToDifficultyStart(player, session);
    }

    public void handlePlayerSteppedOnStart(Player player, Location startBlock) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;
        jumpLoader.onSteppedOnStart(config.getGameWorld(), session, startBlock);
    }
}


