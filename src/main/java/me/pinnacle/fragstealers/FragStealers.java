package me.pinnacle.fragstealers;

import me.pinnacle.fragstealers.data.AuditLogManager;
import me.pinnacle.fragstealers.data.ChestLock;
import me.pinnacle.fragstealers.data.LockManager;
import me.pinnacle.fragstealers.data.MailboxData;
import me.pinnacle.fragstealers.data.MailboxManager;
import me.pinnacle.fragstealers.data.ShopData;
import me.pinnacle.fragstealers.data.ShopManager;
import me.pinnacle.fragstealers.mail.MailboxListener;
import me.pinnacle.fragstealers.mail.MailboxMenuService;
import me.pinnacle.fragstealers.shop.ShopListener;
import me.pinnacle.fragstealers.shop.ShopMenuService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class FragStealers extends JavaPlugin {
    private ContainerResolver resolver;
    private LockManager locks;
    private ShopManager shops;
    private MailboxManager mailboxes;
    private AuditLogManager audit;
    private TrustManager trust;
    private MasterKeyManager masterKeys;
    private ShopMenuService shopMenus;
    private MailboxMenuService mailboxMenus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        mergeConfigDefaults();
        resolver = new ContainerResolver();
        masterKeys = new MasterKeyManager(this);
        locks = new LockManager(this, resolver);
        shops = new ShopManager(this, resolver);
        mailboxes = new MailboxManager(this, resolver);
        audit = new AuditLogManager(this);
        trust = new TrustManager(this);

        locks.load();
        shops.load();
        mailboxes.load();
        trust.load();
        locks.refreshAllContainers();
        shops.refreshAllContainers();
        mailboxes.refreshAllContainers();
        audit.loadAndPurge();

        shopMenus = new ShopMenuService(this, shops);
        mailboxMenus = new MailboxMenuService(this, mailboxes);
        shops.all().forEach(shopMenus::updateSign);

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this, shops, shopMenus), this);
        getServer().getPluginManager().registerEvents(new MailboxListener(this, mailboxes, mailboxMenus), this);

        PluginCommand command = getCommand("fragstealers");
        if (command == null) {
            getLogger().severe("fragstealers command is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        FragStealersCommand handler = new FragStealersCommand(this);
        command.setExecutor(handler);
        command.setTabCompleter(handler);

        long day = 20L * 60L * 60L * 24L;
        getServer().getScheduler().runTaskTimer(this, audit::purgeExpired, day, day);
        getLogger().info("FragStealers " + getPluginMeta().getVersion() + " enabled with "
            + locks.count() + " lock(s), " + shops.count() + " shop(s), and " + mailboxes.count() + " mailbox(es).");
    }

    @Override
    public void onDisable() {
        if (locks != null) locks.save();
        if (shops != null) shops.save();
        if (mailboxes != null) mailboxes.save();
        if (trust != null) trust.save();
        if (audit != null) audit.save();
    }

    public void reloadSettings() {
        reloadConfig();
        mergeConfigDefaults();
    }

    private void mergeConfigDefaults() {
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public boolean shopsEnabled() { return getConfig().getBoolean("fs-shops", true); }
    public boolean mailEnabled() { return getConfig().getBoolean("fs-mail", true); }
    public boolean hopperTakeEnabled() { return getConfig().getBoolean("hopper-take-item", false); }
    public boolean hopperPutEnabled() { return getConfig().getBoolean("hopper-put-item", true); }

    /** Owner or active Master Key administrator. Trusted players are intentionally excluded. */
    public boolean canManage(Player player, UUID owner) {
        return owner.equals(player.getUniqueId()) || masterKeys.canUse(player);
    }

    public boolean canAccessLock(Player player, ChestLock lock) {
        return lock.isOwner(player.getUniqueId())
            || masterKeys.canUse(player)
            || trust.has(ProtectionType.LOCK, lock.signKey(), player.getUniqueId(), TrustLevel.ACCESS);
    }

    public boolean canRestockShop(Player player, ShopData shop) {
        return canManageShop(player, shop)
            || trust.has(ProtectionType.SHOP, shop.signKey(), player.getUniqueId(), TrustLevel.ACCESS);
    }

    public boolean canRestockShop(Player player, BlockKey signKey) {
        ShopData shop = shops.bySign(signKey);
        return shop != null && canRestockShop(player, shop);
    }

    public boolean canManageShop(Player player, ShopData shop) {
        return shop.isOwner(player.getUniqueId())
            || masterKeys.canUse(player)
            || trust.has(ProtectionType.SHOP, shop.signKey(), player.getUniqueId(), TrustLevel.MANAGE);
    }

    public boolean canManageShop(Player player, BlockKey signKey) {
        ShopData shop = shops.bySign(signKey);
        return shop != null && canManageShop(player, shop);
    }

    public boolean canCollectMailbox(Player player, MailboxData mailbox) {
        return mailbox.isOwner(player.getUniqueId())
            || masterKeys.canUse(player)
            || trust.has(ProtectionType.MAILBOX, mailbox.signKey(), player.getUniqueId(), TrustLevel.ACCESS);
    }

    public boolean canCollectMailbox(Player player, BlockKey signKey) {
        MailboxData mailbox = mailboxes.bySign(signKey);
        return mailbox != null && canCollectMailbox(player, mailbox);
    }

    public boolean canManageMailbox(Player player, MailboxData mailbox) {
        return mailbox.isOwner(player.getUniqueId())
            || masterKeys.canUse(player)
            || trust.has(ProtectionType.MAILBOX, mailbox.signKey(), player.getUniqueId(), TrustLevel.MANAGE);
    }

    public boolean isMasterOverride(Player player, UUID owner) {
        return !owner.equals(player.getUniqueId()) && masterKeys.canUse(player);
    }

    public ProtectedTarget protectedTarget(Block block) {
        ChestLock lock = locks.bySign(block).orElseGet(() -> locks.byContainer(block).orElse(null));
        if (lock != null) {
            return new ProtectedTarget(ProtectionType.LOCK, lock.signKey(), lock.ownerUuid(), lock.ownerName());
        }
        ShopData shop = shops.bySign(block);
        if (shop == null) shop = shops.byContainer(block);
        if (shop != null) {
            return new ProtectedTarget(ProtectionType.SHOP, shop.signKey(), shop.ownerUuid(), shop.ownerName());
        }
        MailboxData mailbox = mailboxes.bySign(block);
        if (mailbox == null) mailbox = mailboxes.byContainer(block);
        if (mailbox != null) {
            return new ProtectedTarget(ProtectionType.MAILBOX, mailbox.signKey(), mailbox.ownerUuid(), mailbox.ownerName());
        }
        return null;
    }

    public boolean anyContainerProtected(Block block) {
        return locks.byContainer(block).isPresent() || shops.byContainer(block) != null || mailboxes.byContainer(block) != null;
    }

    public boolean anyProtectedBlock(Block block) {
        return locks.isProtectedBlock(block) || shops.isProtectedBlock(block) || mailboxes.isProtectedBlock(block);
    }

    public ProtectionType containerType(Block block) {
        if (locks.byContainer(block).isPresent()) return ProtectionType.LOCK;
        if (shops.byContainer(block) != null) return ProtectionType.SHOP;
        if (mailboxes.byContainer(block) != null) return ProtectionType.MAILBOX;
        return null;
    }

    public Component success(String text) {
        return Component.text("FragStealers: ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.GREEN));
    }

    public Component error(String text) {
        return Component.text("FragStealers: ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.RED));
    }

    public ContainerResolver resolver() { return resolver; }
    public LockManager locks() { return locks; }
    public ShopManager shops() { return shops; }
    public MailboxManager mailboxes() { return mailboxes; }
    public AuditLogManager audit() { return audit; }
    public TrustManager trust() { return trust; }
    public MasterKeyManager masterKeys() { return masterKeys; }
    public ShopMenuService shopMenus() { return shopMenus; }
    public MailboxMenuService mailboxMenus() { return mailboxMenus; }
}
