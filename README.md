# FragStealers 26.2-5

FragStealers is a Paper 26.2 plugin that combines secure container locks, protected player shops, virtual mailboxes, and an administrator recovery tool.

## Requirements

- Paper 26.2
- Java 25

## Build

```bash
./gradlew clean build
```

The compiled plugin is created in `build/libs/FragStealers-26.2-5.jar`.

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

The material selector includes an anvil search. Search accepts partial friendly names such as `oak log` and Minecraft-style names such as `oak_log`. The result button stores the exact entered query so the search remains consistent when confirmed.

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

## Permissions

```text
fragstealers.lock.create       default: true
fragstealers.shop.create       default: true
fragstealers.mail.create       default: true
fragstealers.masterkey.give    default: op
fragstealers.masterkey.use     default: op
fragstealers.admin.reload      default: op
```

## Data files

```text
plugins/FragStealers/
├── config.yml
├── locks.yml
├── shops.yml
├── mailboxes.yml
└── audit-log.yml
```

Master Key administrative mailbox creation, removals, and item withdrawals are recorded in `audit-log.yml`. Entries older than 30 days are purged automatically.
