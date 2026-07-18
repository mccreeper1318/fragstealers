# FragStealers Changelog

## 26.2-4

- Adopted Minecraft-aligned versioning: `<Minecraft version>-<plugin update number>`.
- Pinned the project to Paper API `26.2.build.62-beta` and Java 25.
- Added independently configurable hopper insertion and extraction for ordinary `[fs]` locks.
- Added hopper minecart support to the same lock settings.
- Integrated PinnacleShop functionality directly into FragStealers.
- Shops are now created with `[fs shop]`.
- Added an anvil-based material search that supports partial friendly and namespaced item names.
- Added colored, glowing shop signs for easier sale-item and price-item identification.
- Added full Master Key access to shop owner controls and shop-sign removal.
- Added payment return when a shop sign is removed; overflow drops safely at the breaker.
- Added `shops.yml` for shop records and stored payments.
- Added configurable shop enablement with `fs-shops`.
- Added virtual mailboxes created with `[fs mail]`.
- Added secure deposit mode with red placeholder panes for occupied slots.
- Added owner and Master Key pickup access with exact-slot persistence.
- Added online deposit notifications and join reminders when mail remains waiting.
- Added safe single-chest to double-chest expansion for locks, shops, and mailboxes.
- Added `mailboxes.yml` for mailbox records and virtual inventory contents.
- Added configurable mailbox enablement with `fs-mail`.
- Required all containers to be empty before creating any FragStealers lock, shop, or mailbox.
- Prevented a container from being registered as more than one protection type.
- Added `/fs reload` and dedicated creation/admin permissions.
- Added atomic YAML writes to reduce corruption risk.
- Added `audit-log.yml` for Master Key administrative actions with automatic 30-day retention.
- Added upgrade-safe config merging so new settings are added without replacing existing values.
- Hardened Master Key GUI access, inventory transitions, and mailbox/shop concurrency checks.
- Restricted FS creation signs to containers directly attached behind wall signs or directly below standing signs.

## 1.1.1

- Corrected the Adventure `TextDecoration` import so the project builds.

## 1.1.0

- Added the Master Key admin recovery tool.
- Added `/fs give masterkey [player]`.
- Allowed authorized Master Key holders to open protected storage and remove protection signs.

## 1.0.1

- Added barrel protection.
- Restricted editing of protected signs to their owners.

## 1.0.0

- Initial release with sign-based chest locks, double-chest protection, and common bypass prevention.
