package com.beaconwar.game;

import com.beaconwar.model.Beacon;
import com.beaconwar.model.TeamColor;
import org.bukkit.Location;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Manages team spawn points based on beacon control.
 * Formula: spawn_x = spacing * (beacons_controlled - total/2)
 * No more scoreboard operations needed!
 */
public class SpawnManager {
    
    private Location redSpawn;
    private Location blueSpawn;
    
    private final BeaconManager beaconManager;
    private final int spacing;
    
    public SpawnManager(BeaconManager beaconManager, int spacing) {
        this.beaconManager = beaconManager;
        this.spacing = spacing;
    }
    
    /**
     * Update spawn positions based on beacon control
     * Strategy: spawn at your second most extreme controlled beacon
     */
    public void updateSpawns() {
        // Find furthest beacon controlled by each team
        redSpawn = findSecondFurthestTeamBeacon(TeamColor.RED, false);  // Most negative
        blueSpawn = findSecondFurthestTeamBeacon(TeamColor.BLUE, true); // Most positive
    }
    
    /**
     * Find the furthest beacon controlled by a team
     * @param team The team color
     * @param positive If true, find most positive; if false, find most negative
     */
    private Location findSecondFurthestTeamBeacon(TeamColor team, boolean positive) {
        List<Beacon> teamBeacons = beaconManager.getAllBeacons().stream()
                .filter(b -> b.getOwner() == team)
                .sorted(Comparator.comparingInt(b -> positive ? -b.getIndex() : b.getIndex()))
                .toList();
        
        if (teamBeacons.size() < 2) {
           // Spawn at distance spacing behind the home beacon (5 for red, -5 for blue)
           int homeBeaconIndex = (team == TeamColor.RED) ? 5 : -5;
           Beacon homeBeacon = beaconManager.getBeacon(homeBeaconIndex);
           Location loc = homeBeacon.getLocation().clone();
           // if red, add spacing to the x coordinate, if blue, subtract spacing from the x coordinate
           if (team == TeamColor.RED) {
            loc.add(spacing, 0, 0);
           } else {
            loc.add(-spacing, 0, 0);
           }
           // find ground block below loc, using BeaconPlacer.findGround
           Location groundLoc = BeaconPlacer.findGround(loc.getWorld(), loc.getBlockX(), 200, loc.getBlockZ());
           return groundLoc.add(0, 1, 0);
        }
        
        Beacon secondFurthest = teamBeacons.get(1);
        Location loc = secondFurthest.getLocation().clone();
        loc.add(0, 2, 0); // Spawn 2 blocks above beacon
        return loc;
    }
    
    /**
     * Find the beacon closest to the target X coordinate
     */
    private Location findClosestBeaconLocation(int targetX) {
        List<Beacon> sortedBeacons = beaconManager.getSortedBeacons();
        
        if (sortedBeacons.isEmpty()) {
            return null;
        }
        
        Beacon closest = sortedBeacons.stream()
                .min(Comparator.comparingInt(b -> 
                        Math.abs(b.getLocation().getBlockX() - targetX)))
                .orElse(sortedBeacons.get(0));
        
        // Return location slightly above beacon
        Location loc = closest.getLocation().clone();
        loc.add(0, 2, 0);
        return loc;
    }
    
    public Location getRedSpawn() {
        return redSpawn != null ? redSpawn.clone() : null;
    }
    
    public Location getBlueSpawn() {
        return blueSpawn != null ? blueSpawn.clone() : null;
    }
    
    /**
     * Get spawn for a specific team
     */
    public Location getSpawn(TeamColor team) {
        return switch (team) {
            case RED -> getRedSpawn();
            case BLUE -> getBlueSpawn();
            default -> null;
        };
    }
}

