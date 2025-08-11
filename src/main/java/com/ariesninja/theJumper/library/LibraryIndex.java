package com.ariesninja.theJumper.library;

import com.ariesninja.theJumper.config.GameConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public final class LibraryIndex {
    private final GameConfig config;
    private final Map<Difficulty, List<JumpRegion>> regionsByDifficulty = new EnumMap<>(Difficulty.class);

    public LibraryIndex(GameConfig config) {
        this.config = config;
        reload();
    }

    public void reload() {
        regionsByDifficulty.clear();
        YamlConfiguration cfg = config.getLibraryCfg();
        ConfigurationSection diffSec = cfg.getConfigurationSection("difficulties");
        if (diffSec == null) return;
        for (String key : diffSec.getKeys(false)) {
            Difficulty difficulty = Difficulty.fromId(key);
            if (difficulty == null) continue;
            List<JumpRegion> list = new ArrayList<>();
            ConfigurationSection listSec = diffSec.getConfigurationSection(key + ".regions");
            if (listSec != null) {
                for (String id : listSec.getKeys(false)) {
                    ConfigurationSection r = listSec.getConfigurationSection(id);
                    if (r == null) continue;
                    String worldName = r.getString("world", config.getLibraryWorldName());
                    int minX = r.getInt("min.x");
                    int minY = r.getInt("min.y");
                    int minZ = r.getInt("min.z");
                    int maxX = r.getInt("max.x");
                    int maxY = r.getInt("max.y");
                    int maxZ = r.getInt("max.z");
                    list.add(new JumpRegion(worldName, minX, minY, minZ, maxX, maxY, maxZ));
                }
            }
            regionsByDifficulty.put(difficulty, list);
        }
    }

    public List<JumpRegion> getRegionsFor(Difficulty difficulty) {
        return regionsByDifficulty.getOrDefault(difficulty, Collections.emptyList());
    }

    public boolean hasRegions(Difficulty difficulty) {
        List<JumpRegion> list = regionsByDifficulty.get(difficulty);
        return list != null && !list.isEmpty();
    }

    public Difficulty nextAvailableAfter(Difficulty current) {
        Difficulty next = current.nextOrNull();
        while (next != null) {
            if (hasRegions(next)) return next;
            next = next.nextOrNull();
        }
        return null;
    }

    public Difficulty hardestAvailableDifficulty() {
        Difficulty last = null;
        for (Difficulty d : Difficulty.values()) {
            if (hasRegions(d)) last = d;
        }
        return last;
    }
}


