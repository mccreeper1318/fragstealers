package me.pinnacle.fragstealers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class FragStealers extends JavaPlugin implements Listener {
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private LockManager lockManager;
    private MasterKeyManager masterKeyManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        lockManager = new LockManager(this);
        lockManager.load();
        masterKeyManager = new MasterKeyManager(this);

        getServer().getPluginManager().registerEvents(this, this);

        PluginCommand fsCommand = getCommand("fragstealers");
        if (fsCommand == null) {
            getLogger().severe("The fragstealers command is missing from plugin.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        FragStealersCommand commandHandler = new FragStealersCommand(this, masterKeyManager);
        fsCommand.setExecutor(commandHandler);
        fsCommand.setTabCompleter(commandHandler);

        getLogger().info("FragStealers enabled.");
    }

    @Override
    public void onDisable() {
        if (lockManager != null) {
            lockManager.save();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        Block signBlock = event.getBlock();

        Optional<ChestLock> existingLock = lockManager.getBySign(signBlock);
        if (existingLock.isPresent()) {
            ChestLock lock = existingLock.get();
            if (!lock.ownerUuid().equals(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(error(message("sign-edit-denied", lock)));
                return;
            }

            // The owner may edit the sign, but the lock header stays intact.
            event.line(0, Component.text("[protected]"));
            event.line(1, Component.text(lock.ownerName()));
            player.sendMessage(success(message("sign-edited")));
            return;
        }

        String firstLine = plain(event.line(0)).trim();
        if (!firstLine.equalsIgnoreCase("[fs]")) {
            return;
        }

        Block containerBlock = findContainerForSign(signBlock).orElse(null);

        if (containerBlock == null) {
            event.setCancelled(true);
            player.sendMessage(error(message("sign-must-touch-container")));
            return;
        }

        Set<Block> containerBlocks = connectedContainerBlocks(containerBlock);
        if (containerBlocks.isEmpty() || containerBlocks.stream().anyMatch(block -> lockManager.getByChest(block).isPresent())) {
            event.setCancelled(true);
            player.sendMessage(error(message("already-protected")));
            return;
        }

        boolean created = lockManager.createLock(containerBlocks, signBlock, player);
        if (!created) {
            event.setCancelled(true);
            player.sendMessage(error(message("already-protected")));
            return;
        }

        event.line(0, Component.text("[protected]"));
        event.line(1, Component.text(player.getName()));
        event.line(2, Component.empty());
        event.line(3, Component.empty());

        player.sendMessage(success(message("lock-created")));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null || !isProtectableContainer(clicked)) {
            return;
        }

        Optional<ChestLock> lock = lockManager.getByChest(clicked);
        if (lock.isEmpty()) {
            return;
        }

        if (lock.get().ownerUuid().equals(event.getPlayer().getUniqueId())
            || masterKeyManager.canUse(event.getPlayer())) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.getPlayer().sendMessage(error(message("not-your-container", lock.get())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        Optional<ChestLock> signLock = lockManager.getBySign(block);
        if (signLock.isPresent()) {
            ChestLock lock = signLock.get();
            if (lock.ownerUuid().equals(player.getUniqueId())) {
                lockManager.remove(lock);
                player.sendMessage(success(message("unlocked")));
            } else if (masterKeyManager.canUse(player)) {
                lockManager.remove(lock);
                player.sendMessage(success(message("master-key-unlocked", lock)));
                getLogger().warning(player.getName()
                    + " used a FragStealers Master Key to remove "
                    + lock.ownerName()
                    + "'s protection at "
                    + lock.signKey().serialize()
                    + ".");
            } else {
                event.setCancelled(true);
                player.sendMessage(error(message("sign-break-denied", lock)));
            }
            return;
        }

        Optional<ChestLock> containerLock = lockManager.getByChest(block);
        if (containerLock.isPresent()) {
            event.setCancelled(true);
            ChestLock lock = containerLock.get();
            if (lock.ownerUuid().equals(player.getUniqueId())
                || masterKeyManager.canUse(player)) {
                player.sendMessage(error(message("break-sign-first")));
            } else {
                player.sendMessage(error(message("not-your-container", lock)));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (inventoryTouchesProtectedContainer(event.getSource()) || inventoryTouchesProtectedContainer(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(lockManager::isProtectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(lockManager::isProtectedBlock);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(lockManager::isProtectedBlock)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(lockManager::isProtectedBlock)) {
            event.setCancelled(true);
        }
    }

    private Optional<Block> findContainerForSign(Block signBlock) {
        BlockState state = signBlock.getState();
        if (!(state instanceof Sign)) {
            return Optional.empty();
        }

        BlockData data = signBlock.getBlockData();
        Set<Block> candidates = new LinkedHashSet<>();

        // Wall signs and wall hanging signs face away from the block they are attached to.
        if (data instanceof Directional directional) {
            candidates.add(signBlock.getRelative(directional.getFacing().getOppositeFace()));
        }

        // Standing signs can be placed directly on top of a chest or barrel.
        candidates.add(signBlock.getRelative(org.bukkit.block.BlockFace.DOWN));

        return candidates.stream().filter(this::isProtectableContainer).findFirst();
    }

    private Set<Block> connectedContainerBlocks(Block containerBlock) {
        Set<Block> blocks = new LinkedHashSet<>();
        BlockState state = containerBlock.getState();

        if (state instanceof Barrel) {
            blocks.add(containerBlock);
            return blocks;
        }

        if (!(state instanceof Chest chest)) {
            return blocks;
        }

        blocks.add(containerBlock);
        InventoryHolder holder = chest.getInventory().getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            addChestSide(blocks, doubleChest.getLeftSide());
            addChestSide(blocks, doubleChest.getRightSide());
        }

        return blocks;
    }

    private void addChestSide(Set<Block> blocks, InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            blocks.add(chest.getBlock());
        }
    }

    private boolean inventoryTouchesProtectedContainer(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();

        if (holder instanceof Barrel barrel) {
            return lockManager.getByChest(barrel.getBlock()).isPresent();
        }

        if (holder instanceof Chest chest) {
            return lockManager.getByChest(chest.getBlock()).isPresent();
        }

        if (holder instanceof DoubleChest doubleChest) {
            return holderTouchesProtectedContainer(doubleChest.getLeftSide())
                || holderTouchesProtectedContainer(doubleChest.getRightSide());
        }

        return false;
    }

    private boolean holderTouchesProtectedContainer(InventoryHolder holder) {
        if (holder instanceof Barrel barrel) {
            return lockManager.getByChest(barrel.getBlock()).isPresent();
        }

        return holder instanceof Chest chest && lockManager.getByChest(chest.getBlock()).isPresent();
    }

    private boolean isProtectableContainer(Block block) {
        BlockState state = block.getState();
        return state instanceof Chest || state instanceof Barrel;
    }

    private String plain(Component component) {
        if (component == null) {
            return "";
        }
        return PLAIN_TEXT.serialize(component);
    }

    String message(String key) {
        return getConfig().getString("messages." + key, key);
    }

    private String message(String key, ChestLock lock) {
        return message(key).replace("{owner}", lock.ownerName());
    }

    private Component success(String message) {
        return Component.text("FragStealers: ", NamedTextColor.GOLD)
            .append(Component.text(message, NamedTextColor.GREEN));
    }

    private Component error(String message) {
        return Component.text("FragStealers: ", NamedTextColor.GOLD)
            .append(Component.text(message, NamedTextColor.RED));
    }
}
