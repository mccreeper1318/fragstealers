# FragStealers Changelog

## 26.2-6

### Added

- Added UUID-based trusted-player access stored in `trusted-players.yml`.
- Added `/fs trust <player> [access|manage]` for granting or updating access on the protection the owner is targeting.
- Added `/fs untrust <player>` for removing access from the targeted protection.
- Added `/fs trusted` for listing trusted players and their access levels.
- Added the `fragstealers.trust.manage` permission with a default of `true`.
- Added `access` and `manage` trust levels.
- Added trusted access to ordinary protected locks.
- Added access-level shop restocking without stock withdrawal or payment collection.
- Added manage-level shop stock withdrawal, restocking, payment collection, and unconfigured-shop setup.
- Added access-level mailbox collection through a restricted pickup view.
- Added manage-level mailbox-content controls.
- Added automatic trust-entry cleanup when a lock, shop, or mailbox protection is removed.
- Added a dedicated shop-search refresh listener that reads the active anvil rename text while the search is open.
- Added GitHub Actions validation builds for `main`, `agent/**` branches, pull requests, and manual runs.
- Added automated GitHub Release packaging with the compiled JAR and a SHA-256 checksum.
- Added stable and prerelease tag support, including tags such as `26.2-6-beta.1`, `26.2-6-alpha.2`, and `26.2-6-rc.1`.
- Added push-triggered prerelease publishing from `agent/**` branches through a `[prerelease VERSION]` commit-message marker.

### Changed

- Changed trust commands to operate on the FragStealers sign or protected container the owner is looking at within six blocks.
- Changed shop management menus to distinguish owner, manage-level, access-level, customer, and Master Key access.
- Changed access-level trusted shop sessions to accept only the configured sale material.
- Changed access-level trusted mailbox pickup sessions to allow collection while blocking insertion and rearrangement.
- Changed active lock, shop, and mailbox sessions to recheck authorization so revoked trust is enforced without waiting for a reconnect.
- Changed shop-search confirmation to use the exact query stored on the result compass instead of rereading a potentially stale anvil field.
- Changed the anvil search input item to use a visually blank name with instructions in its lore.
- Changed release validation to compare prerelease tags against the base version in `build.gradle.kts`.
- Changed prerelease builds to apply the full prerelease version only inside the Actions workspace, leaving the source version at its stable base value.
- Changed release validation to verify the JAR filename, embedded `plugin.yml` version, and required resource files before upload.

### Fixed

- Fixed issue #1 where the shop anvil search could open without producing a clickable result.
- Fixed synthetic anvil searches not reliably firing `PrepareAnvilEvent` by actively restoring the result compass when the rename query changes or vanilla clears the result slot.
- Fixed search results becoming inconsistent when the clicked compass and current rename text briefly differed.
- Fixed prerelease builds failing when a tag such as `26.2-6-beta.1` was used while the project source remained at `26.2-6`.
- Fixed hotbar move-and-readd actions not being included in protected shop and mailbox inventory safeguards.

### Security

- Restricted trust-list changes to the protection owner; Master Key administrators cannot alter another player's trust list.
- Prevented trusted players from editing or removing protection signs.
- Prevented trusted players from breaking protected containers.
- Prevented trusted players from attaching an additional chest half to another player's protection.
- Prevented access-level shop users from removing stock through clicks, shift-clicks, number keys, offhand swaps, drops, cloning, or collect-to-cursor actions.
- Prevented access-level mailbox users from inserting or rearranging items through clicks, shift-clicks, hotbar swaps, offhand swaps, or inventory drags.
- Preserved Master Key-in-hand validation for active administrative inventory sessions.
- Continued rejecting Master Keys from shop stock and mailbox contents.

### Documentation

- Rewrote the README as a server-owner and player usage guide.
- Added installation, configuration, commands, permissions, data-file, trust-level, shop, mailbox, Master Key, and audit-log documentation.
- Removed repository-development and automated-build instructions from the public-facing README.
- Reorganized the changelog into Added, Changed, Fixed, Security, Documentation, and Build and Release categories.

### Build and Release

- Builds use Java 25 and the pinned Paper API `26.2.build.62-beta`.
- Development builds verify that exactly one FragStealers JAR is produced and that it contains `plugin.yml` and `config.yml`.
- Release builds generate `FragStealers-<version>.jar` and `FragStealers-<version>.jar.sha256`.
- Release automation accepts `26.2-6`, `v26.2-6`, `v.26.2-6`, and supported prerelease equivalents.
- Release automation verifies that prerelease-suffixed tags are published as GitHub prereleases and stable tags are published as normal releases.

## 26.2-5

### Added

- Added delegated mailbox creation for authorized administrators holding a Master Key in either hand.
- Added support for `[fs mail]` on line 1 and a known player's name on line 2 to create a mailbox under that player's UUID.
- Added an audit-log entry whenever an administrator creates a mailbox for another player.

### Changed

- Changed the shop anvil search input to start visually blank.
- Changed search results to store the exact query on the result item.
- Changed search confirmation so it no longer depends on a potentially stale rename field.
- Changed shop signs to retain green sale-item text and red payment-item text without glowing.
- Changed plugin startup to refresh existing shop signs and remove their previous glow effect.

### Fixed

- Fixed anvil search results being cleared by configuring repair limits before setting the custom result.

## 26.2-4

### Added

- Added Minecraft-aligned versioning using `<Minecraft version>-<plugin update number>`.
- Added independently configurable hopper insertion and extraction for ordinary `[fs]` locks.
- Added hopper minecart support to the ordinary-lock hopper settings.
- Integrated PinnacleShop functionality directly into FragStealers.
- Added shops created with `[fs shop]`.
- Added an anvil-based material search supporting partial friendly names and Minecraft-style names.
- Added colored shop signs with green sale-item text and red payment-item text.
- Added full Master Key access to shop owner controls and shop-sign removal.
- Added payment return when a shop sign is removed, with overflow safely dropped.
- Added `shops.yml` for shop records and stored payments.
- Added configurable shop enablement with `fs-shops`.
- Added virtual mailboxes created with `[fs mail]`.
- Added secure mailbox deposit mode with red placeholder panes for occupied slots.
- Added owner and Master Key mailbox pickup access with exact-slot persistence.
- Added online deposit notifications and join reminders while mail remains waiting.
- Added safe single-chest to double-chest expansion for locks, shops, and mailboxes.
- Added `mailboxes.yml` for mailbox records and virtual inventory contents.
- Added configurable mailbox enablement with `fs-mail`.
- Added `/fs reload` and dedicated creation and administration permissions.
- Added atomic YAML writes to reduce corruption risk.
- Added `audit-log.yml` for Master Key administrative actions with automatic 30-day retention.
- Added upgrade-safe configuration merging.

### Changed

- Changed the project to Paper API `26.2.build.62-beta` and Java 25.
- Changed protection creation to require an empty container.
- Changed registration rules so a container can only be one FragStealers type.
- Changed creation-sign validation so wall signs must be directly attached and standing signs must be directly above the container.

### Fixed

- Fixed Java compilation on Paper 26.2 by replacing ambiguous scheduler method references with explicit `Runnable` lambdas.

### Security

- Hardened Master Key GUI access and inventory transitions.
- Added mailbox and shop concurrency checks.
- Prevented Master Keys from being used as shop stock or mailed as ordinary items.

## 1.1.1

### Fixed

- Fixed the Adventure `TextDecoration` import used by Master Key formatting.
- Changed the import from `net.kyori.adventure.text.TextDecoration` to `net.kyori.adventure.text.format.TextDecoration`.
- Fixed the `compileJava` failure introduced in 1.1.0.

## 1.1.0

### Added

- Added the custom unbreakable Master Key wooden axe.
- Added `/fs give masterkey` for giving the command sender a Master Key.
- Added `/fs give masterkey <player>` for giving an online administrator a Master Key.
- Added Master Key access to protected storage.
- Added Master Key removal of protected signs.
- Added operator-default permissions for giving and using Master Keys.
- Added console logging for Master Key overrides.

### Security

- Prevented ordinary or renamed wooden axes from acting as Master Keys.
- Prevented Master Keys from directly breaking protected containers; the sign must be removed first.

## 1.0.1

### Added

- Added barrel protection.

### Security

- Restricted editing of protected signs to their owners.

## 1.0.0

### Added

- Added sign-based chest protection.
- Added double-chest protection.
- Added owner-only protected-container access.
- Added common interaction and breaking bypass prevention.
