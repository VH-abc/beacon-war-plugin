package com.beaconwar.game;

import com.beaconwar.model.TeamColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages team scores - 1 point per minute per beacon controlled
 */
public class ScoreManager {
    
    private final Map<TeamColor, Integer> scores = new HashMap<>();
    
    public ScoreManager() {
        scores.put(TeamColor.RED, 0);
        scores.put(TeamColor.BLUE, 0);
    }
    
    /**
     * Award points based on beacon control
     */
    public void awardPoints(Map<TeamColor, Integer> beaconCounts) {
        int redBeacons = beaconCounts.get(TeamColor.RED);
        int blueBeacons = beaconCounts.get(TeamColor.BLUE);
        
        if (redBeacons > 0) {
            scores.put(TeamColor.RED, scores.get(TeamColor.RED) + redBeacons);
        }
        if (blueBeacons > 0) {
            scores.put(TeamColor.BLUE, scores.get(TeamColor.BLUE) + blueBeacons);
        }
        
        // Announce score update
        Bukkit.broadcast(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Score Update! ", NamedTextColor.YELLOW))
                .append(Component.text("Red: " + scores.get(TeamColor.RED), NamedTextColor.RED))
                .append(Component.text(" | ", NamedTextColor.GRAY))
                .append(Component.text("Blue: " + scores.get(TeamColor.BLUE), NamedTextColor.BLUE)));
    }
    
    public int getScore(TeamColor team) {
        return scores.getOrDefault(team, 0);
    }
    
    public void reset() {
        scores.put(TeamColor.RED, 0);
        scores.put(TeamColor.BLUE, 0);
    }
    
    public TeamColor getWinner() {
        int redScore = scores.get(TeamColor.RED);
        int blueScore = scores.get(TeamColor.BLUE);
        
        if (redScore > blueScore) {
            return TeamColor.RED;
        } else if (blueScore > redScore) {
            return TeamColor.BLUE;
        }
        return TeamColor.NEUTRAL; // Tie
    }
}

