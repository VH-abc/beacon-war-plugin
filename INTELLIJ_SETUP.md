# IntelliJ IDEA Setup Guide

## Problem: Can't See Maven Tab?

### Solution 1: Enable Maven Plugin

1. **File** → **Settings** (or `Ctrl+Alt+S`)
2. Go to **Plugins**
3. Search for "Maven"
4. Make sure **Maven** plugin is checked/enabled
5. Click **OK** and restart IntelliJ

### Solution 2: Import as Maven Project

1. Close the project (**File** → **Close Project**)
2. On the welcome screen, click **Open**
3. Navigate to `beacon-war-plugin` folder
4. Select the **pom.xml** file directly (not the folder!)
5. Click **Open as Project**
6. IntelliJ will ask how to open it → Choose **Open as Project**
7. Wait for IntelliJ to index and import

### Solution 3: Show Maven Tool Window

The Maven tab might just be hidden:

1. **View** → **Tool Windows** → **Maven**
2. Or press `Ctrl` twice (double-tap) and type "Maven" → select "Maven tool window"

### Solution 4: Re-import Maven Project

1. Right-click on `pom.xml` in the Project view
2. Select **Maven** → **Reload project**
3. Wait for dependencies to download

### Solution 5: Check Project Structure

1. **File** → **Project Structure** (or `Ctrl+Alt+Shift+S`)
2. Under **Project**, make sure SDK is set (Java 17 or higher)
3. Under **Modules**, you should see `beacon-war` module
4. If not, click **+** → **Import Module** → select `pom.xml`

---

## Once Maven Tab Appears

You should see the Maven tool window on the right side (or wherever you docked it).

Expand it to see:
```
beacon-war
├── Lifecycle
│   ├── clean
│   ├── validate
│   ├── compile
│   ├── test
│   ├── package  ← Double-click this!
│   ├── verify
│   └── install
├── Plugins
└── Dependencies
```

Double-click **package** to build the plugin!

---

## Alternative: Build Without Maven Tab

If Maven tab still won't show up, you can build via terminal:

1. **View** → **Tool Windows** → **Terminal** (or `Alt+F12`)
2. Run:
   ```bash
   mvn clean package
   ```

This requires Maven to be installed separately (see BUILD_INSTRUCTIONS.md).

---

## Verify Java Version

Maven needs Java 17+. Check your Java version:

1. Open **Terminal** in IntelliJ
2. Run:
   ```bash
   java -version
   ```

Should show version 17 or higher. If not:

1. **File** → **Project Structure**
2. **Project** → **SDK**
3. Click dropdown and select Java 17+ (or download it)

---

## Still Having Issues?

### Check IntelliJ Version
- **Help** → **About**
- Make sure you're using IntelliJ IDEA 2020.3 or newer
- Community Edition or Ultimate both work

### Fresh Start
1. **File** → **Invalidate Caches**
2. Select **Invalidate and Restart**
3. After restart, re-import the project

### Manual Build (Fallback)
See BUILD_INSTRUCTIONS.md for installing Maven separately and building from command line.

