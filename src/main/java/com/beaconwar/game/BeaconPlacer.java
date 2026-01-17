package com.beaconwar.game;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import com.beaconwar.model.Beacon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Handles beacon placement with water/lava avoidance.
 * Supports both Overworld and Nether dimensions.
 */
public class BeaconPlacer {
    
    private static final int MAX_FALLBACK_ATTEMPTS = 8;
    private static final int NETHER_SEARCH_MIN_Y = 50;
    private static final int NETHER_SEARCH_MAX_Y = 100;
    
    private final Player player;
    private final BeaconManager beaconManager;
    private final int spacing;
    private final int groundSearchStartY;
    private final int beaconsPerSide;
    private final boolean spawnCastles;
    private final boolean spawnCastleGates;
    private final boolean isNether;
    
    public BeaconPlacer(Player player, BeaconManager beaconManager, int spacing, int beaconsPerSide, int groundSearchStartY, boolean spawnCastles, boolean spawnCastleGates, double netherSpacingMultiplier) {
        this.player = player;
        this.beaconManager = beaconManager;
        this.beaconsPerSide = beaconsPerSide;
        this.spawnCastles = spawnCastles;
        this.spawnCastleGates = spawnCastleGates;
        
        // Detect Nether environment
        this.isNether = player.getWorld().getEnvironment() == World.Environment.NETHER;
        
        // Apply spacing multiplier for Nether
        this.spacing = isNether ? (int)(spacing * netherSpacingMultiplier) : spacing;
        
        // In Nether, groundSearchStartY is randomized per-beacon; store the Overworld value
        this.groundSearchStartY = groundSearchStartY;
    }
    
    /**
     * Get the search start Y for current dimension.
     * In Nether, returns a random Y between 50-100 for terrain variety.
     */
    private int getSearchStartY() {
        if (isNether) {
            return ThreadLocalRandom.current().nextInt(NETHER_SEARCH_MIN_Y, NETHER_SEARCH_MAX_Y + 1);
        }
        return groundSearchStartY;
    }
    
    /**
     * Place all 11 beacons in a line
     */
    public boolean placeAllBeacons() {
        String dimensionName = isNether ? "Nether" : "Overworld";
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.GRAY)
                .append(Component.text("Starting beacon placement in " + dimensionName + "...", NamedTextColor.YELLOW)));
        
        Location spawnPoint = player.getLocation();
        World world = spawnPoint.getWorld();
        
        // Place beacon 0 at spawn (use player's Y + 2 instead of random for beacon 0)
        int beacon0SearchY = isNether ? spawnPoint.getBlockY() + 2 : groundSearchStartY;
        Location beacon0Loc = findGroundForDimension(world, spawnPoint.getBlockX(), beacon0SearchY, spawnPoint.getBlockZ());
        if (beacon0Loc == null) {
            player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                    .append(Component.text("Failed to find ground for beacon 0!", NamedTextColor.YELLOW)));
            return false;
        }
        
        // Beacon 0 location is ground block, place beacon one above
        Location beacon0BeaconLoc = beacon0Loc.clone().add(0, 1, 0);
        buildBeacon(beacon0BeaconLoc, 0);
        Beacon beacon0 = new Beacon(0, beacon0BeaconLoc);
        beacon0.initializeOwnerFromGlass(); // Initialize ownership from the glass we just placed
        beaconManager.addBeacon(beacon0);
        
        // Place positive side (1 to beaconsPerSide)
        Location prevLoc = beacon0Loc; // Keep track of ground location for next beacon
        for (int i = 1; i <= beaconsPerSide; i++) {
            Location loc = placeNextBeacon(prevLoc, i, true);
            if (loc == null) {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Failed to place beacon " + i, NamedTextColor.YELLOW)));
                return false;
            }
            prevLoc = loc;
        }
        
        // Place negative side (-1 to -beaconsPerSide)
        prevLoc = beacon0Loc;
        for (int i = -1; i >= -beaconsPerSide; i--) {
            Location loc = placeNextBeacon(prevLoc, i, false);
            if (loc == null) {
                player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.RED)
                        .append(Component.text("Failed to place beacon " + i, NamedTextColor.YELLOW)));
                return false;
            }
            prevLoc = loc;
        }
        
        String hazardType = isNether ? "lava" : "water";
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.AQUA)
                .append(Component.text("Beacon line deployed with " + hazardType + " avoidance!", NamedTextColor.GREEN)));
        
        return true;
    }
    
    /**
     * Place next beacon relative to previous, avoiding water/lava
     */
    private Location placeNextBeacon(Location prevLoc, int index, boolean positive) {
        World world = prevLoc.getWorld();
        int prevX = prevLoc.getBlockX();
        int prevZ = prevLoc.getBlockZ();
        int direction = positive ? 1 : -1;
        String hazardName = isNether ? "Lava" : "Water";
        
        // List of (xMultiplier, zMultiplier, requireSafe, message)
        record Attempt(double xMultiplier, double zMultiplier, boolean requireSafe, String message) {}
        
        java.util.List<Attempt> attemptsList = new java.util.ArrayList<>();
        
        // Initial attempts
        attemptsList.add(new Attempt(1, 0, true, null));
        attemptsList.add(new Attempt(2, 0, true, hazardName + " detected, trying 2x spacing for beacon " + index));
        attemptsList.add(new Attempt(1, 1, true, hazardName + " detected, trying Z+ offset for beacon " + index));
        attemptsList.add(new Attempt(1, -1, true, hazardName + " detected, trying Z- offset for beacon " + index));
        
        // Extended search: (1.1, 0), (1.2, 0), ..., (2.9, 0)
        for (double x = 1.1; x <= 2.9; x += 0.1) {
            attemptsList.add(new Attempt(x, 0, true, null));
        }
        
        // Extended search: (1, -0.8), (1, -0.6), ..., (1, 0.8)
        for (double z = -0.8; z <= 0.8; z += 0.2) {
            attemptsList.add(new Attempt(1, z, true, null));
        }
        
        // Extended search: (1.5, -1.5), (1.5, -1.3), ..., (1.5, 1.5)
        for (double z = -1.5; z <= 1.5; z += 0.2) {
            attemptsList.add(new Attempt(1.5, z, true, null));
        }
        
        // Extended search: (2, -2), (2, -1.8), ..., (2, 2)
        for (double z = -2.0; z <= 2.0; z += 0.2) {
            attemptsList.add(new Attempt(2, z, true, null));
        }
        
        // Last resort - place regardless of hazard
        attemptsList.add(new Attempt(3, 0, false, "All positions have " + hazardName.toLowerCase() + "! Placing beacon " + index + " at 3x spacing"));
        
        // Get a random search Y for this beacon (only varies in Nether)
        int searchY = getSearchStartY();
        
        for (Attempt attempt : attemptsList) {
            int tryX = (int)(prevX + (attempt.xMultiplier * spacing * direction));
            int tryZ = (int)(prevZ + (attempt.zMultiplier * spacing));
            Location groundLoc = findGroundForDimension(world, tryX, searchY, tryZ);
            
            if (groundLoc != null && (!attempt.requireSafe || !isHazard(groundLoc))) {
                if (attempt.message != null) {
                    NamedTextColor msgColor = attempt.requireSafe ? NamedTextColor.GRAY : NamedTextColor.RED;
                    player.sendMessage(Component.text("[Beacon War] ", msgColor)
                            .append(Component.text(attempt.message, NamedTextColor.YELLOW)));
                }
                return placeBeaconAt(groundLoc, index);
            }
        }
        
        return null;
    }
    
    /**
     * Place beacon at the given ground location and register it
     * Returns the ground location for the next beacon to use as reference
     */
    private Location placeBeaconAt(Location groundLoc, int index) {
        // Ground location is the solid block itself, beacon goes one block above
        Location beaconLoc = groundLoc.clone().add(0, 1, 0);
        buildBeacon(beaconLoc, index);
        Beacon beacon = new Beacon(index, beaconLoc);
        beacon.initializeOwnerFromGlass();
        beaconManager.addBeacon(beacon);
        return groundLoc; // Return ground location for next beacon
    }
    
    /**
     * Find ground using the appropriate method for current dimension
     */
    private Location findGroundForDimension(World world, int x, int startY, int z) {
        if (isNether) {
            return findGroundNether(world, x, startY, z);
        } else {
            return findGround(world, x, startY, z);
        }
    }
    
    /**
     * Find solid ground below a position (Overworld method)
     * Returns the location of the ground block itself (not the block above)
     */
    public static Location findGround(World world, int x, int startY, int z) {
        for (int y = startY; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            
            // Valid ground = any block except air, leaves, and wood
            if (!type.isAir() && 
                !type.name().contains("LEAVES") && 
                !type.name().contains("LOG") &&
                !type.name().contains("WOOD")) {
                // Return the ground block itself
                return new Location(world, x, y, z);
            }
        }
        return null;
    }
    
    /**
     * Find ground in the Nether dimension
     * Searches for air over a solid block. If lava is found before finding
     * valid air-over-solid, returns null (treated as hazard location).
     * Returns the location of the ground block itself (not the block above)
     */
    private static Location findGroundNether(World world, int x, int startY, int z) {
        boolean foundAir = false;
        
        for (int y = startY; y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            
            if (type.isAir() || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
                foundAir = true;
            } else if (type == Material.LAVA) {
                // Lava found before finding valid ground - treat as hazard
                if (!foundAir) {
                    return null;
                }
                // Lava found after air - this is a hazard location
                return new Location(world, x, y, z);
            } else if (foundAir) {
                // Found solid block after air - this is valid ground
                return new Location(world, x, y, z);
            }
            // Otherwise keep searching (we're still in solid ceiling)
        }
        return null;
    }
    
    /**
     * Check if the ground block is a hazard (water in Overworld, lava in Nether)
     */
    private boolean isHazard(Location groundLoc) {
        Block block = groundLoc.getBlock();
        Material type = block.getType();
        
        if (isNether) {
            return type == Material.LAVA;
        } else {
            return type == Material.WATER;
        }
    }
    
    /**
     * Build beacon structure at location
     * loc is the location WHERE THE BEACON BLOCK GOES (one block above ground)
     */
    private void buildBeacon(Location loc, int index) {
        World world = loc.getWorld();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Create 3x3 emerald block base (one block below beacon)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y - 1, z + dz).setType(Material.EMERALD_BLOCK);
            }
        }
        
        // Place beacon at this location
        world.getBlockAt(x, y, z).setType(Material.BEACON);
        
        // Clear column above for beam (use world max height)
        int maxY = world.getMaxHeight();
        for (int dy = 1; dy < maxY - y; dy++) {
            world.getBlockAt(x, y + dy, z).setType(Material.AIR);
        }
        
        // Place colored glass based on index
        Material glassType = Material.AIR;
        if (index > 0) {
            glassType = Material.RED_STAINED_GLASS;
        } else if (index < 0) {
            glassType = Material.BLUE_STAINED_GLASS;
        }
        world.getBlockAt(x, y + 1, z).setType(glassType);
        
        player.sendMessage(Component.text("[Beacon War] ", NamedTextColor.GRAY)
                .append(Component.text("Placed beacon " + index + " at (" + x + ", " + z + ")", 
                        NamedTextColor.GREEN)));
        
        // Build castle around beacon if enabled
        if (spawnCastles) {
            CastleBuilder castle = new CastleBuilder(loc, spawnCastleGates);
            castle.build();
        }
    }
}

