# FragStealers

FragStealers is a PaperMC plugin for Minecraft/Paper `26.2` that lets players protect chests and barrels using signs.

## How to use

1. Place a sign directly on a chest or barrel, either attached to the side or placed on top.
2. Put this on the first line:

```text
[fs]
```

3. Save the sign.

The sign will automatically change to:

```text
[protected]
PlayerName
```

## Lock rules

- Only the lock owner can open the protected chest or barrel.
- No one can break the protected chest or barrel, including the owner.
- The owner unlocks the container by breaking the `[protected]` sign.
- Other players cannot break the protection sign.
- Only the owner can edit the protection sign.
- When the owner edits the sign, line 1 stays `[protected]` and line 2 stays the owner's name, so the protection remains clear.
- Hoppers cannot move items into or out of protected chests or barrels.
- Explosions and pistons cannot destroy or move protected chests/barrels/signs.
- Double chests are protected as one lock.

## Building

Requires Java 25+.

```bash
gradle build
```

The compiled plugin jar will be in:

```text
build/libs/FragStealers-1.0.1.jar
```

Place that jar into your server's `plugins` folder and restart the server.

## Paper API

This project uses:

```kotlin
compileOnly("io.papermc.paper:paper-api:26.2.build.+")
```

That resolves the newest available Paper `26.2` API build when Gradle builds the project.
