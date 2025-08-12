package com.ariesninja.theJumper.game;

import com.ariesninja.theJumper.config.GameConfig;
import com.ariesninja.theJumper.library.JumpRegion;
import com.ariesninja.theJumper.library.LibraryIndex;
import com.ariesninja.theJumper.util.BlockUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class JumpLoader {

    private final GameConfig config;
    private LibraryIndex libraryIndex;
    private java.util.function.Consumer<org.bukkit.Location> globalPlacedBlockTracker;

    public JumpLoader(GameConfig config) {
        this.config = config;
    }

    public void setLibraryIndex(LibraryIndex libraryIndex) {
        this.libraryIndex = libraryIndex;
    }

    public void setGlobalPlacedBlockTracker(java.util.function.Consumer<org.bukkit.Location> tracker) {
        this.globalPlacedBlockTracker = tracker;
    }

    private void trackGlobal(org.bukkit.Location loc) {
        if (loc != null && globalPlacedBlockTracker != null) {
            globalPlacedBlockTracker.accept(loc.clone());
        }
    }

    public void initializeLane(World world, PlayerSession session) {
        // Build start platform
        Location origin = getStartPlatformOrigin(world, session);
        int size = config.getStartPlatformSize();
        int half = size / 2;
        Material platformMat = config.getStartPlatformMaterial();
        BlockUtils.fill(world,
                origin.getBlockX() - half, origin.getBlockY() - 1, origin.getBlockZ() - half,
                origin.getBlockX() + half, origin.getBlockY() - 1, origin.getBlockZ() + half,
                platformMat);
        // Track platform blocks for later global reset
        for (int x = origin.getBlockX() - half; x <= origin.getBlockX() + half; x++) {
            for (int z = origin.getBlockZ() - half; z <= origin.getBlockZ() + half; z++) {
                org.bukkit.Location plat = new org.bukkit.Location(world, x, origin.getBlockY() - 1, z);
                session.addPlacedBlock(plat);
                trackGlobal(plat);
            }
        }
        
        // Reset queues and pending removal
        session.getQueuedEndBlocks().clear();
        session.getQueuedStartBlocks().clear();
        session.setPendingEndRemoval(null);

        // Initialize first start block just ahead of platform (same y as platform top), shifted +4
        Location initialStart = new Location(world, origin.getBlockX() + 4, origin.getBlockY() - 1, origin.getBlockZ());
        world.getBlockAt(initialStart).setType(config.getBlockFor(session.getDifficulty()), false);
        session.setNextStartAt(initialStart);
        session.addPlacedBlock(initialStart);
        trackGlobal(initialStart);
        session.getQueuedStartBlocks().add(initialStart.clone());
        session.updateMaxGeneratedX(initialStart.getBlockX());

        // Place initial chain
        loadNextJumpChain(world, session, config.getPreloadedJumps());
    }

    public void onJumpCompleted(World world, PlayerSession session, Location endTop) {
        // Do not remove the end block yet to avoid player falling; defer until stepping on next start
        if (!session.getQueuedEndBlocks().isEmpty()) {
            // Remove the first target from detection queue (avoid duplicate triggers)
            session.getQueuedEndBlocks().remove(0);
        }
        // No pending removal
        session.setPendingEndRemoval(null);
        // Append next jump to keep pipeline length unless we're at cap
        int needed = config.getCompletionsPerDifficulty();
        int remaining = needed - session.getCompletionsInDifficulty();
        if (session.getCompletionsInDifficulty() < needed && session.getQueuedEndBlocks().size() < remaining) {
            loadNextJumpChain(world, session, 1);
        }

        // Ensure we never display future ends/starts beyond the cap: trim extras from the back
        while (session.getQueuedEndBlocks().size() > remaining) {
            Location extra = session.getQueuedEndBlocks().remove(session.getQueuedEndBlocks().size() - 1);
            world.getBlockAt(extra).setType(Material.AIR, false);
            // also remove from placed set
            session.getPlacedBlocks().removeIf(loc -> loc.getBlockX() == extra.getBlockX() && loc.getBlockY() == extra.getBlockY() && loc.getBlockZ() == extra.getBlockZ());
        }
        while (session.getQueuedStartBlocks().size() > remaining) {
            Location extra = session.getQueuedStartBlocks().remove(session.getQueuedStartBlocks().size() - 1);
            world.getBlockAt(extra).setType(Material.AIR, false);
            session.getPlacedBlocks().removeIf(loc -> loc.getBlockX() == extra.getBlockX() && loc.getBlockY() == extra.getBlockY() && loc.getBlockZ() == extra.getBlockZ());
        }
    }

    public void resetDifficulty(World world, PlayerSession session) {
        // Precisely clear recorded placed blocks for this lane
        for (Location loc : new java.util.ArrayList<>(session.getPlacedBlocks())) {
            world.getBlockAt(loc).setType(Material.AIR, false);
        }
        session.clearPlacedBlocks();
        // Reset queues
        session.getQueuedEndBlocks().clear();
        session.getQueuedStartBlocks().clear();
        session.setPendingEndRemoval(null);
        // Rebuild lane afresh
        initializeLane(world, session);
    }

    public void resetAll(World world, PlayerSession session) {
        // Reuse difficulty reset logic
        resetDifficulty(world, session);
    }

    private Location getStartPlatformOrigin(World world, PlayerSession session) {
        int x = config.getLaneStartX();
        int y = config.getLaneBaseY();
        int z = session.getLaneIndex() * config.getLaneZSpacing();
        return new Location(world, x, y, z);
    }

    private void loadNextJumpChain(World world, PlayerSession session, int howMany) {
        for (int i = 0; i < howMany; i++) {
            placeLinkedJump(world, session);
        }
    }

    private void placeLinkedJump(World world, PlayerSession session) {
        if (libraryIndex == null || !libraryIndex.hasRegions(session.getDifficulty())) {
            fallbackPlace(world, session);
            return;
        }
        Location endTop = pasteFromLibrary(world, session);
        if (endTop == null) {
            fallbackPlace(world, session);
            return;
        }
        linkWithGapAndNextStart(world, session, endTop);
    }

    private void fallbackPlace(World world, PlayerSession session) {
        // Simple 4-forward jump from nextStartAt
        Location start = session.getNextStartAt();
        if (start == null) {
            Location origin = getStartPlatformOrigin(world, session);
            start = new Location(world, origin.getBlockX() + 2, origin.getBlockY() - 1, origin.getBlockZ());
            session.setNextStartAt(start);
            world.getBlockAt(start).setType(config.getBlockFor(session.getDifficulty()), false);
        }
        int endX = start.getBlockX() + 4;
        int y = start.getBlockY();
        int z = start.getBlockZ();
        Location endTop = new Location(world, endX, y, z);
        world.getBlockAt(endTop).setType(config.getBlockFor(session.getDifficulty()), false);
        session.addPlacedBlock(endTop);
        trackGlobal(endTop);
        session.updateMaxGeneratedX(endTop.getBlockX());
        linkWithGapAndNextStart(world, session, endTop);
    }

    private void linkWithGapAndNextStart(World world, PlayerSession session, Location endTop) {
        // Register end target
        session.getQueuedEndBlocks().add(endTop.clone());
        // Also track this end block as placed so clear-on-fall works
        session.addPlacedBlock(endTop);
        trackGlobal(endTop);

        int y = endTop.getBlockY();
        int endZ = endTop.getBlockZ();
        int laneCenterZ = getStartPlatformOrigin(world, session).getBlockZ();

        int comps = session.getCompletionsInDifficulty();
        boolean doCenteringPath = comps > 0 && (comps % 4 == 0);

        if (!doCenteringPath) {
            // Normal: single 3-block gap then next start straight ahead, unless we're at cap
            int needed = config.getCompletionsPerDifficulty();
            if (session.getCompletionsInDifficulty() < needed) {
                int nextStartX = endTop.getBlockX() + 4; // 3 gap + 1 block position
                Location nextStart = new Location(world, nextStartX, y, endZ);
                world.getBlockAt(nextStart).setType(config.getBlockFor(session.getDifficulty()), false);
                session.addPlacedBlock(nextStart);
                trackGlobal(nextStart);
                session.setNextStartAt(nextStart);
                session.getQueuedStartBlocks().add(nextStart.clone());
                session.updateMaxGeneratedX(nextStartX);
            }
            return;
        }

        // Centering path: series of difficulty-colored blocks spaced by 3 forward, shifting 3 toward center each step until within 2 of center.
        Material mat = config.getBlockFor(session.getDifficulty());
        int x = endTop.getBlockX() + 3;
        int z = endZ;

        // First block straight ahead
        Location path = new Location(world, x, y, z);
        world.getBlockAt(path).setType(mat, false);
        session.addPlacedBlock(path);
        trackGlobal(path);
        session.updateMaxGeneratedX(x);

        // Continue stepping by 3 toward center until within 2 (inclusive requires another step to mimic example)
        while (Math.abs(z - laneCenterZ) >= 2) {
            if (z > laneCenterZ) z -= 3; else if (z < laneCenterZ) z += 3;
            x += 3;
            path = new Location(world, x, y, z);
            world.getBlockAt(path).setType(mat, false);
            session.addPlacedBlock(path);
            trackGlobal(path);
            session.updateMaxGeneratedX(x);
        }

        // Place the start block at the last path position (unless at cap)
        if (session.getCompletionsInDifficulty() < config.getCompletionsPerDifficulty()) {
            Location nextStart = new Location(world, x, y, z);
            world.getBlockAt(nextStart).setType(mat, false);
            session.addPlacedBlock(nextStart);
            trackGlobal(nextStart);
            session.setNextStartAt(nextStart);
            session.getQueuedStartBlocks().add(nextStart.clone());
        }
    }

    private Location pasteFromLibrary(World laneWorld, PlayerSession session) {
        World lib = config.getLibraryWorld();
        if (lib == null) return null;
        List<JumpRegion> regions = libraryIndex.getRegionsFor(session.getDifficulty());
        if (regions.isEmpty()) return null;
        JumpRegion region = regions.get(ThreadLocalRandom.current().nextInt(regions.size()));

        // Find markers in the library region
        Location startMarker = null;
        Location emeraldMarker = null;
        Location netheriteMarker = null;
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Material m = lib.getBlockAt(x, y, z).getType();
                    if (m == Material.REDSTONE_BLOCK) startMarker = new Location(lib, x, y, z);
                    else if (m == Material.EMERALD_BLOCK) emeraldMarker = new Location(lib, x, y, z);
                    else if (m == Material.NETHERITE_BLOCK) netheriteMarker = new Location(lib, x, y, z);
                }
            }
        }
        if (startMarker == null) return null;

        // Determine target alignment in lane
        Location startAt = session.getNextStartAt();
        if (startAt == null) {
            Location origin = getStartPlatformOrigin(laneWorld, session);
            startAt = new Location(laneWorld, origin.getBlockX() + 2, origin.getBlockY() - 1, origin.getBlockZ());
            session.setNextStartAt(startAt);
        }

        int dx = startAt.getBlockX() - startMarker.getBlockX();
        int dy = startAt.getBlockY() - startMarker.getBlockY();
        int dz = startAt.getBlockZ() - startMarker.getBlockZ();

        // Copy region blocks except markers and air, preserving block data
        int maxTargetX = Integer.MIN_VALUE;
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    org.bukkit.block.Block src = lib.getBlockAt(x, y, z);
                    Material m = src.getType();
                    if (m == Material.AIR || m == Material.REDSTONE_BLOCK || m == Material.EMERALD_BLOCK || m == Material.NETHERITE_BLOCK) {
                        continue;
                    }
                    int tx = x + dx;
                    int ty = y + dy;
                    int tz = z + dz;
                    org.bukkit.block.Block dst = laneWorld.getBlockAt(tx, ty, tz);
                    if (dst.getType() != m) {
                        dst.setType(m, false);
                    }
                    try {
                        org.bukkit.block.data.BlockData data = src.getBlockData().clone();
                        dst.setBlockData(data, false);
                    } catch (Throwable ignored) {}
                    try {
                        org.bukkit.block.BlockState srcState = src.getState();
                        org.bukkit.block.BlockState dstState = dst.getState();
                        if (srcState instanceof org.bukkit.block.Sign && dstState instanceof org.bukkit.block.Sign) {
                            org.bukkit.block.Sign sSrc = (org.bukkit.block.Sign) srcState;
                            org.bukkit.block.Sign sDst = (org.bukkit.block.Sign) dstState;
                            String[] lines = sSrc.getLines();
                            for (int i = 0; i < lines.length; i++) {
                                sDst.setLine(i, lines[i]);
                            }
                            sDst.update(true, false);
                        }
                    } catch (Throwable ignored) {}
                    org.bukkit.Location placed = new org.bukkit.Location(laneWorld, tx, ty, tz);
                    session.addPlacedBlock(placed);
                    trackGlobal(placed);
                    if (tx > maxTargetX) maxTargetX = tx;
                }
            }
        }

        // Place start marker replacement
        laneWorld.getBlockAt(startAt).setType(config.getBlockFor(session.getDifficulty()), false);
        session.addPlacedBlock(startAt);
        if (maxTargetX != Integer.MIN_VALUE) {
            session.updateMaxGeneratedX(maxTargetX);
        }

        // Determine end top lane position
        Location endTop;
        if (emeraldMarker != null) {
            endTop = new Location(laneWorld, emeraldMarker.getBlockX() + dx, emeraldMarker.getBlockY() + dy, emeraldMarker.getBlockZ() + dz);
            laneWorld.getBlockAt(endTop).setType(config.getBlockFor(session.getDifficulty()), false);
        } else if (netheriteMarker != null) {
            endTop = new Location(laneWorld, netheriteMarker.getBlockX() + dx, netheriteMarker.getBlockY() + dy + 1, netheriteMarker.getBlockZ() + dz);
        } else {
            // Fallback if no end markers: simple 4-forward
            endTop = new Location(laneWorld, startAt.getBlockX() + 4, startAt.getBlockY(), startAt.getBlockZ());
            laneWorld.getBlockAt(endTop).setType(config.getBlockFor(session.getDifficulty()), false);
        }

        session.updateMaxGeneratedX(endTop.getBlockX());

        return endTop;
    }

    public void onSteppedOnStart(World world, PlayerSession session, Location startBlock) {
        // Remove the matched start from queue
        session.getQueuedStartBlocks().removeIf(loc -> loc.getBlockX() == startBlock.getBlockX()
                && loc.getBlockY() == startBlock.getBlockY()
                && loc.getBlockZ() == startBlock.getBlockZ());

        // No-op: we keep previous end blocks for aesthetics
    }
}


