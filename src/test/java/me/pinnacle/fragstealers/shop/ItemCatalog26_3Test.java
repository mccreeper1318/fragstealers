package me.pinnacle.fragstealers.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemCatalog26_3Test {

    @Test
    void poplarMaterialsUseSpecificGroups() {
        assertGroup(Material.POPLAR_LOG, ItemCatalog.Group.POPLAR);
        assertGroup(Material.POPLAR_PLANKS, ItemCatalog.Group.POPLAR);
        assertGroup(Material.STRIPPED_POPLAR_LOG, ItemCatalog.Group.POPLAR);
        assertGroup(Material.STRIPPED_POPLAR_WOOD, ItemCatalog.Group.POPLAR);

        // These more specific rules intentionally win before the general Poplar group.
        assertGroup(Material.POPLAR_BOAT, ItemCatalog.Group.BOATS);
        assertGroup(Material.POPLAR_BUTTON, ItemCatalog.Group.REDSTONE_COMPONENTS);
        assertGroup(Material.RED_POPLAR_LEAVES, ItemCatalog.Group.PLANTS_NATURE);
        assertGroup(Material.ORANGE_POPLAR_LEAVES, ItemCatalog.Group.PLANTS_NATURE);
        assertGroup(Material.YELLOW_POPLAR_LEAVES, ItemCatalog.Group.PLANTS_NATURE);
    }

    @Test
    void wildernessBoundNatureAndCampingItemsHaveDedicatedGroups() {
        assertGroup(Material.RED_SHRUB, ItemCatalog.Group.PLANTS_NATURE);
        assertGroup(Material.SHELF_MUSHROOM, ItemCatalog.Group.PLANTS_NATURE);
        assertGroup(Material.STRAW_BED, ItemCatalog.Group.CAMPING_COMFORT);

        int cushions = 0;
        for (Material material : Material.values()) {
            if (!material.name().endsWith("_CUSHION")) continue;
            cushions++;
            assertGroup(material, ItemCatalog.Group.CAMPING_COMFORT);
        }
        assertEquals(16, cushions, "Paper 26.3 cushion variants");
    }

    @Test
    void newConcreteAndWoolFamiliesStayInExistingGroups() {
        int concreteVariants = 0;
        int woolVariants = 0;

        for (Material material : Material.values()) {
            String name = material.name();
            if (!name.startsWith("LEGACY_")
                && (name.endsWith("_CONCRETE_SLAB") || name.endsWith("_CONCRETE_STAIRS"))) {
                concreteVariants++;
                assertGroup(material, ItemCatalog.Group.CONCRETE);
            }
            if (!name.startsWith("LEGACY_")
                && (name.endsWith("_WOOL_SLAB") || name.endsWith("_WOOL_STAIRS"))) {
                woolVariants++;
                assertGroup(material, ItemCatalog.Group.WOOL_CARPETS);
            }
        }

        assertEquals(32, concreteVariants, "Paper 26.3 concrete slab/stair variants");
        assertEquals(32, woolVariants, "Paper 26.3 wool slab/stair variants");
    }

    @Test
    void explorerMapsUseTheBooksAndMapsGroup() {
        int explorerMaps = 0;

        for (Material material : Material.values()) {
            String name = material.name();
            if (!name.endsWith("_MAP") || name.equals("FILLED_MAP") || name.startsWith("LEGACY_")) continue;
            explorerMaps++;
            assertGroup(material, ItemCatalog.Group.BOOKS_MAPS);
        }

        assertEquals(16, explorerMaps, "Paper 26.3 explorer map variants");
    }

    private static void assertGroup(Material material, ItemCatalog.Group expected) {
        assertEquals(expected, ItemCatalog.classify(material), material.name());
    }
}
