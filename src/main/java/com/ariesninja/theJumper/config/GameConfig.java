package com.ariesninja.theJumper.config;

import com.ariesninja.theJumper.library.Difficulty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.ChatColor;
import org.bukkit.Color;
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
    private static final Map<Material, ChatColor> MATERIAL_TO_CHAT = new EnumMap<>(Material.class);
    private static final Map<Material, Color> MATERIAL_TO_COLOR = new EnumMap<>(Material.class);

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

        // Initialize material color maps (basic mapping for concrete variants)
        if (MATERIAL_TO_CHAT.isEmpty()) {
            map(Material.WHITE_CONCRETE, ChatColor.WHITE, Color.fromRGB(0xF9FFFE));
            map(Material.LIGHT_GRAY_CONCRETE, ChatColor.GRAY, Color.fromRGB(0x9D9D97));
            map(Material.GRAY_CONCRETE, ChatColor.DARK_GRAY, Color.fromRGB(0x474F52));
            map(Material.BLACK_CONCRETE, ChatColor.BLACK, Color.fromRGB(0x1D1D21));
            map(Material.RED_CONCRETE, ChatColor.RED, Color.fromRGB(0xB02E26));
            map(Material.ORANGE_CONCRETE, ChatColor.GOLD, Color.fromRGB(0xF9801D));
            map(Material.YELLOW_CONCRETE, ChatColor.YELLOW, Color.fromRGB(0xFED83D));
            map(Material.LIME_CONCRETE, ChatColor.GREEN, Color.fromRGB(0x80C71F));
            map(Material.GREEN_CONCRETE, ChatColor.DARK_GREEN, Color.fromRGB(0x5E7C16));
            map(Material.LIGHT_BLUE_CONCRETE, ChatColor.AQUA, Color.fromRGB(0x3AB3DA));
            map(Material.CYAN_CONCRETE, ChatColor.DARK_AQUA, Color.fromRGB(0x169C9C));
            map(Material.BLUE_CONCRETE, ChatColor.BLUE, Color.fromRGB(0x3C44AA));
            map(Material.PURPLE_CONCRETE, ChatColor.DARK_PURPLE, Color.fromRGB(0x8932B8));
            map(Material.MAGENTA_CONCRETE, ChatColor.LIGHT_PURPLE, Color.fromRGB(0xC74EBD));
            map(Material.PINK_CONCRETE, ChatColor.LIGHT_PURPLE, Color.fromRGB(0xF38BAA));
            map(Material.BROWN_CONCRETE, ChatColor.GOLD, Color.fromRGB(0x835432));
        }
    }

    private void map(Material m, ChatColor cc, Color color) {
        MATERIAL_TO_CHAT.put(m, cc);
        MATERIAL_TO_COLOR.put(m, color);
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

    public void setGameWorldName(String gameWorldName) {
        this.gameWorldName = gameWorldName;
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

    public ChatColor getChatColorFor(Difficulty difficulty) {
        Material m = getBlockFor(difficulty);
        return MATERIAL_TO_CHAT.getOrDefault(m, ChatColor.WHITE);
    }

    public Color getBukkitColorFor(Difficulty difficulty) {
        Material m = getBlockFor(difficulty);
        return MATERIAL_TO_COLOR.getOrDefault(m, Color.WHITE);
    }

    public YamlConfiguration getLibraryCfg() {
        return libraryCfg;
    }
}


