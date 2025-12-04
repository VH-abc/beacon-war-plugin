package com.beaconwar.game;

import com.beaconwar.model.Beacon;
import com.beaconwar.model.TeamColor;
import org.bukkit.Location;

import java.util.Comparator;
import java.util.List;

/**
 * Manages territory boundaries based on beacon control
 */
public class TerritoryManager {
    
    private final BeaconManager beaconManager;
    private final int spacing;
    
    public TerritoryManager(BeaconManager beaconManager, int spacing) {
        this.beaconManager = beaconManager;
        this.spacing = spacing;
    }
    
    /**
     * Get the frontmost beacon X coordinate for a team
     * Red = most positive X, Blue = most negative X
     */
    private Integer getFrontmostBeaconX(TeamColor team) {
        List<Beacon> teamBeacons = beaconManager.getAllBeacons().stream()
                .filter(b -> b.getOwner() == team)
                .toList();
        
        if (teamBeacons.isEmpty()) {
            return null;
        }
        
        if (team == TeamColor.RED) {
            // Red's front = most negative X
            return teamBeacons.stream()
                    .map(b -> b.getLocation().getBlockX())
                    .min(Integer::compareTo)
                    .orElse(null);
        } else {
            // Blue's front = most positive X
            return teamBeacons.stream()
                    .map(b -> b.getLocation().getBlockX())
                    .max(Integer::compareTo)
                    .orElse(null);
        }
    }
    
    /**
     * Check if a location is in a team's territory
     * Red territory: x > frontMostBeaconX - spacing/2
     * Blue territory: x < frontMostBeaconX + spacing/2
     */
    public boolean isInTerritory(Location loc, TeamColor team) {
        if (team == TeamColor.NEUTRAL) {
            return false;
        }
        
        Integer frontX = getFrontmostBeaconX(team);
        if (frontX == null) {
            return false; // No beacons = no territory
        }
        
        int playerX = loc.getBlockX();
        int halfSpacing = spacing / 2;
        
        if (team == TeamColor.RED) {
            return playerX > frontX - halfSpacing;
        } else {
            return playerX < frontX + halfSpacing;
        }
    }
    
    /**
     * Get which territory a location is in (can be NEUTRAL if in no one's territory)
     */
    public TeamColor getTerritoryAt(Location loc) {
        if (isInTerritory(loc, TeamColor.RED)) {
            return TeamColor.RED;
        }
        if (isInTerritory(loc, TeamColor.BLUE)) {
            return TeamColor.BLUE;
        }
        return TeamColor.NEUTRAL;
    }
}

