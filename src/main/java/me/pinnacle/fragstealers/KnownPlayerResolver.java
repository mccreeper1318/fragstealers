package me.pinnacle.fragstealers;

import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

public final class KnownPlayerResolver {
    private KnownPlayerResolver() {
    }

    public static OfflinePlayer find(Server server, String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return null;
        }

        Player online = server.getPlayerExact(requestedName);
        if (online != null) {
            return online;
        }

        for (OfflinePlayer candidate : server.getOfflinePlayers()) {
            String candidateName = candidate.getName();
            if (candidateName != null
                && candidateName.equalsIgnoreCase(requestedName)
                && candidate.hasPlayedBefore()) {
                return candidate;
            }
        }
        return null;
    }
}
