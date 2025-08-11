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

    public JumpLoader(GameConfig config) {
        this.config = config;
    }

    public void setLibraryIndex(LibraryIndex libraryIndex) {
        this.libraryIndex = libraryIndex;
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
        
        // Reset queues and pending removal
        session.getQueuedEndBlocks().clear();
        session.getQueuedStartBlocks().clear();
        session.setPendingEndRemoval(null);

        // Initialize first start block just ahead of platform (same y as platform top), shifted +4
        Location initialStart = new Location(world, origin.getBlockX() + 4, origin.getBlockY() - 1, origin.getBlockZ());
        world.getBlockAt(initialStart).setType(config.getBlockFor(session.getDifficulty()), false);
        session.setNextStartAt(initialStart);
        session.getQueuedStartBlocks().add(initialStart.clone());

        // Place initial chain
        loadNextJumpChain(world, session, config.getPreloadedJumps());
    }

    public void onJumpCompleted(World world, PlayerSession session, Location endTop) {
        // Do not remove the end block yet to avoid player falling; defer until stepping on next start
        if (!session.getQueuedEndBlocks().isEmpty()) {
            // Remove the first target from detection queue (avoid duplicate triggers)
            session.getQueuedEndBlocks().remove(0);
        }
        session.setPendingEndRemoval(endTop);
        // Append next jump to keep pipeline length
        loadNextJumpChain(world, session, 1);
    }

    public void resetDifficulty(World world, PlayerSession session) {
        // Clear everything ahead in a slice, then regenerate initial chain
        Location origin = getStartPlatformOrigin(world, session);
        int x1 = origin.getBlockX() - 2;
        int x2 = x1 + 200; // generous slice to include queued area beyond
        int y1 = origin.getBlockY() - 8;
        int y2 = origin.getBlockY() + 16;
        int z1 = origin.getBlockZ() - (config.getStartPlatformSize() + 4);
        int z2 = origin.getBlockZ() + (config.getStartPlatformSize() + 4);
        BlockUtils.fill(world, x1, y1, z1, x2, y2, z2, Material.AIR);
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
        linkWithDividerAndNextStart(world, session, endTop);
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
        linkWithDividerAndNextStart(world, session, endTop);
    }

    private void linkWithDividerAndNextStart(World world, PlayerSession session, Location endTop) {
        // Register end target
        session.getQueuedEndBlocks().add(endTop.clone());

        // Divider 2 blocks beyond end top at same y: 5 wide (across Z), 1 deep (X), 1 tall
        int dividerX = endTop.getBlockX() + 2;
        int y = endTop.getBlockY();
        int zCenter = endTop.getBlockZ();
        int half = config.getStartPlatformSize() / 2;
        for (int dz = -half; dz <= half; dz++) {
            world.getBlockAt(dividerX, y, zCenter + dz).setType(Material.BLACK_CONCRETE, false);
        }

        // Next start 2 blocks beyond divider at same y
        int nextStartX = dividerX + 2;
        Location nextStart = new Location(world, nextStartX, y, zCenter);
        world.getBlockAt(nextStart).setType(config.getBlockFor(session.getDifficulty()), false);
        session.setNextStartAt(nextStart);
        session.getQueuedStartBlocks().add(nextStart.clone());
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

        // Copy region blocks except markers and air
        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Material m = lib.getBlockAt(x, y, z).getType();
                    if (m == Material.AIR || m == Material.REDSTONE_BLOCK || m == Material.EMERALD_BLOCK || m == Material.NETHERITE_BLOCK) {
                        continue;
                    }
                    laneWorld.getBlockAt(x + dx, y + dy, z + dz).setType(m, false);
                }
            }
        }

        // Place start marker replacement
        laneWorld.getBlockAt(startAt).setType(config.getBlockFor(session.getDifficulty()), false);

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

        return endTop;
    }

    public void onSteppedOnStart(World world, PlayerSession session, Location startBlock) {
        // Remove the matched start from queue
        session.getQueuedStartBlocks().removeIf(loc -> loc.getBlockX() == startBlock.getBlockX()
                && loc.getBlockY() == startBlock.getBlockY()
                && loc.getBlockZ() == startBlock.getBlockZ());

        // Now it's safe to remove the previous end block if pending
        Location pending = session.getPendingEndRemoval();
        if (pending != null) {
            world.getBlockAt(pending).setType(Material.AIR, false);
            session.setPendingEndRemoval(null);
        }
    }
}


