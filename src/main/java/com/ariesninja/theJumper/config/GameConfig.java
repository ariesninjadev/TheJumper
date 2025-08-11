package com.ariesninja.theJumper.config;

import com.ariesninja.theJumper.library.Difficulty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class GameConfig {

    private final Plugin plugin;

    private FileConfiguration mainConfig;
    private YamlConfiguration difficultiesCfg;
    private YamlConfiguration libraryCfg;

    private String gameWorldName;
    private String libraryWorldName;

    private int laneZSpacing;
    private int laneStartX;
    private int laneBaseY;
    private int startPlatformSize;
    private Material startPlatformMaterial;

    private int preloadedJumps;
    private int completionsPerDifficulty;
    private int timeLimitSeconds;
    private int fallYBuffer;

    private final Map<Difficulty, Material> difficultyBlock = new EnumMap<>(Difficulty.class);

    public GameConfig(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        this.mainConfig = plugin.getConfig();

        File difFile = ensureInDataFolder("difficulties.yml");
        this.difficultiesCfg = YamlConfiguration.loadConfiguration(difFile);

        File libFile = ensureInDataFolder("library.yml");
        this.libraryCfg = YamlConfiguration.loadConfiguration(libFile);

        // Worlds
        this.gameWorldName = mainConfig.getString("worlds.game", "thejumper_game");
        this.libraryWorldName = mainConfig.getString("worlds.library", "thejumper_library");

        // Lanes
        this.laneZSpacing = mainConfig.getInt("lane.spacingZ", 21);
        this.laneStartX = mainConfig.getInt("lane.startX", 0);
        this.laneBaseY = mainConfig.getInt("lane.baseY", 80);
        this.startPlatformSize = mainConfig.getInt("lane.startPlatform.size", 5);
        String startMat = Objects.requireNonNullElse(mainConfig.getString("lane.startPlatform.material"), "LIGHT_GRAY_CONCRETE");
        this.startPlatformMaterial = parseMaterial(startMat, Material.LIGHT_GRAY_CONCRETE);

        // Gameplay
        this.preloadedJumps = mainConfig.getInt("gameplay.preloadedJumps", 3);
        this.completionsPerDifficulty = mainConfig.getInt("gameplay.completionsPerDifficulty", 20);
        this.timeLimitSeconds = mainConfig.getInt("gameplay.timeLimitSeconds", 3600);
        this.fallYBuffer = mainConfig.getInt("gameplay.fallYBuffer", 10);

        // Difficulties mapping
        ConfigurationSection diffSec = difficultiesCfg.getConfigurationSection("difficulties");
        if (diffSec != null) {
            for (String key : diffSec.getKeys(false)) {
                Difficulty difficulty = Difficulty.fromId(key);
                if (difficulty == null) continue;
                String matName = diffSec.getString(key + ".block", "WHITE_CONCRETE");
                difficultyBlock.put(difficulty, parseMaterial(matName, Material.WHITE_CONCRETE));
            }
        }

        // Ensure every difficulty has some material
        for (Difficulty d : Difficulty.values()) {
            difficultyBlock.putIfAbsent(d, Material.WHITE_CONCRETE);
        }
    }

    private File ensureInDataFolder(String fileName) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();
        }
        File file = new File(dataFolder, fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return file;
    }

    private Material parseMaterial(String name, Material def) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown material '" + name + "', using " + def);
            return def;
        }
    }

    public World getGameWorld() {
        return Bukkit.getWorld(gameWorldName);
    }

    public World getLibraryWorld() {
        return Bukkit.getWorld(libraryWorldName);
    }

    public String getGameWorldName() {
        return gameWorldName;
    }

    public String getLibraryWorldName() {
        return libraryWorldName;
    }

    public int getLaneZSpacing() {
        return laneZSpacing;
    }

    public int getLaneStartX() {
        return laneStartX;
    }

    public int getLaneBaseY() {
        return laneBaseY;
    }

    public int getStartPlatformSize() {
        return startPlatformSize;
    }

    public Material getStartPlatformMaterial() {
        return startPlatformMaterial;
    }

    public int getPreloadedJumps() {
        return preloadedJumps;
    }

    public int getCompletionsPerDifficulty() {
        return completionsPerDifficulty;
    }

    public int getTimeLimitSeconds() {
        return timeLimitSeconds;
    }

    public int getFallYBuffer() {
        return fallYBuffer;
    }

    public Material getBlockFor(Difficulty difficulty) {
        return difficultyBlock.get(difficulty);
    }

    public YamlConfiguration getLibraryCfg() {
        return libraryCfg;
    }
}


