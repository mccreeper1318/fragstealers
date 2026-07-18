package me.pinnacle.fragstealers.shop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ItemUtil {
    private ItemUtil() {
    }

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    public static int count(Inventory inventory, Material material) {
        return count(inventory, material, item -> true);
    }

    public static int count(Inventory inventory, Material material, Predicate<ItemStack> eligible) {
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (!isEmpty(item) && item.getType() == material && eligible.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static int count(PlayerInventory inventory, Material material) {
        return count(inventory, material, item -> true);
    }

    public static int count(PlayerInventory inventory, Material material, Predicate<ItemStack> eligible) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (!isEmpty(item) && item.getType() == material && eligible.test(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    public static boolean contains(Inventory inventory, Predicate<ItemStack> predicate) {
        for (ItemStack item : inventory.getContents()) {
            if (!isEmpty(item) && predicate.test(item)) return true;
        }
        return false;
    }

    public static boolean containsOnlyEmptyOr(Inventory inventory, Material material) {
        for (ItemStack item : inventory.getContents()) {
            if (!isEmpty(item) && item.getType() != material) {
                return false;
            }
        }
        return true;
    }

    public static List<ItemStack> peek(Inventory inventory, Material material, int amount) {
        return peek(inventory, material, amount, item -> true);
    }

    public static List<ItemStack> peek(Inventory inventory, Material material, int amount, Predicate<ItemStack> eligible) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (ItemStack item : inventory.getContents()) {
            if (remaining <= 0) break;
            if (isEmpty(item) || item.getType() != material || !eligible.test(item)) continue;
            int taking = Math.min(remaining, item.getAmount());
            ItemStack clone = item.clone();
            clone.setAmount(taking);
            result.add(clone);
            remaining -= taking;
        }
        return remaining == 0 ? result : List.of();
    }

    public static List<ItemStack> take(Inventory inventory, Material material, int amount) {
        return take(inventory, material, amount, item -> true);
    }

    public static List<ItemStack> take(Inventory inventory, Material material, int amount, Predicate<ItemStack> eligible) {
        List<ItemStack> result = new ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSize() && remaining > 0; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item) || item.getType() != material || !eligible.test(item)) continue;
            int taking = Math.min(remaining, item.getAmount());
            ItemStack clone = item.clone();
            clone.setAmount(taking);
            result.add(clone);
            int left = item.getAmount() - taking;
            if (left <= 0) inventory.setItem(slot, null);
            else {
                item = item.clone();
                item.setAmount(left);
                inventory.setItem(slot, item);
            }
            remaining -= taking;
        }
        if (remaining > 0) {
            for (ItemStack item : result) inventory.addItem(item);
            return List.of();
        }
        return result;
    }

    public static void remove(PlayerInventory inventory, Material material, int amount) {
        remove(inventory, material, amount, item -> true);
    }

    public static void remove(PlayerInventory inventory, Material material, int amount, Predicate<ItemStack> eligible) {
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (isEmpty(item) || item.getType() != material || !eligible.test(item)) continue;
            int taking = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - taking;
            if (left <= 0) contents[slot] = null;
            else {
                ItemStack clone = item.clone();
                clone.setAmount(left);
                contents[slot] = clone;
            }
            remaining -= taking;
        }
        inventory.setStorageContents(contents);
    }

    public static boolean canFitAfterPayment(PlayerInventory inventory, Material payment, int amount, List<ItemStack> incoming) {
        return canFitAfterPayment(inventory, payment, amount, incoming, item -> true);
    }

    public static boolean canFitAfterPayment(PlayerInventory inventory, Material payment, int amount, List<ItemStack> incoming,
                                             Predicate<ItemStack> eligiblePayment) {
        ItemStack[] simulated = cloneContents(inventory.getStorageContents());
        if (!removeFrom(simulated, payment, amount, eligiblePayment)) return false;
        return canFit(simulated, incoming);
    }

    public static int fitAmount(PlayerInventory inventory, Material material, long requested) {
        if (requested <= 0) return 0;
        ItemStack template = new ItemStack(material);
        int max = template.getMaxStackSize();
        long room = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isEmpty(item)) room += max;
            else if (item.isSimilar(template)) room += max - item.getAmount();
            if (room >= requested) return (int) Math.min(requested, Integer.MAX_VALUE);
        }
        return (int) Math.min(room, Math.min(requested, Integer.MAX_VALUE));
    }

    public static List<ItemStack> split(Material material, long amount) {
        List<ItemStack> result = new ArrayList<>();
        int max = new ItemStack(material).getMaxStackSize();
        long remaining = amount;
        while (remaining > 0) {
            int size = (int) Math.min(max, remaining);
            result.add(new ItemStack(material, size));
            remaining -= size;
        }
        return result;
    }

    public static void giveOrDrop(Player player, List<ItemStack> items) {
        Location location = player.getLocation();
        for (ItemStack item : items) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (ItemStack remaining : overflow.values()) {
                player.getWorld().dropItemNaturally(location, remaining);
            }
        }
    }

    public static void giveOrDrop(Player player, Material material, long amount) {
        giveOrDrop(player, split(material, amount));
    }

    public static String summarizeDifference(ItemStack[] before, ItemStack[] after) {
        Map<String, Integer> beforeCounts = counts(before);
        Map<String, Integer> afterCounts = counts(after);
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : beforeCounts.entrySet()) {
            int difference = entry.getValue() - afterCounts.getOrDefault(entry.getKey(), 0);
            if (difference > 0) removed.add(entry.getKey() + " x" + difference);
        }
        return removed.isEmpty() ? "No items removed" : "Removed: " + String.join(", ", removed);
    }

    private static Map<String, Integer> counts(ItemStack[] contents) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (ItemStack item : contents) {
            if (!isEmpty(item)) result.merge(item.getType().name(), item.getAmount(), Integer::sum);
        }
        return result;
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) clone[i] = isEmpty(contents[i]) ? null : contents[i].clone();
        return clone;
    }

    private static boolean removeFrom(ItemStack[] contents, Material material, int amount, Predicate<ItemStack> eligible) {
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (isEmpty(item) || item.getType() != material || !eligible.test(item)) continue;
            int taking = Math.min(remaining, item.getAmount());
            int left = item.getAmount() - taking;
            if (left <= 0) contents[i] = null;
            else item.setAmount(left);
            remaining -= taking;
        }
        return remaining == 0;
    }

    private static boolean canFit(ItemStack[] contents, List<ItemStack> incoming) {
        ItemStack[] simulated = cloneContents(contents);
        for (ItemStack incomingItem : incoming) {
            int remaining = incomingItem.getAmount();
            int max = incomingItem.getMaxStackSize();
            for (ItemStack existing : simulated) {
                if (remaining <= 0) break;
                if (!isEmpty(existing) && existing.isSimilar(incomingItem) && existing.getAmount() < max) {
                    int add = Math.min(remaining, max - existing.getAmount());
                    existing.setAmount(existing.getAmount() + add);
                    remaining -= add;
                }
            }
            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                if (isEmpty(simulated[i])) {
                    int add = Math.min(remaining, max);
                    ItemStack clone = incomingItem.clone();
                    clone.setAmount(add);
                    simulated[i] = clone;
                    remaining -= add;
                }
            }
            if (remaining > 0) return false;
        }
        return true;
    }
}
