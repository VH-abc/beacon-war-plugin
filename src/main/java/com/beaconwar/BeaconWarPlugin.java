package com.beaconwar;

import com.beaconwar.commands.BeaconWarCommand;
import com.beaconwar.commands.NetherCommand;
import com.beaconwar.game.GameManager;
import com.beaconwar.listeners.BeaconChangeListener;
import com.beaconwar.listeners.DeathListener;
import org.bukkit.plugin.java.JavaPlugin;

public class BeaconWarPlugin extends JavaPlugin {
    
    private GameManager gameManager;
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize game manager
        gameManager = new GameManager(this);
        
        // Register commands
        getCommand("beaconwar").setExecutor(new BeaconWarCommand(this));
        getCommand("nether").setExecutor(new NetherCommand());
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new BeaconChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        
        // Start the game tick task (runs every tick = 20 times per second)
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (gameManager.isGameActive()) {
                gameManager.tick();
            }
        }, 0L, 1L);
        
        int spacing = getConfig().getInt("beacon-spacing", 200);
        getLogger().info("BeaconWar has been enabled! (Beacon spacing: " + spacing + " blocks)");
    }
    
    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.cleanup();
        }
        getLogger().info("BeaconWar has been disabled!");
    }
    
    public GameManager getGameManager() {
        return gameManager;
    }
}

