package com.ariesninja.theJumper.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class BlockUtils {
    private BlockUtils() {}

    public static void fill(World world, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(material, false);
                }
            }
        }
    }
}


