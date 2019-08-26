package com.region.buyregion.plugins;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import br.net.fabiozumbi12.RedProtect.Bukkit.Region;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class RedProtectHook implements PluginsHook {

    @Override
    public PluginRegion getRegion(String regionName, String world) {
        if (Bukkit.getWorld(world) != null && RedProtect.get().getAPI().getRegion(regionName, Bukkit.getWorld(world)) != null) {
            return new RedRegion(RedProtect.get().getAPI().getRegion(regionName, Bukkit.getWorld(world)));
        }
        return null;
    }

    @Override
    public PluginRegion getRegion(Location location) {
        if (RedProtect.get().getAPI().getRegion(location) != null) {
            return new RedRegion(RedProtect.get().getAPI().getRegion(location));
        }
        return null;
    }

    class RedRegion implements PluginRegion {
        private Region region;

        private RedRegion(Region region) {
            this.region = region;
        }

        @Override
        public void addMember(String member) {
            region.addMember(member);
        }

        @Override
        public void addMember(Player member) {
            addMember(member.getUniqueId().toString());
        }

        @Override
        public void addOwner(String owner) {
            region.clearLeaders();
            region.clearAdmins();
            region.clearMembers();
            region.addLeader(owner);
        }

        @Override
        public void addOwner(Player owner) {
            addOwner(owner.getUniqueId().toString());
        }

        @Override
        public void removeMember(String member) {
            region.removeMember(member);
            if (region.getLeaders().isEmpty())
            region.addLeader(RedProtect.get().config.configRoot().region_settings.default_leader);
        }

        @Override
        public boolean isOwner(Player player) {
            return region.isLeader(player);
        }

        @Override
        public boolean isMember(Player player) {
            return region.isMember(player);
        }

        @Override
        public List<UUID> getOwners() {
            return region.getLeaders().stream().map(
                l -> {
                    try {
                        return UUID.fromString(l.getUUID());
                    } catch(Exception ignored) {
                        return null;
                    }
                }
            ).filter(Objects::nonNull).collect(Collectors.toList());
        }

        @Override
        public String getName() {
            return region.getName();
        }

    }

}

