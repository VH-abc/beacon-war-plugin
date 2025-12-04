# Building Beacon War Plugin - Easy Mode

## Option 1: IntelliJ IDEA (Recommended - No Maven Install Needed!)

### Step 1: Download IntelliJ IDEA
- Go to https://www.jetbrains.com/idea/download/
- Download **Community Edition** (FREE)
- Install it

### Step 2: Open the Project
1. Launch IntelliJ IDEA
2. Click **Open**
3. Navigate to `beacon-war-plugin` folder
4. Select the folder (not a specific file)
5. Click **OK**

### Step 3: Wait for Maven Import
- IntelliJ will detect `pom.xml` automatically
- You'll see "Maven projects need to be imported" - click **Import**
- Wait for dependencies to download (first time only)

### Step 4: Build the Plugin
1. Look for **Maven** tab on the right side
2. Expand **beacon-war**
3. Expand **Lifecycle**
4. Double-click **package**
5. Watch the build run in the bottom panel
6. Success! JAR is in `target/beacon-war-1.0.0.jar`

**That's it!** No Maven installation needed. IntelliJ handles everything.

---

## Option 2: Manual Maven Installation (If You Really Want To)

### Windows

1. **Download Maven**
   - Go to https://maven.apache.org/download.cgi
   - Download `apache-maven-3.9.x-bin.zip` (latest version)

2. **Extract**
   - Extract to `C:\Program Files\Apache\maven`
   - You should have `C:\Program Files\Apache\maven\bin\mvn.cmd`

3. **Add to PATH**
   ```powershell
   # Run as Administrator
   [Environment]::SetEnvironmentVariable("Path", $env:Path + ";C:\Program Files\Apache\maven\bin", "Machine")
   ```

4. **Restart PowerShell** (important!)

5. **Verify**
   ```powershell
   mvn --version
   ```

6. **Build**
   ```powershell
   cd beacon-war-plugin
   mvn clean package
   ```

### Alternative: Scoop (Easier than Chocolatey)

```powershell
# Install Scoop
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex

# Install Maven
scoop install maven

# Build
cd beacon-war-plugin
mvn clean package
```

---

## Option 3: Use VS Code with Maven Extension

1. Install [VS Code](https://code.visualstudio.com/)
2. Install "Extension Pack for Java" extension
3. Install Maven (see Option 2 above)
4. Open `beacon-war-plugin` folder
5. Press `Ctrl+Shift+P` → "Maven: Execute commands"
6. Select "package"

---

## Troubleshooting

### "mvn not recognized"
- Maven bin directory is not in PATH
- Restart your terminal after PATH changes
- Verify: `echo $env:Path` should contain Maven

### "JAVA_HOME not set"
```powershell
# Find Java installation
java -XshowSettings:properties -version 2>&1 | Select-String "java.home"

# Set JAVA_HOME (adjust path to your Java installation)
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Java\jdk-17", "Machine")
```

### Chocolatey Permission Errors
- Run PowerShell as Administrator
- Or use Scoop instead (no admin required)
- Or just use IntelliJ (seriously, it's easier!)

---

## Quick Comparison

| Method | Pros | Cons | Difficulty |
|--------|------|------|------------|
| **IntelliJ IDEA** | Maven built-in, great IDE, debugging | 500MB download | ⭐ Easy |
| **Manual Maven** | Lightweight, command-line | PATH setup, troubleshooting | ⭐⭐⭐ Medium |
| **Chocolatey** | One command | Needs admin, can break | ⭐⭐⭐⭐ Hard (as you discovered!) |
| **Scoop** | Easy, no admin | Not as common | ⭐⭐ Easy-Medium |

## My Recommendation

**Just use IntelliJ IDEA Community Edition.** 

Why?
- ✅ Free
- ✅ Maven works out of the box
- ✅ Proper debugging (set breakpoints!)
- ✅ Code completion for Bukkit/Paper API
- ✅ Refactoring tools
- ✅ No PATH configuration headaches

You're going to want an IDE for plugin development anyway. Might as well get the best one! 🚀

