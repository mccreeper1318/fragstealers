# FragStealers

FragStealers is a PaperMC plugin for Minecraft/Paper `26.2` that lets players protect chests and barrels using signs. Version `1.1.0` adds an administrator-only Master Key for resolving false or malicious ownership claims.

## Protecting storage

1. Place a sign directly on a chest or barrel, either attached to the side or placed on top.
2. Put this on the first line:

```text
[fs]
```

3. Save the sign.

The sign automatically changes to:

```text
[protected]
PlayerName
```

## Lock rules

- Only the lock owner can normally open the protected chest or barrel.
- No one can break the protected chest or barrel while its protection sign exists, including the owner.
- The owner unlocks the container by breaking the `[protected]` sign.
- Other players cannot break the protection sign.
- Only the owner can edit the protection sign.
- Owner edits preserve `[protected]` on line 1 and the owner's name on line 2.
- Hoppers cannot move items into or out of protected storage.
- Explosions and pistons cannot destroy or move protected containers or signs.
- Double chests are protected as one lock.

## Administrator Master Key

Give yourself a Master Key with:

```text
/fs give masterkey
```

Give one to another online administrator with:

```text
/fs give masterkey PlayerName
```

The Master Key is a custom, unbreakable wooden axe marked internally with plugin persistent data. A normal wooden axe—even one renamed to match—does not work as a Master Key.

While an authorized administrator holds the Master Key in their main hand:

- They can open any protected chest or barrel.
- They can break any `[protected]` sign, immediately removing that lock.
- Their override removal is recorded in the server console with the administrator, owner, and sign location.
- They still cannot directly break a protected chest or barrel. The protection sign must be removed first.

This allows staff to correct a situation where a player has protected storage that belongs to someone else without broadly disabling storage protection.

## Permissions

```text
fragstealers.masterkey.give
fragstealers.masterkey.use
```

Both permissions default to server operators.

## Building

Requires Java 25+.

```bash
gradle build
```

The compiled plugin jar will be in:

```text
build/libs/FragStealers-1.1.0.jar
```

Place that jar into the server's `plugins` folder and restart the server.

## Paper API

This project uses:

```kotlin
compileOnly("io.papermc.paper:paper-api:26.2.build.+")
```

That resolves the newest available Paper `26.2` API build when Gradle builds the project.
