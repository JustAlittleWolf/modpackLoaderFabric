<img src="src/main/resources/assets/modpackloaderfabric/icon.png" width="128">

# modpackLoaderFabric
Using this mod makes it very easy to download a ton of mods from a list of modpacks and keep them up to date.

## Dependencies
*These will be automatically downloaded at first launch, restart the game to apply*
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Modmenu](https://modrinth.com/mod/modmenu)
- [Cloth Config API](https://www.curseforge.com/minecraft/mc-mods/cloth-config)

## Usage
This mod uses the json format to store which mods to download and keep up to date. On startup it searches [Modrinth](https://modrinth.com/mods) and [CurseForge](https://www.curseforge.com/minecraft/mc-mods) to see if a new version of the mods are available. If that is the case, *modpackLoderFabric* will download the new version and delete the old version of the mod.
After launching the game with a new modpack selected, a restart is required to load the new mods.
By default, the game checks for updates only once a day to reduce startup time.

### First startup
Due to some technical limitations, this mod only sees the mods it has added itself. That means if you have an old version of another mod installed and update it with *modpackLoaderFabric* it will not delete the old mod. Sometimes this is not an issue, but just to be sure scan your mods folder for duplicate mods, and delete the old versions.

### Modpack from repository
The easiest method at the moment is to use my respository at [wolfii.me](https://wolfii.me/ModpackLoaderFabric/availableModpacks.php). You can access all the modpacks from the config screen in modmenu under `Hosted Modpacks`.
Uploading modpacks to this website to make them publicly available will be possible soon.

### Modpack from local file
If you want to create your own modpack, you can simply do so by creating a file in your /config/MPLF_Modpacks and selecting it from the config screen under `Local Modpacks`. This file Format has to match [the modpack format](#modpack-format).

### Modpack from external URL
Lastly, it is also possible to add a modpack by URL. This is done in the `External Modpacks` panel in the config screen. Modpacks added this way also have to match [the modpack format](#modpack-format).

## Config
There are 3 main options in the config menu:
- `Check for updates on game start` - Disabling this option prevents the mod from making any further changes to your mods folder.
- `Update interval` - This option allows you to customise how often *modpackLoaderFabric* will check for updates. Setting this to 0 (not recommended, launching takes a while) will check for mod updates on every game launch.
- `Force update on next start` - After selecting a new modpack it is recommended to also check this option as well, as the mod will probably skip update checking by default due to the **update interval**.

![image](https://user-images.githubusercontent.com/54244277/167492039-5aae8daf-7388-443f-9a97-87daddac21f1.png)

## Modpack format
The modpacks have to be stored in a json format
```json
{
    "modrinth": {
        "versions": ["1.18.2", "1.18.1", "1.18"],
        "mods": ["P7dR8mSH"]
    },
    "curseforge": {
        "versions": "73250",
        "mods": ["308702"]
    }
}
```
* `mods`: A Json array of modids from the selected platform.
* Modrinth-`versions`: A Json array of the Minecraft versions that will be searched for.
* CurseForge-`versions`: A string of which Minecraft version will be searched for (`73250` = 1.18-1.18.2).

#### Mod-ID on modrinth
![image](https://user-images.githubusercontent.com/54244277/167493765-02f2135c-e071-42bd-bfb6-de73a3337ecd.png)

#### Mod-ID on curseforge
![image](https://user-images.githubusercontent.com/54244277/167493845-2bfd601f-3e28-4ee5-85a0-0ca827c97108.png)
