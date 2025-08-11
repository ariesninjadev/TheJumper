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

    public static TheJumper getInstance() {
        return instance;
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    public GameManager getGameManager() {
        return gameManager;
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

        // Register command
        if (getCommand("jumper") != null) {
            getCommand("jumper").setExecutor(new JumperCommand(this));
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
        getLogger().info("TheJumper disabled.");
    }
}
