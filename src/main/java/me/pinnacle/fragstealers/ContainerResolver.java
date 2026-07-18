package me.pinnacle.fragstealers;

import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public final class ContainerResolver {
    public boolean isSupported(Block block) {
        if (block == null) {
            return false;
        }
        BlockState state = block.getState();
        return state instanceof Chest || state instanceof Barrel;
    }

    public Optional<Block> findAttachedContainer(Block signBlock) {
        if (!(signBlock.getState() instanceof Sign)) {
            return Optional.empty();
        }

        BlockData data = signBlock.getBlockData();
        if (data instanceof Directional directional) {
            Block attached = signBlock.getRelative(directional.getFacing().getOppositeFace());
            return isSupported(attached) ? Optional.of(attached) : Optional.empty();
        }

        Block below = signBlock.getRelative(BlockFace.DOWN);
        return isSupported(below) ? Optional.of(below) : Optional.empty();
    }

    public Set<Block> connectedBlocks(Block containerBlock) {
        Set<Block> blocks = new LinkedHashSet<>();
        if (containerBlock == null) {
            return blocks;
        }

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

    public Set<BlockKey> connectedKeys(Block containerBlock) {
        Set<BlockKey> keys = new LinkedHashSet<>();
        for (Block block : connectedBlocks(containerBlock)) {
            keys.add(BlockKey.from(block));
        }
        return keys;
    }

    public Inventory inventory(Block containerBlock) {
        if (containerBlock == null) {
            return null;
        }
        BlockState state = containerBlock.getState();
        if (state instanceof Chest chest) {
            return chest.getInventory();
        }
        if (state instanceof Barrel barrel) {
            return barrel.getInventory();
        }
        return null;
    }

    public Inventory inventory(Iterable<BlockKey> keys) {
        for (BlockKey key : keys) {
            Block block = key.block();
            Inventory inventory = inventory(block);
            if (inventory != null) {
                return inventory;
            }
        }
        return null;
    }

    public boolean isEmpty(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                return false;
            }
        }
        return true;
    }

    public Set<Block> inventoryBlocks(Inventory inventory) {
        Set<Block> blocks = new LinkedHashSet<>();
        if (inventory == null) return blocks;
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof Chest chest) {
            blocks.add(chest.getBlock());
        } else if (holder instanceof Barrel barrel) {
            blocks.add(barrel.getBlock());
        } else if (holder instanceof DoubleChest doubleChest) {
            addChestSide(blocks, doubleChest.getLeftSide());
            addChestSide(blocks, doubleChest.getRightSide());
        }
        return blocks;
    }

    public boolean inventoryTouches(Inventory inventory, Predicate<Block> predicate) {
        return inventoryBlocks(inventory).stream().anyMatch(predicate);
    }

    private void addChestSide(Set<Block> blocks, InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            blocks.add(chest.getBlock());
        }
    }

}
