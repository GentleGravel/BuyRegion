package com.region.buyregion.plugins;

import br.net.fabiozumbi12.RedProtect.Bukkit.RedProtect;
import br.net.fabiozumbi12.RedProtect.Bukkit.Region;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class RedProtectHook implements PluginsHook {
    @Override
    public PluginRegion getRegion(String regionName, World world) {
        if (RedProtect.get().getAPI().getRegion(regionName, world) != null){
            return new RedRegion(RedProtect.get().getAPI().getRegion(regionName, world));
        }
        return null;
    }

    @Override
    public PluginRegion getRegion(Location location) {
        if (RedProtect.get().getAPI().getRegion(location) != null){
            return new RedRegion(RedProtect.get().getAPI().getRegion(location));
        }
        return null;
    }

    class RedRegion implements PluginRegion {
        private Region region;

        private RedRegion(Region region){
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
            if(region.getLeaders().isEmpty())
                region.addLeader(RedProtect.get().config.configRoot().region_settings.default_leader);
        }

        @Override
        public boolean isOwner(Player player) {
            return region.isLeader(player);
        }

        @Override
        public String getName() {
            return region.getName();
        }
    }
}
