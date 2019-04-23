package com.region.buyregion.plugins;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface PluginsHook {
    PluginRegion getRegion(String regionName, World world);
    PluginRegion getRegion(Location location);

    //Wrapper for plugin region
    interface PluginRegion {
        void addMember(String member);
        void addMember(Player member);
        void addOwner(String owner);
        void addOwner(Player owner);
        void removeMember(String owner);
        boolean isOwner(Player player);
        String getName();
    }
}
