package me.pinnacle.fragstealers.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ItemCatalog {
    private static final Set<String> BLOCKED = new HashSet<>(Set.of(
        "AIR", "CAVE_AIR", "VOID_AIR", "BARRIER", "BEDROCK", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK",
        "REPEATING_COMMAND_BLOCK", "COMMAND_BLOCK_MINECART", "DEBUG_STICK", "LIGHT", "STRUCTURE_BLOCK",
        "STRUCTURE_VOID", "JIGSAW", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME",
        "PLAYER_HEAD", "PLAYER_WALL_HEAD", "KNOWLEDGE_BOOK", "FARMLAND", "PETRIFIED_OAK_SLAB",
        "REINFORCED_DEEPSLATE"
    ));
    private static final List<Material> ITEMS = build();

    private ItemCatalog() {
    }

    public static List<Material> shopItems(String query) {
        if (query == null || query.isBlank()) {
            return ITEMS;
        }
        String normalized = normalize(query);
        return ITEMS.stream().filter(material -> normalize(material.name()).contains(normalized)
            || normalize(display(material)).contains(normalized)).toList();
    }

    public static boolean isAllowed(Material material) {
        if (material == null || !material.isItem() || material.isAir()) {
            return false;
        }
        String name = material.name();
        return !BLOCKED.contains(name) && !name.startsWith("LEGACY_");
    }

    public static int maxStackSize(Material material) {
        return Math.max(1, new ItemStack(material, 1).getMaxStackSize());
    }

    public static int[] quantities(Material material) {
        int max = maxStackSize(material);
        int[] amounts = new int[max];
        for (int i = 1; i <= max; i++) {
            amounts[i - 1] = i;
        }
        return amounts;
    }

    public static String display(Material material) {
        if (material == null) {
            return "Not Configured";
        }
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static List<Material> build() {
        List<Material> result = new ArrayList<>();
        for (Material material : Material.values()) {
            if (isAllowed(material)) {
                result.add(material);
            }
        }
        result.sort(Comparator.comparing(ItemCatalog::display));
        return List.copyOf(result);
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "").trim();
    }
}
