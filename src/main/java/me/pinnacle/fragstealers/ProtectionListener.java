package me.pinnacle.fragstealers;

import io.papermc.paper.event.player.PlayerOpenSignEvent;
import me.pinnacle.fragstealers.data.ChestLock;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.shop.ItemUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProtectionListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    private final FragStealers plugin;
    private final Map<UUID, MasterLockSession> masterLockViews = new HashMap<>();

    public ProtectionListener(FragStealers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        ChestLock existingLock = plugin.locks().bySign(signBlock).orElse(null);
        if (existingLock != null) {
            if (!existingLock.isOwner(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(plugin.error("Only " + existingLock.ownerName() + " can edit this protection sign."));
                return;
            }
            event.line(0, Component.text("[protected]"));
            event.line(1, Component.text(existingLock.ownerName()));
            player.sendMessage(plugin.success("Protection sign updated."));
            return;
        }
        if (plugin.shops().bySign(signBlock) != null || plugin.mailboxes().bySign(signBlock) != null) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("Shop and mailbox signs cannot be edited. Break the sign to remove it."));
            return;
        }

        String tag = normalize(plain(event.line(0)));
        if (!tag.equals("fs") && !tag.equals("fs shop") && !tag.equals("fs mail")) {
            return;
        }

        Block container = plugin.resolver().findAttachedContainer(signBlock).orElse(null);
        if (container == null) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("The sign must be attached to a chest or barrel."));
            return;
        }
        Set<Block> containers = plugin.resolver().connectedBlocks(container);
        if (containers.isEmpty() || containers.stream().anyMatch(plugin::anyContainerProtected)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("That container is already registered with FragStealers."));
            return;
        }
        Inventory inventory = plugin.resolver().inventory(container);
        if (!plugin.resolver().isEmpty(inventory)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("The container must be completely empty before creating an FS container."));
            return;
        }

        switch (tag) {
            case "fs" -> createLock(event, player, signBlock, containers);
            case "fs shop" -> createShop(event, player, signBlock, containers);
            case "fs mail" -> createMailbox(event, player, signBlock, containers);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onOpenSign(PlayerOpenSignEvent event) {
        Block block = event.getSign().getBlock();
        ChestLock lock = plugin.locks().bySign(block).orElse(null);
        if (lock != null) {
            if (!lock.isOwner(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(plugin.error("Only " + lock.ownerName() + " can edit this protection sign."));
            }
            return;
        }
        if (plugin.shops().bySign(block) != null || plugin.mailboxes().bySign(block) != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.error("This FragStealers sign cannot be edited."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onContainerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        ChestLock lock = plugin.locks().byContainer(block).orElse(null);
        if (lock != null) {
            if (lock.isOwner(event.getPlayer().getUniqueId()) || plugin.masterKeys().canUse(event.getPlayer())) return;
            denyInteraction(event);
            event.getPlayer().sendMessage(plugin.error("This container is protected by " + lock.ownerName() + "."));
            return;
        }
        ShopData shop = plugin.shops().byContainer(block);
        if (shop != null) {
            denyInteraction(event);
            event.getPlayer().sendMessage(plugin.error("Use the shop sign to interact with this store."));
            return;
        }
        MailboxData mailbox = plugin.mailboxes().byContainer(block);
        if (mailbox != null) {
            denyInteraction(event);
            event.getPlayer().sendMessage(plugin.error("Use the mailbox sign to deposit or collect mail."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        ChestLock lock = lockForInventory(event.getInventory());
        if (lock == null) return;
        if (lock.isOwner(player.getUniqueId())) {
            masterLockViews.remove(player.getUniqueId());
            return;
        }
        if (!plugin.masterKeys().canUse(player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("This container is protected by " + lock.ownerName() + "."));
            return;
        }
        masterLockViews.put(player.getUniqueId(), new MasterLockSession(lock.signKey(), cloneContents(event.getInventory().getContents())));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MasterLockSession session = masterLockViews.get(player.getUniqueId());
        if (session == null) return;
        ChestLock lock = lockForInventory(event.getView().getTopInventory());
        if (lock == null || !lock.signKey().equals(session.signKey())) {
            masterLockViews.remove(player.getUniqueId());
            return;
        }
        if (!plugin.masterKeys().canUse(player)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            player.sendMessage(plugin.error("Keep the Master Key in your main hand while accessing this container."));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.masterKeys().canUse(player)) player.closeInventory();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MasterLockSession session = masterLockViews.get(player.getUniqueId());
        if (session == null) return;
        ChestLock lock = lockForInventory(event.getView().getTopInventory());
        if (lock == null || !lock.signKey().equals(session.signKey())) {
            masterLockViews.remove(player.getUniqueId());
            return;
        }
        if (!plugin.masterKeys().canUse(player)) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
            player.sendMessage(plugin.error("Keep the Master Key in your main hand while accessing this container."));
        }
    }

    @EventHandler
    public void onLockInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        MasterLockSession session = masterLockViews.remove(player.getUniqueId());
        if (session == null) return;
        ChestLock lock = plugin.locks().bySign(session.signKey()).orElse(null);
        if (lock == null) return;
        String details = ItemUtil.summarizeDifference(session.before(), event.getInventory().getContents());
        if (!details.equals("No items removed")) {
            plugin.audit().log(player, "WITHDREW_LOCK_ITEMS", ProtectionType.LOCK, lock.ownerUuid(), lock.ownerName(), lock.signKey(), details);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        masterLockViews.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        ChestLock lockSign = plugin.locks().bySign(block).orElse(null);
        if (lockSign != null) {
            if (!lockSign.isOwner(player.getUniqueId()) && !plugin.masterKeys().canUse(player)) {
                event.setCancelled(true);
                player.sendMessage(plugin.error("Only " + lockSign.ownerName() + " can break this protection sign."));
                return;
            }
            plugin.locks().remove(lockSign);
            if (!lockSign.isOwner(player.getUniqueId())) {
                plugin.audit().log(player, "REMOVED_LOCK", ProtectionType.LOCK, lockSign.ownerUuid(), lockSign.ownerName(), lockSign.signKey(), "Master Key override");
            }
            player.sendMessage(plugin.success("Container protection removed."));
            return;
        }

        ShopData shopSign = plugin.shops().bySign(block);
        if (shopSign != null) {
            if (!shopSign.isOwner(player.getUniqueId()) && !plugin.masterKeys().canUse(player)) {
                event.setCancelled(true);
                player.sendMessage(plugin.error("Only the shop owner or a Master Key administrator can remove this sign."));
                return;
            }
            plugin.shopMenus().closeViewers(shopSign);
            long payments = shopSign.earnings();
            if (shopSign.isConfigured() && payments > 0) {
                ItemUtil.giveOrDrop(player, shopSign.priceMaterial(), payments);
                shopSign.removeEarnings(payments);
            }
            plugin.shops().remove(shopSign);
            if (!shopSign.isOwner(player.getUniqueId())) {
                plugin.audit().log(player, "REMOVED_SHOP", ProtectionType.SHOP, shopSign.ownerUuid(), shopSign.ownerName(), shopSign.signKey(),
                    "Payments returned to administrator: " + payments);
            }
            player.sendMessage(plugin.success("Shop removed. Stored payments were returned to you."));
            return;
        }

        MailboxData mailboxSign = plugin.mailboxes().bySign(block);
        if (mailboxSign != null) {
            if (!mailboxSign.isOwner(player.getUniqueId()) && !plugin.masterKeys().canUse(player)) {
                event.setCancelled(true);
                player.sendMessage(plugin.error("Only the mailbox owner or a Master Key administrator can remove this sign."));
                return;
            }
            plugin.mailboxMenus().closeViewers(mailboxSign.signKey());
            List<ItemStack> mail = new ArrayList<>();
            for (ItemStack item : mailboxSign.copyContents()) if (!ItemUtil.isEmpty(item)) mail.add(item);
            mailboxSign.clear();
            ItemUtil.giveOrDrop(player, mail);
            plugin.mailboxes().remove(mailboxSign);
            if (!mailboxSign.isOwner(player.getUniqueId())) {
                plugin.audit().log(player, "REMOVED_MAILBOX", ProtectionType.MAILBOX, mailboxSign.ownerUuid(), mailboxSign.ownerName(), mailboxSign.signKey(),
                    "Returned " + mail.size() + " mail stack(s) to administrator");
            }
            player.sendMessage(plugin.success("Mailbox removed. Stored mail was returned to you."));
            return;
        }

        if (plugin.anyContainerProtected(block)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("Break the FragStealers sign first to unlock this container."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placed = event.getBlockPlaced();
        if (placed.getType() != Material.CHEST && placed.getType() != Material.TRAPPED_CHEST) return;

        Set<ProtectedRef> adjacent = new LinkedHashSet<>();
        for (BlockFace face : HORIZONTAL) {
            Block block = placed.getRelative(face);
            if (block.getType() != placed.getType()) continue;
            ProtectedRef ref = ref(block);
            if (ref != null) adjacent.add(ref);
        }
        if (adjacent.isEmpty()) return;
        if (adjacent.size() != 1) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.error("This chest cannot connect multiple FragStealers containers."));
            return;
        }
        ProtectedRef ref = adjacent.iterator().next();
        if (!plugin.canManage(event.getPlayer(), ref.ownerUuid())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.error("You cannot attach a chest to " + ref.ownerName() + "'s protected container."));
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(ref));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        boolean sourceShop = plugin.shops().inventoryBelongsToAny(event.getSource());
        boolean destinationShop = plugin.shops().inventoryBelongsToAny(event.getDestination());
        boolean sourceMail = plugin.mailboxes().inventoryBelongsToAny(event.getSource());
        boolean destinationMail = plugin.mailboxes().inventoryBelongsToAny(event.getDestination());
        if (sourceShop || destinationShop || sourceMail || destinationMail) {
            event.setCancelled(true);
            return;
        }
        boolean sourceLock = plugin.resolver().inventoryTouches(event.getSource(), block -> plugin.locks().byContainer(block).isPresent());
        boolean destinationLock = plugin.resolver().inventoryTouches(event.getDestination(), block -> plugin.locks().byContainer(block).isPresent());
        if ((sourceLock && !plugin.hopperTakeEnabled()) || (destinationLock && !plugin.hopperPutEnabled())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (plugin.anyProtectedBlock(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(plugin::anyProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(plugin::anyProtectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(plugin::anyProtectedBlock)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(plugin::anyProtectedBlock)) event.setCancelled(true);
    }

    private void createLock(SignChangeEvent event, Player player, Block sign, Set<Block> containers) {
        if (!player.hasPermission("fragstealers.lock.create")) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("You do not have permission to create locks."));
            return;
        }
        if (!plugin.locks().create(containers, sign, player)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("Could not create this protection."));
            return;
        }
        event.line(0, Component.text("[protected]"));
        event.line(1, Component.text(player.getName()));
        event.line(2, Component.empty());
        event.line(3, Component.empty());
        player.sendMessage(plugin.success("Container protected."));
    }

    private void createShop(SignChangeEvent event, Player player, Block sign, Set<Block> containers) {
        if (!plugin.shopsEnabled()) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("FragStealers shops are currently disabled."));
            return;
        }
        if (!player.hasPermission("fragstealers.shop.create")) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("You do not have permission to create shops."));
            return;
        }
        Set<BlockKey> keys = new LinkedHashSet<>();
        for (Block block : containers) keys.add(BlockKey.from(block));
        ShopData shop = new ShopData(BlockKey.from(sign), keys, player.getUniqueId(), player.getName(), null, 0, null, 0, 0L);
        plugin.shops().add(shop);
        event.line(0, Component.text("[shop]"));
        event.line(1, Component.text(player.getName()));
        event.line(2, Component.text("Not configured"));
        event.line(3, Component.text("Right-click setup"));
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.shopMenus().updateSign(shop));
        player.sendMessage(plugin.success("Shop created. Right-click the sign to configure it."));
    }

    private void createMailbox(SignChangeEvent event, Player player, Block sign, Set<Block> containers) {
        if (!plugin.mailEnabled()) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("FragStealers mailboxes are currently disabled."));
            return;
        }
        if (!player.hasPermission("fragstealers.mail.create")) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("You do not have permission to create mailboxes."));
            return;
        }

        UUID ownerUuid = player.getUniqueId();
        String ownerName = player.getName();
        boolean delegated = false;
        String requestedOwner = plain(event.line(1)).trim();

        if (!requestedOwner.isEmpty() && !requestedOwner.equalsIgnoreCase(player.getName())) {
            if (!plugin.masterKeys().canUseInEitherHand(player)) {
                event.setCancelled(true);
                player.sendMessage(plugin.error("Only an authorized administrator holding a Master Key in either hand can create a mailbox for another player."));
                return;
            }

            OfflinePlayer target = plugin.getServer().getPlayerExact(requestedOwner);
            if (target == null) {
                target = plugin.getServer().getOfflinePlayerIfCached(requestedOwner);
            }
            if (target == null || target.getName() == null) {
                event.setCancelled(true);
                player.sendMessage(plugin.error("Player '" + requestedOwner + "' is not known to this server. Check the spelling and make sure they have joined before."));
                return;
            }

            ownerUuid = target.getUniqueId();
            ownerName = target.getName();
            delegated = true;
        }

        Set<BlockKey> keys = new LinkedHashSet<>();
        for (Block block : containers) keys.add(BlockKey.from(block));
        MailboxData mailbox = new MailboxData(BlockKey.from(sign), keys, ownerUuid, ownerName);
        plugin.mailboxes().add(mailbox);
        event.line(0, Component.text("[mail]"));
        event.line(1, Component.text(ownerName));
        event.line(2, Component.empty());
        event.line(3, Component.empty());
        plugin.getServer().getScheduler().runTask(plugin, () -> updateMailboxSign(mailbox));

        if (delegated) {
            plugin.audit().log(player, "CREATED_MAILBOX_FOR_PLAYER", ProtectionType.MAILBOX,
                ownerUuid, ownerName, mailbox.signKey(), "Created a mailbox on behalf of " + ownerName + ".");
            player.sendMessage(plugin.success("Mailbox created for " + ownerName + "."));
        } else {
            player.sendMessage(plugin.success("Mailbox created."));
        }
    }

    private void updateMailboxSign(MailboxData mailbox) {
        Block block = mailbox.signKey().block();
        if (block == null || !(block.getState() instanceof Sign sign)) return;
        SignSide side = sign.getSide(Side.FRONT);
        side.line(0, Component.text("[mail]", NamedTextColor.GOLD));
        side.line(1, Component.text(mailbox.ownerName(), NamedTextColor.WHITE));
        side.line(2, Component.empty());
        side.line(3, Component.empty());
        sign.setWaxed(true);
        sign.update(true, false);
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = ItemUtil.isEmpty(contents[i]) ? null : contents[i].clone();
        }
        return copy;
    }

    private ChestLock lockForInventory(Inventory inventory) {
        for (Block block : plugin.resolver().inventoryBlocks(inventory)) {
            ChestLock lock = plugin.locks().byContainer(block).orElse(null);
            if (lock != null) return lock;
        }
        return null;
    }

    private void denyInteraction(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }

    private ProtectedRef ref(Block block) {
        ChestLock lock = plugin.locks().byContainer(block).orElse(null);
        if (lock != null) return new ProtectedRef(ProtectionType.LOCK, lock, lock.ownerUuid(), lock.ownerName());
        ShopData shop = plugin.shops().byContainer(block);
        if (shop != null) return new ProtectedRef(ProtectionType.SHOP, shop, shop.ownerUuid(), shop.ownerName());
        MailboxData mailbox = plugin.mailboxes().byContainer(block);
        if (mailbox != null) return new ProtectedRef(ProtectionType.MAILBOX, mailbox, mailbox.ownerUuid(), mailbox.ownerName());
        return null;
    }

    private void refresh(ProtectedRef ref) {
        switch (ref.type()) {
            case LOCK -> plugin.locks().refreshContainers((ChestLock) ref.record());
            case SHOP -> plugin.shops().refreshContainers((ShopData) ref.record());
            case MAILBOX -> plugin.mailboxes().refreshContainers((MailboxData) ref.record());
        }
    }

    private String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    private String normalize(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase();
        if (text.startsWith("[") && text.endsWith("]") && text.length() > 2) {
            text = text.substring(1, text.length() - 1).trim();
        }
        return text.replaceAll("\\s+", " ");
    }

    private record MasterLockSession(BlockKey signKey, ItemStack[] before) {
    }

    private record ProtectedRef(ProtectionType type, Object record, UUID ownerUuid, String ownerName) {
        @Override
        public boolean equals(Object object) {
            return object instanceof ProtectedRef other && record == other.record;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(record);
        }
    }
}
