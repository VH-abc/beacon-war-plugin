# Datapack vs Plugin: A Side-by-Side Comparison

This document shows how common patterns in the datapack version are handled in the plugin version.

## Example 1: Checking Beacon Ownership

### Datapack Version (Cursed 😭)

```mcfunction
# Remember previous owner value
scoreboard players operation @s bw_prev_owner = @s bw_owner

# Read the current glass color
scoreboard players set @s bw_owner 0
execute if block ~ ~1 ~ minecraft:red_stained_glass run scoreboard players set @s bw_owner 1
execute if block ~ ~1 ~ minecraft:blue_stained_glass run scoreboard players set @s bw_owner 2

# If ownership changed, validate the capture is legal
execute unless score @s bw_owner = @s bw_prev_owner run function beacon_war:beacons/validate_capture
```

Problems:
- Uses scoreboard as variables
- Entity context switching with `@s`
- Chained execute commands
- Needs separate function file for validation

### Plugin Version (Clean 🎉)

```java
public void updateOwnerFromGlass() {
    Location glassLocation = location.clone().add(0, 1, 0);
    Material material = glassLocation.getBlock().getType();
    
    TeamColor newOwner = switch (material) {
        case RED_STAINED_GLASS -> TeamColor.RED;
        case BLUE_STAINED_GLASS -> TeamColor.BLUE;
        default -> TeamColor.NEUTRAL;
    };
    
    if (newOwner != owner) {
        setOwner(newOwner);
    }
}
```

Benefits:
- Real variables (`location`, `material`, `newOwner`)
- Switch expressions
- Type safety
- All logic in one place

---

## Example 2: Validating Beacon Capture

### Datapack Version (Cursed 😭)

```mcfunction
# Set up validation flag (assume valid by default)
scoreboard players set #capture_valid bw_state 1

# Check if beacon to the left (index - 1) is Red-owned
scoreboard players operation #check_index bw_state = @s bw_index
scoreboard players operation #check_index bw_state -= #1 bw_state
execute as @e[type=minecraft:marker,tag=bw_beacon_ready] if score @s bw_index = #check_index bw_state if score @s bw_prev_owner matches 1 run scoreboard players set #left_is_red bw_state 1
execute as @e[type=minecraft:marker,tag=bw_beacon_ready] if score @s bw_index = #check_index bw_state unless score @s bw_prev_owner matches 1 run scoreboard players set #left_is_red bw_state 0

# Check if beacon to the right (index + 1) is Blue-owned
scoreboard players operation #check_index bw_state = @s bw_index
scoreboard players operation #check_index bw_state += #1 bw_state
execute as @e[type=minecraft:marker,tag=bw_beacon_ready] if score @s bw_index = #check_index bw_state if score @s bw_prev_owner matches 2 run scoreboard players set #right_is_blue bw_state 1
execute as @e[type=minecraft:marker,tag=bw_beacon_ready] if score @s bw_index = #check_index bw_state unless score @s bw_prev_owner matches 2 run scoreboard players set #right_is_blue bw_state 0

# If left is Red, this beacon must stay Red
execute if score #left_is_red bw_state matches 1 unless score @s bw_owner matches 1 run scoreboard players set #capture_valid bw_state 0

# If right is Blue, this beacon must stay Blue
execute if score #right_is_blue bw_state matches 1 unless score @s bw_owner matches 2 run scoreboard players set #capture_valid bw_state 0
```

Problems:
- 15+ lines of command code
- Manual scoreboard arithmetic
- Entity selector with score predicates
- Multiple temporary variables
- Hard to understand the logic

### Plugin Version (Clean 🎉)

```java
private void validateCapture(Beacon beacon) {
    TeamColor newOwner = beacon.getOwner();
    int index = beacon.getIndex();
    
    Beacon leftBeacon = beacons.get(index - 1);
    Beacon rightBeacon = beacons.get(index + 1);
    
    boolean valid = false;
    
    if (leftBeacon != null && leftBeacon.getPreviousOwner() == newOwner) {
        valid = true;
    }
    
    if (rightBeacon != null && rightBeacon.getPreviousOwner() == newOwner) {
        valid = true;
    }
    
    if (newOwner == TeamColor.RED && leftBeacon != null && 
        leftBeacon.getPreviousOwner() == TeamColor.BLUE) {
        valid = false;
    }
    
    if (valid) {
        announceCapture(beacon);
    } else {
        revertCapture(beacon);
    }
}
```

Benefits:
- Readable boolean logic
- HashMap lookup instead of entity selectors
- Actual if statements
- The logic is obvious

---

## Example 3: Placing Beacons with Water Avoidance

### Datapack Version (Cursed 😭)

```mcfunction
# Try position 1: (prev_x + 200, prev_z)
execute store result storage beacon_war:temp try_x int 1 run scoreboard players operation #try_x bw_state = #prev_x bw_state
scoreboard players add #try_x bw_state 200
execute store result storage beacon_war:temp try_x int 1 run scoreboard players get #try_x bw_state
execute store result storage beacon_war:temp try_z int 1 run scoreboard players get #prev_z bw_state

tellraw @a ["",{"text":"[Beacon War Debug] ","color":"gray"},{"text":"Trying position 1: (","color":"yellow"},{"score":{"name":"#try_x","objective":"bw_state"},"color":"white"},{"text":", ","color":"white"},{"score":{"name":"#try_z","objective":"bw_state"},"color":"white"},{"text":")","color":"yellow"}]

function beacon_war:setup/check_water_at_pos with storage beacon_war:temp

# If not water, place there
execute if score #is_water bw_state matches 0 run tellraw @a ["",{"text":"[Beacon War Debug] ","color":"gray"},{"text":"No water found, placing beacon here","color":"green"}]
execute if score #is_water bw_state matches 0 run function beacon_war:setup/place_at_try_pos with storage beacon_war:temp

# If water, try fallback positions
execute if score #is_water bw_state matches 1 run tellraw @a ["",{"text":"[Beacon War Debug] ","color":"gray"},{"text":"Water detected, trying fallbacks","color":"red"}]
execute if score #is_water bw_state matches 1 run function beacon_war:setup/try_positive_fallbacks
```

Problems:
- Coordinates stored in scoreboards and NBT storage
- Macros needed for coordinate passing
- Multiple function files
- No actual loop constructs
- Recursive function calls for fallbacks

### Plugin Version (Clean 🎉)

```java
private Location placeNextBeacon(Location prevLoc, int index, boolean positive) {
    int prevX = prevLoc.getBlockX();
    int prevZ = prevLoc.getBlockZ();
    
    // Primary position
    int tryX = positive ? prevX + SPACING : prevX - SPACING;
    int tryZ = prevZ;
    
    Location loc = findGround(world, tryX, 150, tryZ);
    if (loc != null && !isWaterNearby(loc)) {
        buildBeacon(loc, index);
        return loc;
    }
    
    // Try fallbacks
    List<int[]> fallbacks = generateFallbackPositions(prevX, prevZ, positive);
    for (int[] pos : fallbacks) {
        loc = findGround(world, pos[0], 150, pos[1]);
        if (loc != null && !isWaterNearby(loc)) {
            buildBeacon(loc, index);
            return loc;
        }
    }
    
    return null;
}
```

Benefits:
- Real coordinates (just integers!)
- Actual for loop
- Early return pattern
- No function recursion
- Clear control flow

---

## Example 4: Team Spawn Calculation

### Datapack Version (Cursed 😭)

```mcfunction
# Calculate Blue spawn X coordinate: 200*(blue_beacons - 7)
scoreboard players operation #blue_spawn_x bw_state = #blue bw_team_beacons
scoreboard players remove #blue_spawn_x bw_state 7
scoreboard players set #200 bw_state 200
scoreboard players operation #blue_spawn_x bw_state *= #200 bw_state

# Calculate Red spawn X coordinate: -200*(red_beacons - 7)
scoreboard players operation #red_spawn_x bw_state = #red bw_team_beacons
scoreboard players remove #red_spawn_x bw_state 7
scoreboard players operation #red_spawn_x bw_state *= #200 bw_state
scoreboard players operation #red_spawn_x bw_state *= #-1 bw_state
```

Problems:
- Manual arithmetic via scoreboard operations
- Need to store constant `200` in scoreboard
- Need to store constant `-1` for negation
- No actual subtraction operator

### Plugin Version (Clean 🎉)

```java
public void updateSpawns() {
    Map<TeamColor, Integer> counts = beaconManager.getTeamCounts();
    int redBeacons = counts.get(TeamColor.RED);
    int blueBeacons = counts.get(TeamColor.BLUE);
    
    int redSpawnX = -200 * (redBeacons - 7);
    int blueSpawnX = 200 * (blueBeacons - 7);
    
    redSpawn = findClosestBeaconLocation(redSpawnX);
    blueSpawn = findClosestBeaconLocation(blueSpawnX);
}
```

Benefits:
- One line per formula
- Actual math operators
- No constant storage needed
- Readable variable names

---

## Summary Table

| Operation | Datapack Lines | Plugin Lines | Readability |
|-----------|----------------|--------------|-------------|
| Check beacon ownership | ~10 | 9 | Plugin 10x better |
| Validate capture | ~20 | 25 | Plugin 100x better |
| Place beacon with water check | ~15 (+ 3 files) | 20 | Plugin much cleaner |
| Calculate spawn | 8 | 5 | Plugin way clearer |
| Store beacon list | NBT array + 32 execute lines | `HashMap<Integer, Beacon>` | No contest |

## The Real Difference

**Datapack**: You're fighting the system to do basic programming.

**Plugin**: You're actually programming.

Choose plugins. Your future self will thank you. 🚀

