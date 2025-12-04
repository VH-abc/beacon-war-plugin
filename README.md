# Beacon War Plugin

A team-based beacon control game for Minecraft Paper/Spigot servers. **NO MORE CURSED DATAPACKS!** 🎉

## What This Does

Beacon War is a territorial control game where two teams (Red and Blue) compete to capture beacons placed in a line across the map. Teams can only capture adjacent beacons, creating a dynamic front-line battle system.

### Features

- 🎯 **11 beacons** placed automatically in a line (indices -5 to 5)
- 🌊 **Water avoidance** during beacon placement
- ✅ **Capture validation** - can only capture adjacent beacons
- 📍 **Dynamic team spawns** based on beacon control
- 🎨 **Visual ownership** via colored glass (red/blue)
- 💪 **Proper Java** - no scoreboard gymnastics or execute chains!

## Building

### Requirements

- Java 17 or higher
- Maven 3.6+

### Compile

```bash
cd beacon-war-plugin
mvn clean package
```

The compiled JAR will be in `target/beacon-war-1.0.0.jar`

## Installation

1. Build the plugin (see above)
2. Copy `beacon-war-1.0.0.jar` to your server's `plugins` folder
3. Restart the server
4. Done! No datapack shenanigans needed.

## Usage

### Setup (Admin Only)

```bash
/bw setup
```

Stand where you want the center beacon (beacon 0) and run this command. The plugin will:
- Place beacon 0 at your location
- Place beacons 1-5 extending in the positive X direction
- Place beacons -1 to -5 in the negative X direction
- Avoid water bodies automatically
- Use fallback positions if needed

### Game Control (Admin Only)

```bash
/bw start    # Start the game
/bw stop     # Stop the game
```

### Player Commands

```bash
/bw join red     # Join the red team
/bw join blue    # Join the blue team
/bw status       # Show current game status
/bw help         # Show all commands
```

## Game Mechanics

### Capturing Beacons

To capture a beacon:
1. Place colored stained glass above the beacon block
   - Red glass = Red team captures
   - Blue glass = Blue team captures
   - No glass = Neutral

### Capture Rules

You can only capture a beacon if:
- An adjacent beacon (left or right) is owned by your team
- This creates a continuous front-line system
- Invalid captures are automatically reverted

### Team Spawns

Team spawn points dynamically move based on beacon control:
- **Red spawn**: `-200 × (red_beacons - 7)`
- **Blue spawn**: `200 × (blue_beacons - 7)`

The more beacons you control, the further forward your spawn moves!

## Comparison to Datapack Version

| Feature | Datapack | Plugin |
|---------|----------|--------|
| Variables | Scoreboards 😭 | Actual Java variables 🎉 |
| Loops | Recursive functions 🤯 | For loops 😎 |
| Data structures | NBT storage + execute 😵 | HashMap, ArrayList 🚀 |
| Performance | Meh | Fast |
| Debugging | Pain | IntelliJ |
| Maintenance | Nightmare | Pleasant |

## Permissions

- `beaconwar.use` - Basic commands (default: true)
- `beaconwar.admin` - Admin commands like setup/start/stop (default: op)

## Configuration

Currently no config file needed! Game parameters are hardcoded:
- Beacon spacing: 200 blocks
- Beacon count: 11 total (-5 to 5)
- Water check radius: 3 blocks

Want to change these? Edit the constants in `BeaconPlacer.java` and recompile.

## Technical Details

### Architecture

```
BeaconWarPlugin (Main)
├── GameManager - Coordinates all systems
├── BeaconManager - Tracks beacon states and ownership
├── BeaconPlacer - Handles initial beacon placement
├── SpawnManager - Calculates team spawn positions
└── Listeners - Event handlers (block place, respawn)
```

### Models

- `Beacon` - Represents a single beacon with position, index, and owner
- `TeamColor` - Enum for RED, BLUE, NEUTRAL teams

### Game Loop

The plugin runs a tick task every game tick (20 times/second):
1. Update beacon ownership from glass blocks
2. Validate any ownership changes
3. Update team spawn points

## Credits

Rewritten from the cursed datapack version that made everyone's eyes bleed. You're welcome! 😄

## License

MIT - Do whatever you want with it!

