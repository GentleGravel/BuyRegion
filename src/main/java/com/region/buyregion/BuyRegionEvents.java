package com.region.buyregion;

import com.region.buyregion.helpers.ChatHelper;
import com.region.buyregion.plugins.PluginsHook;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;

import static com.region.buyregion.BuyRegion.econ;

public class BuyRegionEvents implements Listener {
    private final BuyRegion plugin;

    BuyRegionEvents(BuyRegion plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPunchSign(PlayerInteractEvent event) {
        try {
            if (event.getAction().name().equals("RIGHT_CLICK_BLOCK")) {
                Material blockType = event.getClickedBlock().getType();
                if (blockType.name().endsWith("_SIGN") || blockType.name().endsWith("WALL_SIGN")) {
                    Sign sign = (Sign) event.getClickedBlock().getState();
                    String topLine = sign.getLine(0);
                    if (topLine.length() > 0 && (topLine.equalsIgnoreCase(this.plugin.config.signHeaderBuy) || topLine.equalsIgnoreCase("[WGRSA]"))) {
                        Player sender = event.getPlayer();
                        String playerName = sender.getName();
                        if (topLine.equalsIgnoreCase("[WGRSA]")) {
                            sign.setLine(0, this.plugin.config.signHeaderBuy);
                            sign.update();
                        }
                        if (this.plugin.config.requireBuyPerms && !sender.hasPermission("buyregion.buy") && !sender.isOp()) {
                            sender.sendMessage(ChatHelper.notice("BuyPerms"));
                            return;
                        }
                        if (this.plugin.config.buyRegionMax > 0 && this.plugin.getBoughtRegionsCount(playerName) >= this.plugin.config.buyRegionMax && !sender.isOp() && (!sender.hasPermission("buyregion.exempt"))) {
                            sender.sendMessage(ChatHelper.notice("BuyMax", this.plugin.config.buyRegionMax));
                            return;
                        }
                        if (this.plugin.BuyMode.containsKey(playerName) || !this.plugin.config.requireBuyMode) {
                            double regionPrice = Double.parseDouble(sign.getLine(2));

                            String regionName = sign.getLine(1);
                            World world = sender.getWorld();

                            PluginsHook.PluginRegion region = this.plugin.pluginsHooks.getRegion(regionName, world.getName());

                            if (region == null) {
                                sender.sendMessage(ChatHelper.notice("RegionNoExist"));
                                return;
                            }

                            if (region.isOwner(sender)){
                                sender.sendMessage(ChatHelper.notice("CantSelf"));
                                return;
                            }

                            if (econ.getBalance(sender) >= regionPrice) {
                                EconomyResponse response = econ.withdrawPlayer(sender, regionPrice);
                                if (response.transactionSuccess()) {
                                    if (region.getOwners().size() > 0){
                                        double v = regionPrice / region.getOwners().size();
                                        region.getOwners().forEach(o-> econ.depositPlayer(Bukkit.getOfflinePlayer(o), v));
                                    }

                                    region.addOwner(sender);

                                    this.plugin.addBoughtRegionToCounts(playerName);

                                    sender.sendMessage(ChatHelper.notice("Purchased", regionName));
                                    sender.sendMessage(ChatHelper.notice("NewBalance", econ.getBalance(sender)));

                                    this.plugin.logActivity(playerName, " BUY " + regionName);

                                    sign.setLine(0, ChatColor.translateAlternateColorCodes('&', BuyRegion.instance.locale.get("SignSold")));
                                    sign.setLine(1, ChatColor.translateAlternateColorCodes('&', BuyRegion.instance.locale.get("SignSoldTo")));
                                    sign.setLine(2, ChatColor.WHITE + playerName);
                                    sign.setLine(3, ChatColor.translateAlternateColorCodes('&', BuyRegion.instance.locale.get("SignSold")));
                                    sign.update();

                                    this.plugin.BuyMode.remove(playerName);
                                } else {
                                    sender.sendMessage(ChatHelper.notice("TransFailed"));
                                }
                            } else {
                                sender.sendMessage(ChatHelper.warning("NotEnoughBuy"));
                                sender.sendMessage(ChatHelper.warning("Balance", econ.getBalance(sender)));
                            }
                        } else {
                            sender.sendMessage(ChatHelper.warning("BuyModeBuy"));
                            sender.sendMessage(ChatHelper.warning("ToEnterBuyMode"));
                        }
                    } else if (topLine.length() > 0 && (topLine.equalsIgnoreCase(this.plugin.config.signHeaderRent))) {
                        Player sender = event.getPlayer();
                        String regionName = sign.getLine(1);
                        String playerName = sender.getName();
                        if (this.plugin.config.requireRentPerms && !sender.hasPermission("buyregion.rent") && (!sender.isOp())) {
                            sender.sendMessage(ChatHelper.warning("RentPerms"));
                            return;
                        }
                        if (this.plugin.config.rentRegionMax > 0 && this.plugin.getRentedRegionsCount(playerName) >= this.plugin.config.rentRegionMax && !sender.isOp() && (!sender.hasPermission("buyregion.exempt"))) {
                            sender.sendMessage(ChatHelper.notice("RentMax", this.plugin.config.rentRegionMax));
                            return;
                        }
                        if (this.plugin.BuyMode.containsKey(playerName) || (!this.plugin.config.requireBuyMode)) {
                            if (regionName.length() > 0) {
                                String dateString = sign.getLine(3);
                                double regionPrice;

                                try {
                                    regionPrice = Double.parseDouble(sign.getLine(2));

                                    String[] expiration = dateString.split("\\s");
                                    int i = Integer.parseInt(expiration[0]);
                                    BuyRegion.DateResult dateResult = this.plugin.parseDateString(i, expiration[1]);
                                    if (dateResult.IsError) {
                                        throw new Exception();
                                    }
                                } catch(Exception e) {
                                    this.plugin.getLogger().info("Region price or expiration");
                                    sign.setLine(0, "-invalid-");
                                    sign.setLine(1, "<region here>");
                                    sign.setLine(2, "<price here>");
                                    sign.setLine(3, "<timespan>");
                                    sign.update();
                                    this.plugin.getLogger().info("Invalid [RentRegion] sign cleared at " + sign.getLocation().toString());
                                    return;
                                }
                                String[] expiration = sign.getLine(3).split("\\s");
                                BuyRegion.DateResult dateResult = this.plugin.parseDateString(Integer.parseInt(expiration[0]), expiration[1]);
                                if (dateResult.IsError) {
                                    throw new Exception();
                                }
                                World world = sender.getWorld();

                                PluginsHook.PluginRegion region = this.plugin.pluginsHooks.getRegion(regionName, world.getName());

                                if (region == null) {
                                    sender.sendMessage(ChatHelper.notice("RegionNoExist"));
                                    sign.setLine(0, "-invalid-");
                                    sign.setLine(1, "<region here>");
                                    sign.setLine(2, "<price here>");
                                    sign.setLine(3, "<timespan>");
                                    sign.update();
                                    this.plugin.getLogger().info("Invalid [RentRegion] sign cleared at " + sign.getLocation().toString());
                                    return;
                                }

                                if (region.isOwner(sender)){
                                    sender.sendMessage(ChatHelper.notice("CantSelf"));
                                    return;
                                }

                                if (econ.getBalance(sender) >= regionPrice) {
                                    EconomyResponse response = econ.withdrawPlayer(sender, regionPrice);
                                    if (response.transactionSuccess()) {
                                        if (this.plugin.config.payRentOwners){
                                            if (region.getOwners().size() > 0) {
                                                double v = regionPrice / region.getOwners().size();
                                                region.getOwners().forEach(o->econ.depositPlayer(Bukkit.getOfflinePlayer(o), v));
                                            }
                                        }

                                        region.addMember(sender);

                                        this.plugin.addRentedRegionFile(playerName, regionName, sign);

                                        this.plugin.addRentedRegionToCounts(playerName);

                                        this.plugin.logActivity(playerName, " RENT " + regionName);

                                        SimpleDateFormat sdf = new SimpleDateFormat(this.plugin.config.dateFormatString);

                                        sign.setLine(0, regionName);
                                        sign.setLine(1, playerName);
                                        sign.setLine(2, ChatColor.WHITE + BuyRegion.instance.locale.get("SignUntil"));
                                        sign.setLine(3, sdf.format(new Date(dateResult.Time)));
                                        sign.update();

                                        sender.sendMessage(ChatHelper.notice("Rented", regionName, sdf.format(new Date(dateResult.Time))));
                                        sender.sendMessage(ChatHelper.notice("NewBalance", econ.getBalance(sender)));

                                        this.plugin.rentedRegionExpirations.get().put(regionName, dateResult.Time);
                                        this.plugin.rentedRegionExpirations.save();

                                        this.plugin.BuyMode.remove(playerName);
                                    } else {
                                        sender.sendMessage(ChatHelper.warning("TransFailed"));
                                    }
                                } else {
                                    sender.sendMessage(ChatHelper.warning("NotEnoughRent"));
                                    sender.sendMessage(ChatHelper.warning("Balance", econ.getBalance(sender)));
                                }
                            }
                        } else {
                            sender.sendMessage(ChatHelper.warning("BuyModeRent"));
                            sender.sendMessage(ChatHelper.warning("ToEnterBuyMode"));
                        }
                    }
                }
            }
        } catch(Exception e) {
            this.plugin.getLogger().severe(e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void signChangeMonitor(SignChangeEvent event) {
        try {
            Player player = event.getPlayer();
            if (event.getLine(0).equalsIgnoreCase("[WGRSA]") || event.getLine(0).equalsIgnoreCase(this.plugin.config.signHeaderBuy) || (event.getLine(0).equalsIgnoreCase(this.plugin.config.signHeaderRent))) {
                if (!player.hasPermission("buyregion.create") && (!player.isOp())) {
                    event.setLine(0, "-restricted-");
                } else {
                    String regionName = event.getLine(1);
                    PluginsHook.PluginRegion region = (regionName != null && regionName.isEmpty())
                            ? this.plugin.pluginsHooks.getRegion(event.getBlock().getLocation())
                            : this.plugin.pluginsHooks.getRegion(regionName, event.getBlock().getWorld().getName());

                    if (region != null){
                        regionName = region.getName();
                        if (!region.isOwner(player) && !player.hasPermission("buyregion.admin")){
                            event.getPlayer().sendMessage(ChatHelper.warning("NotOwner"));
                            event.setLine(0, "-invalid-");
                            return;
                        }
                    } else if (!regionName.isEmpty()){
                        World world = event.getBlock().getWorld();
                        region = this.plugin.pluginsHooks.getRegion(regionName, world.getName());
                    }

                    if (region == null) {
                        event.getPlayer().sendMessage(ChatHelper.warning("RegionNoExist"));
                        event.setLine(0, "-invalid-");
                        return;
                    }

                    event.setLine(1, regionName);

                    try {
                        String dateString = event.getLine(3);
                        try {
                            double regionPrice = Double.parseDouble(event.getLine(2));
                            if (regionPrice <= 0.0D) {
                                throw new Exception();
                            }
                            if (event.getLine(0).equalsIgnoreCase(this.plugin.config.signHeaderRent)) {
                                String[] expiration = dateString.split("\\s");
                                int i = Integer.parseInt(expiration[0]);
                                BuyRegion.DateResult dateResult = this.plugin.parseDateString(i, expiration[1]);
                                if (dateResult.IsError) {
                                    throw new Exception();
                                }
                            }
                        } catch(Exception e) {
                            event.getPlayer().sendMessage(ChatHelper.notice("InvalidPriceTime"));
                            event.setLine(0, "-invalid-");

                            return;
                        }
                        if (!event.getLine(0).equalsIgnoreCase(this.plugin.config.signHeaderRent)) {
                            event.setLine(0, this.plugin.config.signHeaderBuy);
                        } else {
                            event.setLine(0, this.plugin.config.signHeaderRent);
                        }
                    } catch(Exception e) {
                        event.getPlayer().sendMessage(ChatHelper.notice("Invalid amount!"));
                        event.setLine(0, "-invalid-");
                        return;
                    }
                    event.getPlayer().sendMessage(ChatHelper.notice("A BuyRegion sign has been created!"));
                }
            }
        } catch(Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "An error occurred in signChangeMonitor", e);
        }
    }

    @EventHandler
    public void onBreakSign(BlockBreakEvent event) {
        try {
            Player player = event.getPlayer();

            if (player.isOp() || player.hasPermission("buyregion.admin")) {
                return;
            }

            Material blockType = event.getBlock().getType();

            if (!(blockType.name().endsWith("_SIGN") || blockType.name().endsWith("WALL_SIGN"))) {
                return;
            }

            Sign sign = (Sign) event.getBlock().getState();
            String topLine = sign.getLine(0);

            if (!(topLine.length() == 0 || !(topLine.equalsIgnoreCase(this.plugin.config.signHeaderBuy) && topLine.equalsIgnoreCase("[WGRSA]") || topLine.equalsIgnoreCase(this.plugin.config.signHeaderRent)))) {
                return;
            }

            String regionName = sign.getLine(1);
            World world = player.getWorld();
            PluginsHook.PluginRegion region = this.plugin.pluginsHooks.getRegion(regionName, world.getName());

            if (region == null) {
                return;
            }

            if (!player.hasPermission("buyregion.create") || !region.isOwner(player) || region.isMember(player)) {
                event.setCancelled(true);
            }
        } catch (Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "An error occurred in break sign event handler", e);
        }
    }
}
