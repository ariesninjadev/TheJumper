package com.ariesninja.theJumper;

import com.ariesninja.theJumper.command.JumperCommand;
import com.ariesninja.theJumper.config.GameConfig;
import com.ariesninja.theJumper.game.GameManager;
import com.ariesninja.theJumper.listener.PlayerMovementListener;
import com.ariesninja.theJumper.listener.PlayerQuitListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TheJumper extends JavaPlugin {

    private static TheJumper instance;
    private GameConfig gameConfig;
    private GameManager gameManager;
    private com.ariesninja.theJumper.game.SpectateManager spectateManager;

    public static TheJumper getInstance() {
        return instance;
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public com.ariesninja.theJumper.game.SpectateManager getSpectateManager() {
        return spectateManager;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Ensure default configs are saved on first run
        saveDefaultConfig();
        saveResource("difficulties.yml", false);
        saveResource("library.yml", false);

        // Load configuration wrapper
        this.gameConfig = new GameConfig(this);

        // Initialize core manager(s)
        this.gameManager = new GameManager(this);
        this.spectateManager = new com.ariesninja.theJumper.game.SpectateManager(this);
        this.spectateManager.start();

        // Register commands
        if (getCommand("jumper") != null) {
            JumperCommand jc = new JumperCommand(this);
            getCommand("jumper").setExecutor(jc);
            getCommand("jumper").setTabCompleter(jc);
        }
        if (getCommand("jumperhelp") != null) {
            getCommand("jumperhelp").setExecutor(new com.ariesninja.theJumper.command.JumperHelpCommand(this));
        }

        // Register listeners
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new PlayerMovementListener(this), this);
        pm.registerEvents(new PlayerQuitListener(this), this);

        getLogger().info("TheJumper enabled.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (spectateManager != null) {
            spectateManager.stop();
        }
        getLogger().info("TheJumper disabled.");
    }
}
