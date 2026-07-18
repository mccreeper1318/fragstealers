package me.pinnacle.fragstealers;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class FragStealersCommand implements CommandExecutor, TabCompleter {
    private static final String RELOAD_PERMISSION = "fragstealers.admin.reload";
    private final FragStealers plugin;

    public FragStealersCommand(FragStealers plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                sender.sendMessage(plugin.error("You do not have permission to reload FragStealers."));
                return true;
            }
            plugin.reloadSettings();
            sender.sendMessage(plugin.success("Configuration reloaded."));
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("masterkey") && args.length <= 3) {
            return giveMasterKey(sender, args);
        }

        sender.sendMessage(plugin.success("Commands: /fs give masterkey [player], /fs reload"));
        return true;
    }

    private boolean giveMasterKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            sender.sendMessage(plugin.error("You do not have permission to give Master Keys."));
            return true;
        }
        Player target;
        if (args.length == 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.error("Player " + args[2] + " is not online."));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(plugin.error("Console must specify an online player."));
            return true;
        }

        ItemStack key = plugin.masterKeys().createMasterKey();
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(key);
        for (ItemStack item : overflow.values()) target.getWorld().dropItemNaturally(target.getLocation(), item);
        target.sendMessage(plugin.success("You received a FragStealers Master Key."));
        if (!sender.equals(target)) sender.sendMessage(plugin.success("Gave a Master Key to " + target.getName() + "."));
        plugin.getLogger().info(sender.getName() + " gave a FragStealers Master Key to " + target.getName() + ".");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) options.add("give");
            if (sender.hasPermission(RELOAD_PERMISSION)) options.add("reload");
            return matches(args[0], options);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give") && sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            return matches(args[1], List.of("masterkey"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("masterkey")
            && sender.hasPermission(MasterKeyManager.GIVE_PERMISSION)) {
            return matches(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }
        return List.of();
    }

    private List<String> matches(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
