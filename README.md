# Nightmare Realms

A three-phase custom boss fight for Fabric, targeting **Minecraft 1.21.11**.

- **Phase I - Shadow Sovereign**: an empowered Enderman (300 HP)
- **Phase II - Dread Knight**: a netherite-equipped Wither Skeleton (450 HP)
- **Phase III - Apex Phantom**: a giant Phantom finale (600 HP)

A boss bar tracks whichever phase is active, and the fight automatically
advances to the next phase when the current boss dies.

## Starting the fight

Run the command as an operator (permission level 2+):

```
/nightmarerealms start
```

This spawns Phase I at your current position and adds you to the boss bar.

## Building the mod

This is a standard [Fabric Loom](https://fabricmc.net/develop/) Gradle
project. You'll need a JDK 21.

1. Open the folder in IntelliJ IDEA (with the Gradle plugin) or another
   Gradle-aware IDE, and let it import the project — it will download the
   Fabric Loom plugin, Minecraft 1.21.11, and the Yarn mappings the first
   time you build.
2. To build a shippable jar from a terminal instead:
   - If you have Gradle installed locally, run `gradle wrapper` once inside
     this folder (this repo ships `gradle/wrapper/gradle-wrapper.properties`
     but not the wrapper jar/scripts themselves, since those are binary
     files), which will generate `gradlew` / `gradlew.bat`.
   - Then run `./gradlew build` (or `gradlew.bat build` on Windows).
3. The finished mod jar will be in `build/libs/nightmare-realms-1.0.0.jar`.
   Drop that into your Fabric server or client's `mods` folder (you'll also
   need [Fabric API](https://modrinth.com/mod/fabric-api) and
   [Fabric Loader](https://fabricmc.net/use/) matching 1.21.11 installed).

## Versions this project targets

| | |
|---|---|
| Minecraft | 1.21.11 |
| Yarn mappings | 1.21.11+build.4 |
| Fabric Loader | 0.18.4 |
| Fabric API | 0.141.1+1.21.11 |
| Fabric Loom | 1.14-SNAPSHOT |
| Java | 21 |

If a newer patch of any of these has come out since this project was put
together, check https://fabricmc.net/develop/ for the current numbers and
update `gradle.properties` accordingly.

## Notes on porting from older (~1.20) code

The original boss-fight code this project is based on used some APIs that
have since changed. This version updates it for current 1.21.11 mappings:

- `EntityAttributes.GENERIC_MAX_HEALTH` / `GENERIC_ATTACK_DAMAGE` are now
  `EntityAttributes.MAX_HEALTH` / `EntityAttributes.ATTACK_DAMAGE` — vanilla
  dropped the `generic.` prefix from these attribute IDs.
- `Entity#getWorld()` is deprecated; `Entity#getEntityWorld()` is used
  instead when going from a boss entity back to its world.
- Bosses are spawned via each entity's own `(EntityType, World)` constructor
  rather than `EntityType#create(World)`, since that static factory's
  signature has changed a few times across versions (it now wants a spawn
  reason) while the constructor has stayed stable.
- Added a `/nightmarerealms start` command (via Fabric API's command
  registration hook) so the fight is actually triggerable in-game — the
  original code defined `startBossFight` but never called it.

Yarn mappings do occasionally rename or move a class between versions. If
your IDE flags an import as missing, it almost certainly just moved package
— use the IDE's "fix import" / auto-import on the class name and it will
resolve it, since the mappings jar with full source is downloaded locally
during the Gradle sync.
