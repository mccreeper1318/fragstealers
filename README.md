# FragStealers 26.2-6

FragStealers is a Paper 26.2 plugin that combines secure container locks, protected player shops, virtual mailboxes, and an administrator recovery tool.

## Requirements

- Paper 26.2
- Java 25

## Build

```bash
./gradlew clean build
```

The compiled plugin is created in `build/libs/FragStealers-26.2-6.jar`.

## Ordinary storage locks

1. Attach a sign to an empty chest, trapped chest, double chest, or barrel.
2. Enter `[fs]` on the first line.
3. The sign becomes `[protected]` with the owner's name.

Only the owner can open the container or edit/remove the sign. Nobody can break the protected container until the sign is removed. Authorized Master Key holders can open it and remove its sign.

Hopper behavior for ordinary locks is controlled in `config.yml`:

```yaml
hopper-take-item: false
hopper-put-item: true
```

## Shops

1. Attach a sign to an empty supported container.
2. Enter `[fs shop]` on the first line.
3. Right-click the sign and choose **Setup Shop**.
4. Select the sale item and quantity, then the payment item and quantity.

The material selector includes an anvil search. Search accepts partial friendly names such as `oak log` and Minecraft-style names such as `oak_log`. FragStealers actively refreshes the synthetic anvil result while it is open, so the result compass remains available even when vanilla anvil processing does not fire. The result button stores the exact entered query so the search remains consistent when confirmed.

Shop signs retain colored text for readability: the sale item is green and the payment item is red. The text does not glow.

Shop stock stays in the physical container. Payments are stored by the plugin in `shops.yml`. Owners and authorized Master Key holders receive the full management interface. Removing the shop sign gives stored payments to the person who removed it; items that do not fit are dropped safely.

Shops can be disabled with:

```yaml
fs-shops: false
```

Existing shops remain saved. Purchases and new shop creation stop, while owners and administrators can still manage, collect, and dismantle them.

## Mailboxes

To create your own mailbox:

1. Attach a sign to an empty supported container.
2. Enter `[fs mail]` on the first line.
3. Leave the second line blank.
4. The sign becomes `[mail]` with your name.

An authorized administrator can create a mailbox for another player while holding a Master Key in either hand:

```text
Line 1: [fs mail]
Line 2: PlayerName
```

The target must already be known to the server. The mailbox is stored under the target player's UUID exactly as though that player had created it, and delegated creation is recorded in `audit-log.yml`.

Clicking another player's mailbox offers deposit access only. Occupied mailbox slots appear as locked red panes, while empty slots accept items. Owners and authorized Master Key holders can deposit or open pickup mode to view the real virtual inventory.

Mailbox contents are stored in `mailboxes.yml`, not in the physical container. A barrel or single chest has 27 slots; a double chest has 54. Owners receive **You've Got Mail!** when an online deposit completes and once when joining while mail remains waiting.

Mailboxes can be disabled with:

```yaml
fs-mail: false
```

Existing mail remains saved and can still be collected or recovered.

## Master Key

```text
/fs give masterkey
/fs give masterkey <player>
```

The Master Key is an unbreakable custom wooden axe. It works only for players with `fragstealers.masterkey.use`. It must be in the main hand when opening or managing protected storage and removing signs; delegated mailbox creation accepts it in either hand so the sign can be placed normally. It cannot directly break a still-protected container.

## Other commands

```text
/fs reload
```

Reloads `config.yml` without deleting or recreating saved protections. Missing settings from newer versions are merged into existing configurations without replacing values you already changed.

## Automated builds and releases

The repository includes two GitHub Actions workflows:

- `.github/workflows/build.yml` compiles and verifies the plugin on pushes to `main`, pushes to `agent/**` branches, pull requests, and manual runs. The verified JAR is saved as a workflow artifact.
- `.github/workflows/release.yml` runs when a GitHub Release is published. It checks out the release tag, builds with Java 25, verifies the packaged `plugin.yml` and `config.yml`, and attaches the plugin JAR plus its SHA-256 checksum to the release.

Before publishing a release, make sure the version in `build.gradle.kts` matches the normalized release tag exactly.

Stable tags may use any of these styles:

```text
26.2-6
v26.2-6
v.26.2-6
```

Prerelease tags may add a dot-separated suffix:

```text
26.2-6-beta.1
26.2-6-alpha.2
26.2-6-rc.1
v26.2-6-beta.1
```

A prerelease tag must be published as a GitHub **prerelease**. A stable tag must be published as a normal GitHub release. The workflow stops if the tag type and GitHub release type do not match.

For example, when `build.gradle.kts` contains:

```kotlin
version = "26.2-6-beta.1"
```

publish a GitHub prerelease tagged `26.2-6-beta.1`. The workflow uploads:

```text
FragStealers-26.2-6-beta.1.jar
FragStealers-26.2-6-beta.1.jar.sha256
```

The release workflow can also be run manually from the Actions tab for an existing GitHub Release tag.
