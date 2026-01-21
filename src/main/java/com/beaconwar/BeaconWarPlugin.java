package com.beaconwar;

import org.bukkit.plugin.java.JavaPlugin;

import com.beaconwar.commands.BeaconWarCommand;
import com.beaconwar.commands.NetherCommand;
import com.beaconwar.commands.OverworldCommand;
import com.beaconwar.game.GameManager;
import com.beaconwar.listeners.BeaconChangeListener;
import com.beaconwar.listeners.DeathListener;
import com.beaconwar.listeners.PlayerConnectionListener;

public class BeaconWarPlugin extends JavaPlugin {
    
    private GameManager gameManager;
    
    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        
        // Initialize game manager
        gameManager = new GameManager(this);
        
        // Register commands
        BeaconWarCommand bwCommand = new BeaconWarCommand(this);
        getCommand("beaconwar").setExecutor(bwCommand);
        getCommand("nether").setExecutor(new NetherCommand());
        getCommand("overworld").setExecutor(new OverworldCommand());
        
        // Register direct commands (without /bw prefix)
        String[] directCommands = {"setup", "start", "stop", "reset", "end", "pause", "unpause", 
                                   "join", "resistance", "elo", "quicklaunch", "balanced_teams", "status"};
        for (String cmd : directCommands) {
            getCommand(cmd).setExecutor(bwCommand);
            getCommand(cmd).setTabCompleter(bwCommand);
        }
        
        // Register listeners
        getServer().getPluginManager().registerEvents(new BeaconChangeListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        
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

