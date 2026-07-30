package me.pinnacle.fragstealers.shop;

import me.pinnacle.fragstealers.FragStealers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Keeps the result slot of the synthetic shop-search anvil synchronized with
 * the text entered by the player.
 *
 * <p>Inventories created through Bukkit#createInventory with ANVIL type do not
 * reliably fire PrepareAnvilEvent when their rename text changes. Polling the
 * active AnvilView while it is open gives the plugin a deterministic result
 * button without requiring a physical anvil block.</p>
 */
public final class ShopSearchRefreshListener implements Listener {
    private static final int RESULT_SLOT = 2;

    private final FragStealers plugin;
    private final ShopMenuService menus;
    private final Map<UUID, BukkitTask> refreshTasks = new HashMap<>();

    public ShopSearchRefreshListener(FragStealers plugin, ShopMenuService menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
            || !(event.getView() instanceof AnvilView)
            || !(event.getInventory().getHolder() instanceof ShopSearchHolder)) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> startRefreshing(player));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player
            && event.getInventory().getHolder() instanceof ShopSearchHolder) {
            stopRefreshing(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopRefreshing(event.getPlayer().getUniqueId());
    }

    private void startRefreshing(Player player) {
        UUID playerId = player.getUniqueId();
        stopRefreshing(playerId);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()
                    || !(player.getOpenInventory() instanceof AnvilView anvilView)) {
                    finish(playerId);
                    return;
                }

                Inventory top = anvilView.getTopInventory();
                if (!(top.getHolder() instanceof ShopSearchHolder holder)) {
                    finish(playerId);
                    return;
                }

                if (holder.adminOverride() && !plugin.masterKeys().canUse(player)) {
                    finish(playerId);
                    player.closeInventory();
                    player.sendMessage(plugin.error("Keep the Master Key in your main hand while managing this shop."));
                    return;
                }

                String query = menus.cleanSearchQuery(anvilView.getRenameText());
                ItemStack currentResult = top.getItem(RESULT_SLOT);
                String currentQuery = menus.searchQuery(currentResult);

                if (Objects.equals(query, currentQuery)) {
                    return;
                }

                anvilView.setRepairCost(0);
                anvilView.setMaximumRepairCost(Integer.MAX_VALUE);
                top.setItem(RESULT_SLOT, menus.searchResult(query));
                player.updateInventory();
            }

            private void finish(UUID id) {
                cancel();
                refreshTasks.remove(id);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        refreshTasks.put(playerId, task);
    }

    private void stopRefreshing(UUID playerId) {
        BukkitTask task = refreshTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
