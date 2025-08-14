package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.TheJumper;
import com.ariesninja.theJumper.config.GameConfig;
import com.ariesninja.theJumper.library.Difficulty;
import com.ariesninja.theJumper.library.LibraryIndex;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Sound;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.io.File;

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
    private Scoreboard scoreboard;
    private Objective scoreboardObjective;
    private final java.util.Set<Long> firedTimeAlerts = new java.util.HashSet<>();
    private final java.util.List<BukkitTask> clearingTasks = new java.util.ArrayList<>();
    private boolean resettingWorld = false;
    private BukkitTask resetNotifyTask;
    private final java.util.Map<Difficulty, Long> bestTimeByDifficultyMs = new java.util.EnumMap<>(Difficulty.class);
    private final java.util.Map<Difficulty, java.util.UUID> bestTimeHolderByDifficulty = new java.util.EnumMap<>(Difficulty.class);
    private BukkitTask leaderTask;
    private final java.util.LinkedHashSet<org.bukkit.Location> globalPlacedBlocks = new java.util.LinkedHashSet<>();

    public GameManager(TheJumper plugin) {
        this.plugin = plugin;
        this.config = plugin.getGameConfig();
        this.laneManager = new LaneManager(config);
        this.sessionManager = new PlayerSessionManager();
        this.libraryIndex = new LibraryIndex(config);
        this.jumpLoader = new JumpLoader(config);
        this.jumpLoader.setLibraryIndex(libraryIndex);
        this.jumpLoader.setGlobalPlacedBlockTracker(globalPlacedBlocks::add);
    }

    public boolean isGameActive() {
        return active;
    }

    public PlayerSessionManager getSessionManager() {
        return sessionManager;
    }

    public void startGame() {
        if (resettingWorld) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.RED + "Cannot start: world is resetting.");
            return;
        }
        if (active) return;
        World world = config.getGameWorld();
        if (world == null) {
            plugin.getLogger().severe("Game world '" + config.getGameWorldName() + "' is not loaded.");
            return;
        }
        // No async clear on start; admins should run /jumper reset beforehand
        firedTimeAlerts.clear();
        bestTimeByDifficultyMs.clear();
        bestTimeHolderByDifficulty.clear();
        this.active = true;
        this.startEpochSeconds = System.currentTimeMillis() / 1000L;
        Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.GREEN + "Event started!");
        // Clickable join broadcast
        net.md_5.bungee.api.chat.TextComponent joinBtn = new net.md_5.bungee.api.chat.TextComponent(ChatColor.AQUA + "[Click to Join]");
        joinBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/jumper join"));
        joinBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder(ChatColor.GRAY + "Join the event now").create()));
        for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.spigot().sendMessage(joinBtn);
        }
        // Initialize scoreboard
        initializeScoreboard();
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
                long remaining = Math.max(0, (startEpochSeconds + config.getTimeLimitSeconds()) - (System.currentTimeMillis() / 1000L));
                handleTimeAlerts(remaining);
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
                updateLeaderboardScoreboard();
                for (PlayerSession s : sessionManager.getAllSessions()) {
                    Player p = Bukkit.getPlayer(s.getPlayerId());
                    if (p == null) continue;
                    boolean levelOne = s.getDifficulty() == Difficulty.I;
                    String status = (levelOne || s.getStrikesInDifficulty() == 0) ? ChatColor.GREEN + "SAFE" : ChatColor.RED + "DANGER";
                    ChatColor lvlColor = config.getChatColorFor(s.getDifficulty());
                    String msg = lvlColor + "Lvl " + s.getDifficulty().getDisplayName() + ChatColor.DARK_GRAY + " | "
                            + ChatColor.YELLOW + Math.min(s.getCompletionsInDifficulty() + 1, config.getCompletionsPerDifficulty()) + "/" + config.getCompletionsPerDifficulty() + ChatColor.DARK_GRAY + " | "
                            + ChatColor.GOLD + "Score: " + s.getTotalScore() + ChatColor.DARK_GRAY + " | "
                            + status + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + time;
                    p.spigot().sendMessage(
                            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(msg)
                    );

                    // Ambient particles above each queued end block to visually mark targets
                    for (int i = 0; i < s.getQueuedEndBlocks().size(); i++) {
                        Location target = s.getQueuedEndBlocks().get(i);
                        org.bukkit.World w = target.getWorld();
                        if (w == null) w = config.getGameWorld();
                        if (w != null) {
                            boolean isFinalTarget = (i == s.getQueuedEndBlocks().size() - 1)
                                    && (s.getCompletionsInDifficulty() + i + 1 >= config.getCompletionsPerDifficulty());
                            if (isFinalTarget) {
                                // Pre-landing final marker: colored dust matching next level, plus subtle enchant swirl
                                com.ariesninja.theJumper.library.Difficulty next = resolveNextLevelForColor(s.getDifficulty());
                                org.bukkit.Color color = config.getBukkitColorFor(next != null ? next : s.getDifficulty());
                                org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1.3F);
                                w.spawnParticle(org.bukkit.Particle.REDSTONE,
                                        target.getBlockX() + 0.5,
                                        target.getBlockY() + 1.2,
                                        target.getBlockZ() + 0.5,
                                        30, 0.35, 0.2, 0.35, 0.0, dust);
                                w.spawnParticle(org.bukkit.Particle.ENCHANTMENT_TABLE,
                                        target.getBlockX() + 0.5,
                                        target.getBlockY() + 1.4,
                                        target.getBlockZ() + 0.5,
                                        16, 0.4, 0.3, 0.4, 0.0);
                            } else {
                                w.spawnParticle(
                                        org.bukkit.Particle.END_ROD,
                                        target.getBlockX() + 0.5,
                                        target.getBlockY() + 1.2,
                                        target.getBlockZ() + 0.5,
                                        4, 0.15, 0.1, 0.15, 0.01
                                );
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        // Start periodic leader broadcast once per minute
        if (leaderTask != null) leaderTask.cancel();
        leaderTask = new BukkitRunnable() {
            @Override
            public void run() {
                PlayerSession leader = sessionManager.getAllSessions().stream()
                        .max(java.util.Comparator.comparingInt(PlayerSession::getBestScore))
                        .orElse(null);
                if (leader != null) {
                    Player lp = Bukkit.getPlayer(leader.getPlayerId());
                    String name = lp != null ? lp.getName() : (leader.getLastKnownName() != null ? leader.getLastKnownName() : leader.getPlayerId().toString().substring(0, 6));
                    Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.GOLD + "Current leader: " + ChatColor.YELLOW + name + ChatColor.DARK_GRAY + " (" + ChatColor.RED + leader.getBestScore() + ChatColor.DARK_GRAY + ")");
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60);
    }

    public void stopGame() {
        if (!active) return;
        // Snapshot top 10 before clearing sessions
        java.util.List<PlayerSession> snapshot = new java.util.ArrayList<>(sessionManager.getAllSessions());
        snapshot.sort((a, b) -> Integer.compare(b.getBestScore(), a.getBestScore()));
        java.util.List<String> top10Lines = new java.util.ArrayList<>();
        int rnk = 1;
        for (PlayerSession s : snapshot) {
            if (rnk > 10) break;
            Player p = Bukkit.getPlayer(s.getPlayerId());
            String name = p != null ? p.getName() : (s.getLastKnownName() != null ? s.getLastKnownName() : s.getPlayerId().toString().substring(0, 6));
            ChatColor color = (rnk == 1) ? ChatColor.YELLOW : (rnk == 2) ? ChatColor.WHITE : (rnk == 3) ? ChatColor.GOLD : ChatColor.GRAY;
            String line = color + "#" + rnk + ChatColor.DARK_GRAY + " | " + ChatColor.RESET + name + ChatColor.DARK_GRAY + " (" + ChatColor.RED + s.getBestScore() + ChatColor.DARK_GRAY + ")";
            top10Lines.add(line);
            rnk++;
        }
        this.active = false;
        Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.RED + "Event ended.");
        if (!top10Lines.isEmpty()) {
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.AQUA + "Top 10");
            for (String ln : top10Lines) Bukkit.broadcastMessage(ln);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
            // Send to spawn via console command
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + online.getName());
        }
        // Cleanup sessions
        sessionManager.clear();
        // Cancel any ongoing clearing tasks
        cancelClearingTasks();
        if (scoreboard != null) {
            Scoreboard main = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (main != null) online.setScoreboard(main);
            }
            scoreboard = null;
            scoreboardObjective = null;
        }
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (hudTask != null) {
            hudTask.cancel();
            hudTask = null;
        }
        if (leaderTask != null) {
            leaderTask.cancel();
            leaderTask = null;
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
            existing.setActiveInEvent(true);
            existing.setLastKnownName(player.getName());
            // If we have a lastKnownLocation within the game world, return them there
            Location returnTo = existing.getLastKnownLocation();
            if (returnTo != null && returnTo.getWorld() != null && returnTo.getWorld().equals(config.getGameWorld())) {
                player.teleport(returnTo);
                player.sendMessage(ChatColor.YELLOW + "Returned you to your previous spot in The Jumper.");
            } else {
                initializePlayerInLane(player, existing);
                player.sendMessage(ChatColor.YELLOW + "Returned you to your lane in The Jumper.");
            }
        }
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        // Blindness/slow on join similar to level-up transition
        applyFreezeAndBlind(player, 30);
        scheduleUnfreezeAndUnblind(player, 30);
    }

    public void leave(Player player) {
        UUID id = player.getUniqueId();
        // Keep session so best score remains on leaderboard
        // Keep lane allocation so they can return to the same lane
        PlayerSession s = sessionManager.getSession(id);
        if (s != null) {
            s.setActiveInEvent(false);
            s.setLastKnownName(player.getName());
            s.setLastKnownLocation(player.getLocation());
        }
        player.sendMessage(ChatColor.YELLOW + "You left The Jumper event.");
        // Send to lobby/spawn
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + player.getName());
    }

    public void handlePlayerQuitKeepScore(Player player) {
        UUID id = player.getUniqueId();
        // Keep lane allocation on quit so rejoining preserves lane/position
        player.sendMessage(ChatColor.YELLOW + "[Jumper] Your score will remain on the board.");
        // Also inform they will be returned
        player.sendMessage(ChatColor.YELLOW + "We'll return you to your spot when you rejoin.");
    }

    private void initializePlayerInLane(Player player, PlayerSession session) {
        World world = config.getGameWorld();
        if (world == null) return;
        Location origin = laneManager.getLaneOrigin(world, session.getLaneIndex());
        jumpLoader.initializeLane(world, session);
        // Prepare player state
        player.setGameMode(GameMode.SURVIVAL);
        // Clear inventory and armor
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        // Clear active effects
        for (org.bukkit.potion.PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        Location tp = origin.clone().add(0.5, 0, 0.5);
        tp.setYaw(-90f);
        tp.setPitch(0f);
        player.teleport(tp);
        player.sendMessage(ChatColor.AQUA + "Starting at level " + session.getDifficulty().getDisplayName());
    }

    public void handlePlayerReachedEnd(Player player, Location endBlock) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;
        // Burst effect on completion (grand if final step of difficulty)
        boolean isFinal = session.getCompletionsInDifficulty() + 1 >= config.getCompletionsPerDifficulty();
        if (isFinal) {
            // Bigger white/gray landing burst plus a ring of white dust
            player.getWorld().spawnParticle(org.bukkit.Particle.FLASH, endBlock.getBlockX() + 0.5, endBlock.getBlockY() + 1.4, endBlock.getBlockZ() + 0.5,
                    4, 0, 0, 0, 0.0);
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, endBlock.getBlockX() + 0.5, endBlock.getBlockY() + 1.35, endBlock.getBlockZ() + 0.5,
                    120, 0.9, 0.7, 0.9, 0.03);
            org.bukkit.Particle.DustOptions white = new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(240,240,240), 1.0F);
            player.getWorld().spawnParticle(org.bukkit.Particle.REDSTONE,
                    endBlock.getBlockX() + 0.5, endBlock.getBlockY() + 1.25, endBlock.getBlockZ() + 0.5,
                    60, 0.8, 0.1, 0.8, 0.0, white);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
        } else {
            // More grand white/gray non-colored landing effect
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD,
                    endBlock.getBlockX() + 0.5, endBlock.getBlockY() + 1.1, endBlock.getBlockZ() + 0.5,
                    20, 0.3, 0.2, 0.3, 0.01);
            player.getWorld().spawnParticle(org.bukkit.Particle.FIREWORKS_SPARK,
                    endBlock.getBlockX() + 0.5, endBlock.getBlockY() + 1.2, endBlock.getBlockZ() + 0.5,
                    40, 0.5, 0.3, 0.5, 0.03);
            player.playSound(player.getLocation(), Sound.UI_LOOM_TAKE_RESULT, 0.8f, 1.2f);
        }
        session.incrementCompletions();
        int stage = Math.min(session.getCompletionsInDifficulty(), config.getCompletionsPerDifficulty());
        float pitch = Math.min(2.0f, 0.5f + (stage - 1) * 0.075f);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.0f, pitch);
        session.incrementTotalScore();
        session.recordEndForCurrentDifficulty(endBlock);
        jumpLoader.onJumpCompleted(config.getGameWorld(), session, endBlock);

        int needed = config.getCompletionsPerDifficulty();
        if (session.getCompletionsInDifficulty() >= needed) {
            // Time record tracking and special announcements
            long elapsedMs = System.currentTimeMillis() - session.getDifficultyStartEpochMs();
            Difficulty current = session.getDifficulty();
            Long best = bestTimeByDifficultyMs.get(current);
            boolean isNewRecord = (best == null) || (elapsedMs < best);
            if (isNewRecord) {
                bestTimeByDifficultyMs.put(current, elapsedMs);
                bestTimeHolderByDifficulty.put(current, player.getUniqueId());
                Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.GREEN + player.getName() + ChatColor.GRAY + " set a new record on level " + config.getChatColorFor(current) + current.getDisplayName() + ChatColor.GRAY + ": " + ChatColor.AQUA + formatMillis(elapsedMs));
            }
            if (current.getIndex() >= 7) {
                boolean firstTry = session.getStrikesInDifficulty() == 0;
                String emph = firstTry ? (ChatColor.GOLD + " FIRST TRY!") : "";
                Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.LIGHT_PURPLE + player.getName() + ChatColor.GRAY + " completed level " + config.getChatColorFor(current) + current.getDisplayName() + ChatColor.GRAY + "! " + emph);
            }
            // Progression: next available or continue hardest
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
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                player.sendMessage(ChatColor.GOLD + "Continuing at difficulty " + current.getDisplayName());
            }
            // Blind/freeze with animated title, teleport to platform, rebuild, then unfreeze after 0.5s
            // ~1.5s blind/freeze
            applyFreezeAndBlind(player, 30);
            playLevelTransitionTitle(player, current, session.getDifficulty());
            teleportToDifficultyStart(player, session);
            jumpLoader.resetDifficulty(config.getGameWorld(), session);
            scheduleUnfreezeAndUnblind(player, 30);
        }
    }

    public void handlePlayerFall(Player player) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;
        session.addStrike();
        // If first fall or on first level
        if (session.getStrikesInDifficulty() == 1 || session.getDifficulty() == Difficulty.I) {
            // First fall: reset to start of current difficulty

            player.sendMessage(ChatColor.RED + "You fell! Resetting this difficulty.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.0f);
            session.resetCompletions();
            session.resetTotalScoreToDifficultyStart();
            session.markDifficultyRestartedNow();
            jumpLoader.resetDifficulty(config.getGameWorld(), session);
            // Teleport to the start of the current difficulty
            teleportToDifficultyStart(player, session);
        } else {
            // Second fall: reset everything
            session.resetStrikes();
            Difficulty prev = Difficulty.I; // reset to base
            Difficulty current = session.getDifficulty();
            session.setDifficulty(prev);
            // Use a strong but shorter reset-death sound
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5f, 1.0f);
            session.resetAllScore();
            player.sendMessage(ChatColor.DARK_RED + "Second fall! Restarting from the beginning.");
            // Blindness/slow effect and death transition animation
            applyFreezeAndBlind(player, 30);
            playDeathTransitionTitle(player, current, prev);
            teleportToDifficultyStart(player, session);
            jumpLoader.resetAll(config.getGameWorld(), session);
            scheduleUnfreezeAndUnblind(player, 30);
        }
    }

    private void teleportToDifficultyStart(Player player, PlayerSession session) {
        World world = config.getGameWorld();
        if (world == null) return;
        Location origin = laneManager.getLaneOrigin(world, session.getLaneIndex());
        Location tp = origin.clone().add(0.5, 0, 0.5);
        tp.setYaw(-90f);
        tp.setPitch(0f);
        player.teleport(tp);
    }

    public void setPlayerDifficulty(Player player, Difficulty difficulty) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) {
            player.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.RED + "Join the event first.");
            return;
        }
        session.setDifficulty(difficulty);
        player.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.AQUA + "Difficulty set to " + difficulty.getDisplayName());
        jumpLoader.resetDifficulty(config.getGameWorld(), session);
        teleportToDifficultyStart(player, session);
    }

    public void handlePlayerSteppedOnStart(Player player, Location startBlock) {
        PlayerSession session = sessionManager.getSession(player.getUniqueId());
        if (session == null) return;
        jumpLoader.onSteppedOnStart(config.getGameWorld(), session, startBlock);
    }

    private void applyFreezeAndBlind(Player player, int ticks) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ticks, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 10, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 200, false, false, false));
    }

    private void scheduleUnfreezeAndUnblind(Player player, int ticks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.SLOW);
                player.removePotionEffect(PotionEffectType.JUMP);
            }
        }.runTaskLater(plugin, ticks);
    }

    // Animated title during blind window: arrows flipping between prev/next colors
    private void playLevelTransitionTitle(Player player, Difficulty from, Difficulty to) {
        org.bukkit.Color fromColor = config.getBukkitColorFor(from);
        org.bukkit.Color toColor = config.getBukkitColorFor(to);
        ChatColor fromChat = config.getChatColorFor(from);
        ChatColor toChat = config.getChatColorFor(to);
        final int frames = 12; // ~1.2s at 2 ticks per frame
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= frames) { cancel(); return; }
                boolean flip = (i % 2 == 0);
                ChatColor left = flip ? fromChat : toChat;
                ChatColor right = flip ? toChat : fromChat;
                String arrows = left + "<<<" + ChatColor.GRAY + " Lvl " + ChatColor.WHITE + to.getDisplayName() + right + " >>>";
                String sub = ChatColor.DARK_GRAY + "Level Up!";
                player.sendTitle(arrows, sub, 0, 10, 0);
                i++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void initializeScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        scoreboard = manager.getNewScoreboard();
        String bar = ChatColor.DARK_GRAY + "--------------------"; // 20 dashes
        scoreboardObjective = scoreboard.registerNewObjective("jumper", "dummy", ChatColor.AQUA + "Top Scores");
        scoreboardObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(scoreboard);
        }
    }

    private void updateLeaderboardScoreboard() {
        if (scoreboard == null || scoreboardObjective == null) return;
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
        java.util.List<PlayerSession> list = new java.util.ArrayList<>(sessionManager.getAllSessions());
        list.sort((a, b) -> Integer.compare(b.getBestScore(), a.getBestScore()));
        int rank = 1;
        int online = Bukkit.getOnlinePlayers().size();
        long now = System.currentTimeMillis() / 1000L;
        long remaining = Math.max(0, (startEpochSeconds + config.getTimeLimitSeconds()) - now);
        for (PlayerSession s : list) {
            if (rank > 10) break;
            Player p = Bukkit.getPlayer(s.getPlayerId());
            String name = p != null ? p.getName() : (s.getLastKnownName() != null ? s.getLastKnownName() : s.getPlayerId().toString().substring(0, 6));
            ChatColor color = (rank == 1) ? ChatColor.YELLOW /*gold*/ : (rank == 2) ? ChatColor.WHITE /*silver*/ : (rank == 3) ? ChatColor.GOLD /*bronze &6*/ : ChatColor.GRAY /*dull*/;
            String entry = color + "#" + rank + ChatColor.DARK_GRAY + " | " + ChatColor.RESET + name + ChatColor.DARK_GRAY + " (" + ChatColor.RED + s.getBestScore() + ChatColor.DARK_GRAY + ")";
            scoreboardObjective.getScore(entry).setScore(0);
            rank++;
        }
        // Footer
        String footer1 = ChatColor.DARK_GRAY + "--------------------";
        String footer2 = ChatColor.GRAY + "Time: " + ChatColor.WHITE + formatTime(remaining);
        String footer3 = ChatColor.GRAY + "Playing: " + ChatColor.WHITE + online;
        String footer4 = ChatColor.BLUE + "goga.events";
        scoreboardObjective.getScore(footer1).setScore(-1);
        scoreboardObjective.getScore(footer2).setScore(-2);
        scoreboardObjective.getScore(footer3).setScore(-3);
        scoreboardObjective.getScore(footer4).setScore(-4);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setScoreboard(scoreboard);
        }
    }

    private void clearEventAreaAsync(World world) {
        final int startX = config.getLaneStartX();
        final int baseY = config.getLaneBaseY();
        final int spacingZ = config.getLaneZSpacing();
        final int y1 = baseY - 32;
        final int y2 = baseY + 96;
        final int x1 = startX - 2;
        final int x2 = startX + 1024; // reasonable forward range
        final int z1 = -spacingZ * 20; // 40 lanes span
        final int z2 = spacingZ * 20;

        // Build fine-grained boxes (16x16x16 bands) to throttle block updates per tick
        final java.util.ArrayDeque<int[]> boxes = new java.util.ArrayDeque<>();
        int minChunkX = x1 >> 4;
        int maxChunkX = x2 >> 4;
        int minChunkZ = z1 >> 4;
        int maxChunkZ = z2 >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                int bx1 = cx << 4;
                int bz1 = cz << 4;
                int bx2 = bx1 + 15;
                int bz2 = bz1 + 15;
                int ix1 = Math.max(bx1, x1);
                int ix2 = Math.min(bx2, x2);
                int iz1 = Math.max(bz1, z1);
                int iz2 = Math.min(bz2, z2);
                if (ix1 <= ix2 && iz1 <= iz2) {
                    for (int by = y1; by <= y2; by += 16) {
                        int iy1 = by;
                        int iy2 = Math.min(by + 15, y2);
                        boxes.add(new int[]{ix1, iy1, iz1, ix2, iy2, iz2});
                    }
                }
            }
        }

        // Process a few boxes per tick
        final int boxesPerTick = 3; // conservative to protect TPS
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (boxes.isEmpty()) {
                    cancel();
                    clearingTasks.remove(this);
                    return;
                }
                for (int n = 0; n < boxesPerTick && !boxes.isEmpty(); n++) {
                    int[] b = boxes.pollFirst();
                    if (b == null) break;
                    com.ariesninja.theJumper.util.BlockUtils.fill(world, b[0], b[1], b[2], b[3], b[4], b[5], org.bukkit.Material.AIR);
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
        clearingTasks.add(task);
    }

    // Expose reset for admin command
    public void resetEventAreaAsync(CommandSender sender) {
        if (resettingWorld) {
            sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.RED + "Reset already in progress.");
            return;
        }
        resettingWorld = true;
        World world = config.getGameWorld();
        if (world == null) {
            sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.RED + "Game world not loaded.");
            resettingWorld = false;
            return;
        }
        // Stop active timers/HUD without clearing sessions yet
        if (timerTask != null) { timerTask.cancel(); timerTask = null; }
        if (hudTask != null) { hudTask.cancel(); hudTask = null; }
        if (leaderTask != null) { leaderTask.cancel(); leaderTask = null; }
        if (active) {
            active = false;
        }
        // Send all players to lobby/spawn
        for (Player p : Bukkit.getOnlinePlayers()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spawn " + p.getName());
        }

        // Aggregate all placed blocks across sessions plus global tracker
        java.util.LinkedHashSet<org.bukkit.Location> toClear = new java.util.LinkedHashSet<>(globalPlacedBlocks);
        for (PlayerSession s : sessionManager.getAllSessions()) toClear.addAll(s.getPlacedBlocks());

        // Clear scoreboard back to main for all players
        if (scoreboard != null) {
            Scoreboard main = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (main != null) online.setScoreboard(main);
            }
            scoreboard = null;
            scoreboardObjective = null;
        }

        // Process block removals in batches to protect TPS
        final java.util.ArrayDeque<org.bukkit.Location> queue = new java.util.ArrayDeque<>(toClear);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                int perTick = 4000; // fairly aggressive but bounded
                int removed = 0;
                while (removed < perTick && !queue.isEmpty()) {
                    org.bukkit.Location loc = queue.pollFirst();
                    if (loc != null && loc.getWorld() != null) {
                        loc.getWorld().getBlockAt(loc).setType(org.bukkit.Material.AIR, false);
                    }
                    removed++;
                }
                if (queue.isEmpty()) {
                    cancel();
                    clearingTasks.remove(this);
                    // Clear per-session tracking and queues
                    for (PlayerSession s : sessionManager.getAllSessions()) {
                        s.clearPlacedBlocks();
                        s.getQueuedEndBlocks().clear();
                        s.getQueuedStartBlocks().clear();
                        s.setPendingEndRemoval(null);
                    }
                    // Finally clear sessions entirely and global tracker
                    sessionManager.clear();
                    globalPlacedBlocks.clear();
                    resettingWorld = false;
                    sender.sendMessage(ChatColor.DARK_AQUA + "[ADMIN] " + ChatColor.GREEN + "Reset complete. You can /jumper start now.");
                }
            }
        }.runTaskTimer(plugin, 1L, 2L);
        clearingTasks.add(task);
    }

    private void cancelClearingTasks() {
        for (BukkitTask t : new java.util.ArrayList<>(clearingTasks)) {
            if (t != null && !t.isCancelled()) t.cancel();
        }
        clearingTasks.clear();
    }

    // No deleter needed; worlds are created with new unique names

    private void handleTimeAlerts(long remainingSeconds) {
        long[] checkpoints = new long[]{1800, 900, 600, 300, 120, 60, 30};
        for (long cp : checkpoints) {
            if (remainingSeconds == cp && !firedTimeAlerts.contains(cp)) {
                firedTimeAlerts.add(cp);
                String label;
                if (cp == 1800) label = "30 Minutes Remaining!";
                else if (cp == 900) label = "15 Minutes Remaining!";
                else if (cp == 600) label = "10 Minutes Remaining!";
                else if (cp == 300) label = "5 Minutes Remaining!";
                else if (cp == 120) label = "2 Minutes Remaining!";
                else if (cp == 60) label = "1 Minute Remaining!";
                else label = "30 Seconds Remaining!";
                Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.LIGHT_PURPLE + label);
                Sound sound = (cp == 30) ? Sound.ENTITY_TNT_PRIMED : Sound.ENTITY_GHAST_SCREAM;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                }
            }
        }
        if (remainingSeconds <= 10 && remainingSeconds >= 1 && !firedTimeAlerts.contains(remainingSeconds)) {
            firedTimeAlerts.add(remainingSeconds);
            int sec = (int) remainingSeconds;
            Bukkit.broadcastMessage(ChatColor.DARK_AQUA + "[Jumper] " + ChatColor.YELLOW + "Ending in " + ChatColor.GOLD + sec + ChatColor.YELLOW + "...");
            // Use a different instrument than jump progression: chime instead of bit
            float pitch = Math.min(2.0f, 0.5f + (10 - sec) * 0.15f);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, pitch);
            }
        }
    }

    private String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private String formatMillis(long millis) {
        long seconds = millis / 1000;
        long ms = millis % 1000;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        return String.format("%02d:%02d.%03d", m, s, ms);
    }

    // Determine the next level to use for pre-landing color hints
    private com.ariesninja.theJumper.library.Difficulty resolveNextLevelForColor(com.ariesninja.theJumper.library.Difficulty current) {
        com.ariesninja.theJumper.library.Difficulty next = libraryIndex.nextAvailableAfter(current);
        if (next == null) {
            com.ariesninja.theJumper.library.Difficulty hardest = libraryIndex.hardestAvailableDifficulty();
            if (hardest == null) {
                next = current.nextOrNull();
            } else {
                next = hardest;
            }
        }
        return next != null ? next : current;
    }

    // Animated title for death reset: same arrow flip style, different subtitle
    private void playDeathTransitionTitle(Player player, Difficulty from, Difficulty to) {
        org.bukkit.Color fromColor = config.getBukkitColorFor(from);
        org.bukkit.Color toColor = config.getBukkitColorFor(to);
        ChatColor fromChat = config.getChatColorFor(from);
        ChatColor toChat = config.getChatColorFor(to);
        final int frames = 12; // ~1.2s at 2 ticks per frame
        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (i >= frames) { cancel(); return; }
                boolean flip = (i % 2 == 0);
                ChatColor left = flip ? fromChat : toChat;
                ChatColor right = flip ? toChat : fromChat;
                String arrows = left + "<<<" + ChatColor.GRAY + " Lvl " + ChatColor.WHITE + to.getDisplayName() + right + " >>>";
                String sub = ChatColor.DARK_RED + "Death Reset";
                player.sendTitle(arrows, sub, 0, 10, 0);
                i++;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}


