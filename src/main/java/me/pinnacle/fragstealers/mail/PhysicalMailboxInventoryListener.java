package me.pinnacle.fragstealers.mail;

import me.pinnacle.fragstealers.FragStealers;
import me.pinnacle.fragstealers.data.MailboxManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

public final class PhysicalMailboxInventoryListener implements Listener {
    private final FragStealers plugin;
    private final MailboxManager manager;

    public PhysicalMailboxInventoryListener(FragStealers plugin, MailboxManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inventory = event.getInventory();
        if (!isPhysicalMailboxInventory(inventory)) return;
        event.setCancelled(true);
        player.sendMessage(plugin.error("Use the mailbox sign to deposit or collect mail."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isPhysicalMailboxInventory(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        closeUnexpectedPhysicalView(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isPhysicalMailboxInventory(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        closeUnexpectedPhysicalView(player);
    }

    private boolean isPhysicalMailboxInventory(Inventory inventory) {
        return !(inventory.getHolder() instanceof MailboxMenuHolder) && manager.inventoryBelongsToAny(inventory);
    }

    private void closeUnexpectedPhysicalView(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.closeInventory());
        player.sendMessage(plugin.error("Use the mailbox sign to deposit or collect mail."));
    }
}
