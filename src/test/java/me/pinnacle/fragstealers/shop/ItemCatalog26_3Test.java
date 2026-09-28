package me.pinnacle.fragstealers.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCatalog26_3Test {

    @Test
    void poplarMaterialsAvoidGenericFallbackGroups() {
        for (Material material : Material.values()) {
            if (!ItemCatalog.isAllowed(material) || !material.name().contains("POPLAR")) continue;
            ItemCatalog.Group group = groupOf(material);
            assertNotEquals(ItemCatalog.Group.OTHER_BUILDING, group, material.name());
            assertNotEquals(ItemCatalog.Group.OTHER_ITEMS, group, material.name());
        }

        assertGroup(Material.POPLAR_LOG, ItemCatalog.Group.POPLAR);
        assertGroup(Material.STRIPPED_POPLAR_LOG, ItemCatalog.Group.POPLAR);
        assertGroup(Material.STRIPPED_POPLAR_WOOD, ItemCatalog.Group.POPLAR);
        assertGroup(Material.POPLAR_BOAT, ItemCatalog.Group.BOATS);
        assertGroup(Material.POPLAR_BUTTON, ItemCatalog.Group.REDSTONE_COMPONENTS);
        assertGroup(Material.RED_POPLAR_LEAVES, ItemCatalog.Group.PLANTS_NATURE);
    }

    @Test
    void wildernessBoundNatureAndCampingItemsHaveDedicatedGroups() {
        assertGroup(Material.RED_SHRUB, ItemCatalog.Group.PLANTS_NATURE);
        assertGroup(Material.SHELF_MUSHROOM, ItemCatalog.Group.PLANTS_NATURE);
        assertGroup(Material.STRAW_BED, ItemCatalog.Group.CAMPING_COMFORT);

        for (Material material : Material.values()) {
            if (material.name().endsWith("_CUSHION")) {
                assertTrue(ItemCatalog.isAllowed(material), material.name());
                assertGroup(material, ItemCatalog.Group.CAMPING_COMFORT);
            }
        }
    }

    @Test
    void newBuildingFamiliesStayInTheirExistingFamilyGroups() {
        for (Material material : Material.values()) {
            String name = material.name();
            if (name.endsWith("_CONCRETE_SLAB") || name.endsWith("_CONCRETE_STAIRS")) {
                assertGroup(material, ItemCatalog.Group.CONCRETE);
            }
            if (name.endsWith("_WOOL_SLAB") || name.endsWith("_WOOL_STAIRS")) {
                assertGroup(material, ItemCatalog.Group.WOOL_CARPETS);
            }
        }
    }

    @Test
    void explorerMapsAreSaleItemsButNeverPaymentCurrencies() {
        assertTrue(ItemCatalog.isSafePaymentMaterial(Material.MAP));

        for (Material material : Material.values()) {
            if (!material.name().endsWith("_MAP") || material.name().startsWith("LEGACY_")) continue;
            assertTrue(ItemCatalog.isAllowed(material), material.name());
            assertGroup(material, ItemCatalog.Group.BOOKS_MAPS);
            assertFalse(ItemCatalog.isSafePaymentMaterial(material), material.name());
        }
    }

    @Test
    void quantitiesUseTheActual26_3MaterialStackSize() {
        Material[] materials = {
            Material.POPLAR_LOG,
            Material.WHITE_CUSHION,
            Material.STRAW_BED,
            Material.ABANDONED_CAMP_MAP,
            Material.SHELF_MUSHROOM,
            Material.RED_SHRUB,
            Material.WHITE_WOOL_STAIRS,
            Material.WHITE_CONCRETE_SLAB
        };

        for (Material material : materials) {
            int max = material.getMaxStackSize();
            int[] quantities = ItemCatalog.quantities(material);
            assertEquals(max, quantities.length, material.name());
            assertEquals(1, quantities[0], material.name());
            assertEquals(max, quantities[quantities.length - 1], material.name());
        }
    }

    @Test
    void nonItem26_3BlockStatesRemainUnavailable() {
        assertFalse(ItemCatalog.isAllowed(Material.POPLAR_WALL_SIGN));
        assertFalse(ItemCatalog.isAllowed(Material.POPLAR_WALL_HANGING_SIGN));
        assertFalse(ItemCatalog.isAllowed(Material.POTTED_POPLAR_SAPLING));
    }

    private static void assertGroup(Material material, ItemCatalog.Group expected) {
        assertEquals(expected, groupOf(material), material.name());
    }

    private static ItemCatalog.Group groupOf(Material material) {
        return Arrays.stream(ItemCatalog.Group.values())
            .filter(group -> ItemCatalog.items(group).contains(material))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Material missing from catalog: " + material));
    }
}
