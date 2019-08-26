package com.region.buyregion.plugins;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldGuardPlatform;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import static org.bukkit.Bukkit.getLogger;

public class WorldGuardHook implements PluginsHook {
    private RegionManager regionManager;

    @Override
    public PluginRegion getRegion(Location location) {
        regionManager = getWorldGuardRegionManager(location.getWorld().getName());
        if (regionManager != null && regionManager.getApplicableRegions(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ())).size() > 0) {
            return new WERegion(regionManager.getApplicableRegions(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ())).getRegions().stream().max(Comparator.comparing(ProtectedRegion::getPriority)).get());
        }
        return null;
    }

    @Override
    public PluginRegion getRegion(String regionName, String world) {
        regionManager = getWorldGuardRegionManager(world);
        if (regionManager != null && regionManager.getRegion(regionName) != null) {
            return new WERegion(regionManager.getRegion(regionName));
        }
        return null;
    }

    class WERegion implements PluginRegion {
        private ProtectedRegion region;

        private WERegion(ProtectedRegion region) {
            this.region = region;
        }

        @Override
        public void addMember(String member) {
            DefaultDomain dd = new DefaultDomain();
            dd.addPlayer(member);
            region.setMembers(dd);
            try {
                regionManager.save();
            } catch(StorageException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void addMember(Player member) {
            addMember(member.getName());
        }

        @Override
        public void addOwner(String owner) {
            DefaultDomain dd = new DefaultDomain();
            dd.addPlayer(owner);
            region.setOwners(dd);
            try {
                regionManager.save();
            } catch(StorageException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void addOwner(Player owner) {
            addOwner(owner.getName());
        }

        @Override
        public void removeMember(String member) {
            DefaultDomain dd = new DefaultDomain();
            dd.removePlayer(member);
            region.setMembers(dd);
            try {
                regionManager.save();
            } catch(StorageException e) {
                e.printStackTrace();
            }
        }

        @Override
        public boolean isOwner(Player player) {
            return region.isOwner(WorldGuardPlugin.inst().wrapPlayer(player));
        }

        @Override
        public boolean isMember(Player player) {
            return region.isMember(WorldGuardPlugin.inst().wrapPlayer(player));
        }

        @Override
        public List<UUID> getOwners() {
            return new ArrayList<>(region.getOwners().getUniqueIds());
        }

        @Override
        public String getName() {
            return region.getId();
        }

    }

    private RegionManager getWorldGuardRegionManager(String world) {
        BukkitWorldGuardPlatform wgPlatform = (BukkitWorldGuardPlatform) WorldGuard.getInstance().getPlatform();
        try {
            com.sk89q.worldedit.world.World wgWorld = wgPlatform.getMatcher().getWorldByName(world);
            return wgPlatform.getRegionContainer().get(wgWorld);
        } catch(NoSuchMethodError e) {
            getLogger().log(Level.SEVERE, "Method not found in WorldGuard. Make sure you are using WG 7.0.0 Beta 3 or higher", e);
            return null;
        }
    }

}

