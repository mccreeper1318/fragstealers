package me.pinnacle.fragstealers.shop;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final Map<Group, List<Material>> ITEMS_BY_GROUP = buildGroups();
    private static final Map<Category, List<Group>> GROUPS_BY_CATEGORY = buildCategories();
    private static final List<Category> CATEGORIES = List.of(Category.values()).stream()
        .filter(category -> !groups(category).isEmpty())
        .toList();

    private ItemCatalog() {
    }

    public static List<Category> categories() {
        return CATEGORIES;
    }

    public static List<Group> groups(Category category) {
        if (category == null) {
            return List.of();
        }
        return GROUPS_BY_CATEGORY.getOrDefault(category, List.of());
    }

    public static List<Material> items(Group group) {
        if (group == null) {
            return List.of();
        }
        return ITEMS_BY_GROUP.getOrDefault(group, List.of());
    }

    public static int itemCount(Category category) {
        int count = 0;
        for (Group group : groups(category)) {
            count += items(group).size();
        }
        return count;
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

    private static Map<Group, List<Material>> buildGroups() {
        Map<Group, List<Material>> grouped = new EnumMap<>(Group.class);
        for (Group group : Group.values()) {
            grouped.put(group, new ArrayList<>());
        }
        for (Material material : ITEMS) {
            grouped.get(classify(material)).add(material);
        }
        Map<Group, List<Material>> immutable = new EnumMap<>(Group.class);
        for (Map.Entry<Group, List<Material>> entry : grouped.entrySet()) {
            immutable.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static Map<Category, List<Group>> buildCategories() {
        Map<Category, List<Group>> grouped = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            List<Group> groups = new ArrayList<>();
            for (Group group : Group.values()) {
                if (group.category() == category && !items(group).isEmpty()) {
                    groups.add(group);
                }
            }
            grouped.put(category, List.copyOf(groups));
        }
        return Map.copyOf(grouped);
    }

    private static Group classify(Material material) {
        String name = material.name();

        if (name.equals("FISHING_ROD") || hasAny(name, "COD", "SALMON", "PUFFERFISH", "TROPICAL_FISH")) {
            return Group.FISHING;
        }
        if (hasAny(name, "_BOAT", "_RAFT") || name.endsWith("BOAT") || name.endsWith("RAFT")) {
            return Group.BOATS;
        }
        if (name.contains("MINECART") || name.equals("RAIL") || name.endsWith("_RAIL")) {
            return Group.MINECARTS_RAILS;
        }
        if (hasAny(name, "SADDLE", "ELYTRA", "CARROT_ON_A_STICK", "WARPED_FUNGUS_ON_A_STICK")) {
            return Group.RIDING_FLIGHT;
        }

        if (endsWithAny(name, "_PICKAXE", "_SHOVEL", "_HOE", "_AXE")
            || hasAny(name, "SHEARS", "BRUSH", "FLINT_AND_STEEL")) {
            return Group.TOOLS;
        }
        if (endsWithAny(name, "_SWORD") || hasAny(name, "BOW", "CROSSBOW", "TRIDENT", "MACE")) {
            return Group.WEAPONS;
        }
        if (endsWithAny(name, "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_HORSE_ARMOR")
            || hasAny(name, "TURTLE_HELMET", "WOLF_ARMOR")) {
            return Group.ARMOR;
        }
        if (hasAny(name, "SHIELD", "TOTEM_OF_UNDYING", "RECOVERY_COMPASS", "SPYGLASS")) {
            return Group.UTILITY_GEAR;
        }

        if (hasAny(name, "ENCHANTING_TABLE", "ENCHANTED_BOOK", "EXPERIENCE_BOTTLE", "BOOKSHELF", "CHISELED_BOOKSHELF")) {
            return Group.ENCHANTING;
        }
        if (hasAny(name, "POTION", "GLASS_BOTTLE", "DRAGON_BREATH")) {
            return Group.POTIONS_BOTTLES;
        }
        if (hasAny(name, "NETHER_WART", "BLAZE_POWDER", "FERMENTED_SPIDER_EYE", "GLISTERING_MELON_SLICE",
            "MAGMA_CREAM", "GHAST_TEAR", "RABBIT_FOOT")) {
            return Group.BREWING_INGREDIENTS;
        }

        if (hasAny(name, "REDSTONE", "REPEATER", "COMPARATOR", "OBSERVER", "LEVER", "BUTTON", "PRESSURE_PLATE",
            "TRIPWIRE_HOOK", "DAYLIGHT_DETECTOR", "SCULK_SENSOR", "TARGET")) {
            return Group.REDSTONE_COMPONENTS;
        }
        if (hasAny(name, "PISTON", "DISPENSER", "DROPPER", "HOPPER", "CRAFTER")) {
            return Group.REDSTONE_AUTOMATION;
        }
        if (hasAny(name, "REDSTONE_LAMP", "NOTE_BLOCK")) {
            return Group.REDSTONE_LIGHT_SOUND;
        }

        if (isStorage(name)) {
            return Group.STORAGE;
        }
        if (hasAny(name, "FURNACE", "SMOKER", "CAMPFIRE", "CRAFTING_TABLE", "STONECUTTER")) {
            return Group.CRAFTING_FURNACES;
        }
        if (hasAny(name, "ANVIL", "GRINDSTONE", "SMITHING_TABLE", "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE",
            "LECTERN", "COMPOSTER", "CAULDRON")) {
            return Group.WORKSTATIONS;
        }

        if (name.startsWith("DARK_OAK_")) return Group.DARK_OAK;
        if (name.startsWith("PALE_OAK_")) return Group.PALE_OAK;
        if (name.startsWith("OAK_")) return Group.OAK;
        if (name.startsWith("SPRUCE_")) return Group.SPRUCE;
        if (name.startsWith("BIRCH_")) return Group.BIRCH;
        if (name.startsWith("JUNGLE_")) return Group.JUNGLE;
        if (name.startsWith("ACACIA_")) return Group.ACACIA;
        if (name.startsWith("MANGROVE_")) return Group.MANGROVE;
        if (name.startsWith("CHERRY_")) return Group.CHERRY;
        if (name.startsWith("BAMBOO_")) return Group.BAMBOO;
        if (name.startsWith("CRIMSON_")) return Group.CRIMSON;
        if (name.startsWith("WARPED_")) return Group.WARPED;

        if (name.endsWith("_ORE") || name.startsWith("RAW_") || name.equals("ANCIENT_DEBRIS")) {
            return Group.ORES_RAW;
        }
        if (endsWithAny(name, "_INGOT", "_NUGGET") || hasAny(name, "DIAMOND", "EMERALD", "LAPIS_LAZULI", "AMETHYST_SHARD",
            "ECHO_SHARD", "COAL", "CHARCOAL", "QUARTZ")) {
            return Group.INGOTS_GEMS;
        }
        if (isMineralBlock(name)) {
            return Group.MINERAL_BLOCKS;
        }

        if (hasAny(name, "SEEDS", "WHEAT", "CARROT", "POTATO", "BEETROOT", "MELON_SEEDS", "PUMPKIN_SEEDS",
            "COCOA_BEANS", "SUGAR_CANE")) {
            return Group.CROPS_SEEDS;
        }
        if (material.isEdible()) {
            return Group.FOOD;
        }
        if (hasAny(name, "LEATHER", "FEATHER", "EGG", "MILK_BUCKET", "HONEY", "INK_SAC")) {
            return Group.ANIMAL_PRODUCTS;
        }

        if (hasAny(name, "BLAZE_ROD", "WITHER_SKELETON_SKULL", "NETHER_STAR")) {
            return Group.NETHER_DROPS;
        }
        if (hasAny(name, "ENDER_PEARL", "SHULKER_SHELL")) {
            return Group.END_DROPS;
        }
        if (hasAny(name, "ROTTEN_FLESH", "BONE", "STRING", "SPIDER_EYE", "GUNPOWDER", "SLIME_BALL", "PHANTOM_MEMBRANE",
            "BREEZE_ROD", "WIND_CHARGE")) {
            return Group.HOSTILE_DROPS;
        }

        if (isNature(name)) {
            return Group.PLANTS_NATURE;
        }

        if (hasAny(name, "NETHERRACK", "SOUL_SAND", "SOUL_SOIL", "BASALT", "BLACKSTONE", "GLOWSTONE", "MAGMA_BLOCK",
            "NETHER_BRICK", "NETHER_GOLD", "NETHER_QUARTZ")) {
            return Group.NETHER_BLOCKS;
        }
        if (hasAny(name, "SHROOMLIGHT", "WEEPING_VINES", "TWISTING_VINES", "NETHER_SPROUTS")) {
            return Group.NETHER_NATURE;
        }
        if (hasAny(name, "END_STONE", "PURPUR", "END_ROD", "DRAGON_EGG")) {
            return Group.END_BLOCKS;
        }
        if (hasAny(name, "CHORUS")) {
            return Group.CHORUS_SHULKER;
        }

        if (name.contains("CONCRETE")) return Group.CONCRETE;
        if (name.contains("TERRACOTTA")) return Group.TERRACOTTA;
        if (name.contains("GLASS")) return Group.GLASS;
        if (hasAny(name, "SAND", "SANDSTONE")) return Group.SAND_SANDSTONE;
        if (name.contains("COPPER") && material.isBlock()) return Group.COPPER_BUILDING;
        if (hasAny(name, "BRICK", "PRISMARINE", "MUD_BRICKS", "TUFF_BRICKS")) return Group.BRICKS_MASONRY;
        if (hasAny(name, "STONE", "DEEPSLATE", "COBBLE", "ANDESITE", "DIORITE", "GRANITE", "TUFF", "CALCITE", "DRIPSTONE")) {
            return Group.STONE_DEEPSLATE;
        }

        if (hasAny(name, "WOOL", "CARPET")) return Group.WOOL_CARPETS;
        if (hasAny(name, "DYE", "BANNER")) return Group.BANNERS_DYES;
        if (hasAny(name, "TORCH", "LANTERN", "CANDLE", "FROGLIGHT", "JACK_O_LANTERN", "SEA_LANTERN")) {
            return Group.LIGHTING;
        }
        if (hasAny(name, "PAINTING", "ITEM_FRAME", "ARMOR_STAND", "FLOWER_POT", "DECORATED_POT", "SKULL", "HEAD")) {
            return Group.ART_DISPLAY;
        }

        if (material.isBlock()) {
            return Group.OTHER_BUILDING;
        }
        if (hasAny(name, "BOOK", "PAPER", "MAP", "COMPASS", "CLOCK", "NAME_TAG")) {
            return Group.BOOKS_MAPS;
        }
        if (hasAny(name, "MUSIC_DISC", "DISC_FRAGMENT", "GOAT_HORN")) {
            return Group.MUSIC_COLLECTIBLES;
        }
        return Group.OTHER_ITEMS;
    }

    private static boolean isStorage(String name) {
        return name.equals("CHEST") || name.equals("TRAPPED_CHEST") || name.equals("ENDER_CHEST")
            || name.equals("BARREL") || name.equals("BUNDLE") || name.endsWith("_SHULKER_BOX");
    }

    private static boolean isMineralBlock(String name) {
        return hasAny(name, "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "LAPIS_BLOCK", "COAL_BLOCK",
            "RAW_IRON_BLOCK", "RAW_GOLD_BLOCK", "RAW_COPPER_BLOCK", "AMETHYST_BLOCK", "BUDDING_AMETHYST");
    }

    private static boolean isNature(String name) {
        return hasAny(name, "LEAVES", "SAPLING", "FLOWER", "GRASS", "MOSS", "AZALEA", "VINE", "LILY", "MUSHROOM",
            "CACTUS", "KELP", "SEAGRASS", "DRIPLEAF", "SPORE_BLOSSOM", "FERN", "BUSH", "ROOTS");
    }

    private static boolean hasAny(String value, String... fragments) {
        for (String fragment : fragments) {
            if (value.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public enum Category {
        BUILDING_BLOCKS("Building Blocks", Material.BRICKS),
        WOOD_NATURAL("Wood & Natural", Material.OAK_LOG),
        ORES_MINERALS("Ores & Minerals", Material.DIAMOND),
        REDSTONE("Redstone", Material.REDSTONE),
        FARMING_FOOD("Farming & Food", Material.WHEAT),
        MOB_DROPS("Mob Drops", Material.ROTTEN_FLESH),
        TOOLS_EQUIPMENT("Tools & Equipment", Material.IRON_PICKAXE),
        DECORATION("Decoration", Material.PAINTING),
        BREWING_ENCHANTING("Brewing & Enchanting", Material.BREWING_STAND),
        TRANSPORTATION("Transportation", Material.MINECART),
        NETHER("Nether", Material.NETHERRACK),
        END("End", Material.END_STONE),
        STORAGE_UTILITY("Storage & Utility", Material.CHEST),
        MISCELLANEOUS("Miscellaneous", Material.BOOK);

        private final String displayName;
        private final Material icon;

        Category(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String displayName() { return displayName; }
        public Material icon() { return icon; }
    }

    public enum Group {
        STONE_DEEPSLATE(Category.BUILDING_BLOCKS, "Stone & Deepslate", Material.STONE),
        BRICKS_MASONRY(Category.BUILDING_BLOCKS, "Bricks & Masonry", Material.BRICKS),
        CONCRETE(Category.BUILDING_BLOCKS, "Concrete", Material.WHITE_CONCRETE),
        TERRACOTTA(Category.BUILDING_BLOCKS, "Terracotta", Material.TERRACOTTA),
        GLASS(Category.BUILDING_BLOCKS, "Glass", Material.GLASS),
        SAND_SANDSTONE(Category.BUILDING_BLOCKS, "Sand & Sandstone", Material.SANDSTONE),
        COPPER_BUILDING(Category.BUILDING_BLOCKS, "Copper Blocks", Material.COPPER_BLOCK),
        OTHER_BUILDING(Category.BUILDING_BLOCKS, "Other Building Blocks", Material.COBBLESTONE),

        OAK(Category.WOOD_NATURAL, "Oak", Material.OAK_LOG),
        SPRUCE(Category.WOOD_NATURAL, "Spruce", Material.SPRUCE_LOG),
        BIRCH(Category.WOOD_NATURAL, "Birch", Material.BIRCH_LOG),
        JUNGLE(Category.WOOD_NATURAL, "Jungle", Material.JUNGLE_LOG),
        ACACIA(Category.WOOD_NATURAL, "Acacia", Material.ACACIA_LOG),
        DARK_OAK(Category.WOOD_NATURAL, "Dark Oak", Material.DARK_OAK_LOG),
        MANGROVE(Category.WOOD_NATURAL, "Mangrove", Material.MANGROVE_LOG),
        CHERRY(Category.WOOD_NATURAL, "Cherry", Material.CHERRY_LOG),
        PALE_OAK(Category.WOOD_NATURAL, "Pale Oak", Material.PALE_OAK_LOG),
        BAMBOO(Category.WOOD_NATURAL, "Bamboo", Material.BAMBOO_BLOCK),
        CRIMSON(Category.WOOD_NATURAL, "Crimson", Material.CRIMSON_STEM),
        WARPED(Category.WOOD_NATURAL, "Warped", Material.WARPED_STEM),
        PLANTS_NATURE(Category.WOOD_NATURAL, "Plants & Nature", Material.OAK_SAPLING),

        ORES_RAW(Category.ORES_MINERALS, "Ores & Raw Materials", Material.IRON_ORE),
        INGOTS_GEMS(Category.ORES_MINERALS, "Ingots & Gems", Material.DIAMOND),
        MINERAL_BLOCKS(Category.ORES_MINERALS, "Mineral Blocks", Material.IRON_BLOCK),

        REDSTONE_COMPONENTS(Category.REDSTONE, "Components & Signals", Material.REDSTONE),
        REDSTONE_AUTOMATION(Category.REDSTONE, "Automation", Material.PISTON),
        REDSTONE_LIGHT_SOUND(Category.REDSTONE, "Light & Sound", Material.REDSTONE_LAMP),

        CROPS_SEEDS(Category.FARMING_FOOD, "Crops & Seeds", Material.WHEAT_SEEDS),
        FOOD(Category.FARMING_FOOD, "Food", Material.BREAD),
        ANIMAL_PRODUCTS(Category.FARMING_FOOD, "Animal Products", Material.LEATHER),
        FISHING(Category.FARMING_FOOD, "Fishing", Material.COD),

        HOSTILE_DROPS(Category.MOB_DROPS, "Hostile Mob Drops", Material.ROTTEN_FLESH),
        NETHER_DROPS(Category.MOB_DROPS, "Nether Mob Drops", Material.BLAZE_ROD),
        END_DROPS(Category.MOB_DROPS, "End Mob Drops", Material.ENDER_PEARL),

        TOOLS(Category.TOOLS_EQUIPMENT, "Tools", Material.IRON_PICKAXE),
        WEAPONS(Category.TOOLS_EQUIPMENT, "Weapons", Material.IRON_SWORD),
        ARMOR(Category.TOOLS_EQUIPMENT, "Armor", Material.IRON_CHESTPLATE),
        UTILITY_GEAR(Category.TOOLS_EQUIPMENT, "Utility Gear", Material.SHIELD),

        WOOL_CARPETS(Category.DECORATION, "Wool & Carpets", Material.WHITE_WOOL),
        BANNERS_DYES(Category.DECORATION, "Banners & Dyes", Material.RED_DYE),
        LIGHTING(Category.DECORATION, "Lighting", Material.LANTERN),
        ART_DISPLAY(Category.DECORATION, "Art & Display", Material.PAINTING),

        POTIONS_BOTTLES(Category.BREWING_ENCHANTING, "Potions & Bottles", Material.POTION),
        BREWING_INGREDIENTS(Category.BREWING_ENCHANTING, "Brewing Ingredients", Material.NETHER_WART),
        ENCHANTING(Category.BREWING_ENCHANTING, "Enchanting", Material.ENCHANTING_TABLE),

        BOATS(Category.TRANSPORTATION, "Boats & Rafts", Material.OAK_BOAT),
        MINECARTS_RAILS(Category.TRANSPORTATION, "Minecarts & Rails", Material.RAIL),
        RIDING_FLIGHT(Category.TRANSPORTATION, "Riding & Flight", Material.SADDLE),

        NETHER_BLOCKS(Category.NETHER, "Nether Blocks", Material.NETHERRACK),
        NETHER_NATURE(Category.NETHER, "Nether Plants & Materials", Material.SHROOMLIGHT),

        END_BLOCKS(Category.END, "End Blocks", Material.END_STONE),
        CHORUS_SHULKER(Category.END, "Chorus & End Materials", Material.CHORUS_FRUIT),

        STORAGE(Category.STORAGE_UTILITY, "Storage", Material.CHEST),
        CRAFTING_FURNACES(Category.STORAGE_UTILITY, "Crafting & Furnaces", Material.FURNACE),
        WORKSTATIONS(Category.STORAGE_UTILITY, "Workstations", Material.CRAFTING_TABLE),

        BOOKS_MAPS(Category.MISCELLANEOUS, "Books, Maps & Info", Material.BOOK),
        MUSIC_COLLECTIBLES(Category.MISCELLANEOUS, "Music & Collectibles", Material.MUSIC_DISC_13),
        OTHER_ITEMS(Category.MISCELLANEOUS, "Other Items", Material.PAPER);

        private final Category category;
        private final String displayName;
        private final Material icon;

        Group(Category category, String displayName, Material icon) {
            this.category = category;
            this.displayName = displayName;
            this.icon = icon;
        }

        public Category category() { return category; }
        public String displayName() { return displayName; }
        public Material icon() { return icon; }
    }
}
