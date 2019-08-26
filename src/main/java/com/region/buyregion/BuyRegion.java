package com.region.buyregion;

import com.region.buyregion.config.BuyRegionConfig;
import com.region.buyregion.config.DigiFile;
import com.region.buyregion.helpers.ChatHelper;
import com.region.buyregion.helpers.LocaleHelper;
import com.region.buyregion.listeners.RenterTask;
import com.region.buyregion.plugins.PluginsHook;
import com.region.buyregion.plugins.RedProtectHook;
import com.region.buyregion.plugins.WorldGuardHook;
import com.region.buyregion.regions.RentableRegion;
import com.sk89q.worldguard.WorldGuard;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BuyRegion extends JavaPlugin {
    public BuyRegionConfig config;
    public LocaleHelper locale;
    public static Economy econ = null;
    HashMap<String, Boolean> BuyMode = new HashMap<>();
    private DigiFile<HashMap<String, Integer>> regionCounts;
    private DigiFile<ConcurrentHashMap<String, Integer>> rentedRegionCounts;
    public DigiFile<ConcurrentHashMap<String, Long>> rentedRegionExpirations;
    public DigiFile<ConcurrentHashMap<String, Boolean>> autoRenews;
    public PluginsHook pluginsHooks;
    public PluginsHook getPluginsHooks(){
        return this.pluginsHooks;
    }

    public static BuyRegion instance;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("Original by Luke199");
        getLogger().info("Updated to 1.13 by GentleGravel");
        getLogger().info("Contributors:");
        getLogger().info("-> FabioZumbi12 (1.14 & RedProtect Integration)");

        try {
            if (!setupEconomy()) {
                getLogger().severe("No Vault-compatible economy plugin found!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            if (!setupPlugins()){
                getLogger().severe("No regions plugins found!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            config = new BuyRegionConfig();
            locale = new LocaleHelper();

            getServer().getPluginManager().registerEvents(new BuyRegionEvents(this), this);

            regionCounts = new DigiFile<>("RegionCounts", config.dataLoc, new HashMap<>());
            rentedRegionCounts = new DigiFile<>("RentedRegionCounts", config.dataLoc, new ConcurrentHashMap<>());
            rentedRegionExpirations = new DigiFile<>("RentedRegionExpirations", config.dataLoc, new ConcurrentHashMap<>());
            autoRenews = new DigiFile<>("AutoRenews", config.dataLoc, new ConcurrentHashMap<>());

            saveConfig();

            new RenterTask();

            File file = new File(config.dataLoc + "RegionActivityLog.txt");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch(IOException e) {
                    getLogger().log(Level.SEVERE, "Error creating log file", e);
                }
            }
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred while enabling BuyRegion", e);
        }
    }

    public void onDisable() {
        try {
            if (pluginsHooks != null) {
                regionCounts.save();
                rentedRegionExpirations.save();
                rentedRegionCounts.save();
                autoRenews.save();
            }

            getServer().getScheduler().cancelTasks(this);
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred during shutdown.", e);
        }
    }

    private boolean setupPlugins(){
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")){
            getLogger().info(String.format("Region Manager: WorldGuard %s (min 7.0.0 beta 3)", WorldGuard.getVersion()));
            pluginsHooks = new WorldGuardHook();
        } else if (Bukkit.getPluginManager().isPluginEnabled("RedProtect")){
            String version = Bukkit.getPluginManager().getPlugin("RedProtect").getDescription().getVersion();

            getLogger().info(String.format("Region Manager: RedProtect %s (min 7.6.0 b#200)", version));
            pluginsHooks = new RedProtectHook();
        }

        return this.pluginsHooks != null;
    }

    private void renewRental(String regionName, Player sender) {
        try {
            if (new File(config.signDataLoc + regionName + ".digi").exists() && (this.rentedRegionExpirations.get().containsKey(regionName))) {
                RentableRegion region = loadRegion(regionName);
                if (sender.getName().equalsIgnoreCase(region.renter)) {
                    double regionPrice = Double.parseDouble(region.signLine3);
                    if (econ.getBalance(sender) >= regionPrice) {
                        EconomyResponse response = econ.withdrawPlayer(sender, regionPrice);
                        if (response.transactionSuccess()) {
                            if (config.payRentOwners){
                                PluginsHook.PluginRegion pRegion = pluginsHooks.getRegion(regionName, sender.getWorld().getName());
                                if (pRegion != null && pRegion.getOwners().size() > 0) {
                                    double v = regionPrice / pRegion.getOwners().size();
                                    pRegion.getOwners().forEach(o->econ.depositPlayer(Bukkit.getOfflinePlayer(o), v));
                                }
                            }

                            String[] timeSpan = region.signLine4.split(" ");
                            long currentExpiration = this.rentedRegionExpirations.get().get(regionName);

                            DateResult timeData = parseDateString(Integer.parseInt(timeSpan[0]), timeSpan[1], currentExpiration);

                            this.rentedRegionExpirations.get().put(regionName, timeData.Time);
                            rentedRegionExpirations.save();

                            logActivity(sender.getName(), " RENEW " + regionName);

                            SimpleDateFormat sdf = new SimpleDateFormat(config.dateFormatString);

                            sender.sendMessage(ChatHelper.notice("Renewed", regionName, sdf.format(new Date(timeData.Time))));
                            sender.sendMessage(ChatHelper.notice("Balance", econ.getBalance(sender)));

                            World world = getServer().getWorld(region.worldName);

                            double x = Double.parseDouble(region.signLocationX);
                            double y = Double.parseDouble(region.signLocationY);
                            double z = Double.parseDouble(region.signLocationZ);
                            float pitch = Float.parseFloat(region.signLocationPitch);
                            float yaw = Float.parseFloat(region.signLocationYaw);

                            Location signLoc = new Location(world, x, y, z, pitch, yaw);

                            Block currentBlock = world.getBlockAt(signLoc);
                            if (currentBlock.getType().name().endsWith("_SIGN") || currentBlock.getType().name().endsWith("WALL_SIGN")) {
                                Sign theSign = (Sign) currentBlock.getState();

                                theSign.setLine(0, regionName);
                                theSign.setLine(1, sender.getName());
                                theSign.setLine(2, ChatColor.WHITE + BuyRegion.instance.locale.get("SignUntil"));
                                theSign.setLine(3, sdf.format(new Date(timeData.Time)));
                                theSign.update();

                                theSign.update();
                            }
                        } else {
                            sender.sendMessage(ChatHelper.notice("TransFailed"));
                        }
                    } else {
                        sender.sendMessage(ChatHelper.notice("NotEnoughRenew"));
                        sender.sendMessage(ChatHelper.notice("Balance", econ.getBalance(sender)));
                    }
                } else {
                    sender.sendMessage(ChatHelper.notice("NotRenting"));
                }
            } else {
                sender.sendMessage(ChatHelper.notice("NotRented", regionName));
            }
        } catch(Exception e) {
            getLogger().severe("An error has occurred while renewing rental for: " + regionName);
        }
    }

    public void logActivity(String player, String action) {
        try {
            Date tmp = new Date();
            File file = new File(config.dataLoc + "RegionActivityLog.txt");

            FileWriter out = new FileWriter(file, true);
            out.write(String.format("%s [%s] %s\r\n", tmp.toString(), player, action));
            out.flush();
            out.close();
        } catch(IOException e) {
            getLogger().log(Level.SEVERE, "An error occurred while trying to log activity.", e);
        }
    }

    int getBoughtRegionsCount(String playerName) {
        if (regionCounts.get().containsKey(playerName)) {
            return this.regionCounts.get().get(playerName);
        }
        return 0;
    }

    public void removeRentedRegionFromCount(String playerName) {
        try {
            if (this.rentedRegionCounts.get().containsKey(playerName)) {
                int amount = getRentedRegionsCount(playerName);
                if (amount > 0) {
                    amount--;
                }
                if (amount >= 0) {
                    this.rentedRegionCounts.get().put(playerName, amount);
                } else {
                    this.rentedRegionCounts.get().put(playerName, 0);
                }
                rentedRegionCounts.save();
            }
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred while removing a rented region from a player's count.", e);
        }
    }

    int getRentedRegionsCount(String playerName) {
        if (rentedRegionCounts.get().containsKey(playerName)) {
            return rentedRegionCounts.get().get(playerName);
        }
        return 0;
    }

    private void setBoughtRegionsCount(String playerName, int amount, CommandSender sender) {
        try {
            this.regionCounts.get().put(playerName, amount);
            this.regionCounts.save();
            sender.sendMessage(ChatHelper.notice(playerName + " bought regions set to " + amount));
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred in setBoughtRegions", e);
        }
    }

    private void setRentedRegionsCount(String playerName, int amount, CommandSender sender) {
        try {
            rentedRegionCounts.get().put(playerName, amount);
            rentedRegionCounts.save();
            sender.sendMessage(ChatHelper.notice(playerName + " rented regions set to " + amount));
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred in setRentedRegionsCount", e);
        }
    }

    void addRentedRegionFile(String playerName, String regionName, Sign sign) {
        RentableRegion region = new RentableRegion();

        Location tmpLoc = sign.getLocation();

        region.regionName = regionName;
        region.signLine1 = sign.getLine(0);
        region.signLine2 = sign.getLine(1);
        region.signLine3 = sign.getLine(2);
        region.signLine4 = sign.getLine(3);
        region.renter = playerName;
        region.signLocationX = String.valueOf(tmpLoc.getBlockX());
        region.signLocationY = String.valueOf(tmpLoc.getBlockY());
        region.signLocationZ = String.valueOf(tmpLoc.getBlockZ());
        region.signLocationPitch = String.valueOf(tmpLoc.getPitch());
        region.signLocationYaw = String.valueOf(tmpLoc.getYaw());
        region.signDirection = tmpLoc.getDirection().toString();
        region.worldName = sign.getWorld().getName();
        if (sign.getType().name().endsWith("WALL_SIGN")) {
            region.signType = Arrays.stream(Material.values()).filter(s->s.name().endsWith("WALL_SIGN")).findFirst().get().name();
        } else {
            region.signType = Arrays.stream(Material.values()).filter(s->s.name().endsWith("SIGN")).findFirst().get().name();
        }
        saveRentableRegion(region);
    }

    void addBoughtRegionToCounts(String playerName) {
        if (this.regionCounts.get().containsKey(playerName)) {
            this.regionCounts.get().put(playerName, getBoughtRegionsCount(playerName) + 1);
        } else {
            this.regionCounts.get().put(playerName, 1);
        }
        regionCounts.save();
    }

    void addRentedRegionToCounts(String playerName) {
        if (this.rentedRegionCounts.get().containsKey(playerName)) {
            this.rentedRegionCounts.get().put(playerName, getRentedRegionsCount(playerName) + 1);
        } else {
            this.rentedRegionCounts.get().put(playerName, 1);
        }
        rentedRegionCounts.save();
    }

    private void saveAutoRenews() {
        try {
            save(this.autoRenews, config.dataLoc, "autoRenews");
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error has occurred saving autoRenews", e);
        }
    }

    private void checkPlayerRentedRegionCount(String playerName, CommandSender sender) {
        if (this.rentedRegionCounts.get().containsKey(playerName)) {
            sender.sendMessage(ChatHelper.notice(playerName + " has " + getRentedRegionsCount(playerName) + " rented regions."));
        } else {
            sender.sendMessage(ChatHelper.notice(playerName + " has no rented regions."));
        }
    }

    private void checkPlayerRegionCount(String playerName, CommandSender sender) {
        if (this.regionCounts.get().containsKey(playerName)) {
            sender.sendMessage(ChatHelper.notice(playerName + " has " + getBoughtRegionsCount(playerName) + " bought regions."));
        } else {
            sender.sendMessage(ChatHelper.notice(playerName + " has no bought regions."));
        }
    }

    private void saveRegion(RentableRegion region) {
        save(region.toString(), config.signDataLoc, region.regionName);
    }

    public RentableRegion loadRegion(String regionName) {
        String tmp = (String) load(config.signDataLoc, regionName);

        return new RentableRegion(tmp);
    }

    private static void save(Object obj, String dataLoc, String file) {
        try {
            File f = new File(dataLoc + file + ".digi");
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            ObjectOutputStream tmp = new ObjectOutputStream(new FileOutputStream(f));
            tmp.writeObject(obj);
            tmp.flush();
            tmp.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static Object load(String dataLoc, String file) {
        try {
            ObjectInputStream tmp = new ObjectInputStream(new FileInputStream(dataLoc + file + ".digi"));
            Object rv = tmp.readObject();
            tmp.close();
            return rv;
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setAutoRenew(String playerName, boolean autoRenew) {
        if (autoRenew) {
            this.autoRenews.get().put(playerName, Boolean.TRUE);
            saveAutoRenews();
        } else {
            this.autoRenews.get().remove(playerName);
            saveAutoRenews();
        }
    }

    DateResult parseDateString(int val, String type) {
        try {
            Date tmp = new Date();
            if (type.equalsIgnoreCase("d") || type.equalsIgnoreCase("day") || (type.equalsIgnoreCase("days"))) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(tmp);
                cal.add(Calendar.DATE, val);

                return new DateResult(cal.getTime().getTime(), val + " days", false);
            }
            if (type.equalsIgnoreCase("h") || type.equalsIgnoreCase("hour") || (type.equalsIgnoreCase("hours"))) {
                return new DateResult(tmp.getTime() + val * 60 * 60 * 1000, val + " hours", false);
            }
            if (type.equalsIgnoreCase("m") || type.equalsIgnoreCase("mins") || type.equalsIgnoreCase("min") || type.equalsIgnoreCase("minutes") || (type.equalsIgnoreCase("minute"))) {
                return new DateResult(tmp.getTime() + val * 60 * 1000, val + " minutes", false);
            }
            if (type.equalsIgnoreCase("s") || type.equalsIgnoreCase("sec") || type.equalsIgnoreCase("secs") || type.equalsIgnoreCase("seconds") || (type.equalsIgnoreCase("second"))) {
                return new DateResult(tmp.getTime() + val * 1000, val + " seconds", false);
            }
            return new DateResult(-1L, "ERROR", true);
        } catch(Exception ignored) {}
        return new DateResult(-1L, "ERROR", true);
    }

    public DateResult parseDateString(int val, String type, long start) {
        try {
            Date tmp = new Date(start);
            if (type.equalsIgnoreCase("d") || type.equalsIgnoreCase("day") || (type.equalsIgnoreCase("days"))) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(tmp);
                cal.add(Calendar.DATE, val);

                return new DateResult(cal.getTime().getTime(), val + " days", false);
            }
            if (type.equalsIgnoreCase("h") || type.equalsIgnoreCase("hour") || (type.equalsIgnoreCase("hours"))) {
                return new DateResult(tmp.getTime() + val * 60 * 60 * 1000, val + " hours", false);
            }
            if (type.equalsIgnoreCase("m") || type.equalsIgnoreCase("mins") || type.equalsIgnoreCase("min") || type.equalsIgnoreCase("minutes") || (type.equalsIgnoreCase("minute"))) {
                return new DateResult(tmp.getTime() + val * 60 * 1000, val + " minutes", false);
            }
            if (type.equalsIgnoreCase("s") || type.equalsIgnoreCase("sec") || type.equalsIgnoreCase("secs") || type.equalsIgnoreCase("seconds") || (type.equalsIgnoreCase("second"))) {
                return new DateResult(tmp.getTime() + val * 1000, val + " seconds", false);
            }
            return new DateResult(-1L, "ERROR", true);
        } catch(Exception ignored) {}
        return new DateResult(-1L, "ERROR", true);
    }

    public class DateResult {
        public long Time;
        String Text;
        boolean IsError;

        DateResult(long time, String text, boolean isError) {
            this.Time = time;
            this.Text = text;
            this.IsError = isError;
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null)
            return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
            return false;
        econ = rsp.getProvider();
        return true;
    }

    private void toggleBuyMode(CommandSender sender) {
        try {
            String playerName = sender.getName();
            if (!this.BuyMode.containsKey(playerName)) {
                this.BuyMode.put(sender.getName(), Boolean.TRUE);
                sender.sendMessage(ChatHelper.notice("BuyModeEnter"));
            } else {
                this.BuyMode.remove(playerName);
                sender.sendMessage(ChatHelper.notice("BuyModeExit"));
            }
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred in toggleBuyMode", e);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("buyregion")) {
            if (args.length == 0) {
                toggleBuyMode(sender);
            } else {
                if (args[0].equalsIgnoreCase("renew") && sender instanceof Player) {
                    if (args.length < 2) {
                        sender.sendMessage(ChatHelper.notice("InvalidRenewArgs"));
                    } else {
                        renewRental(args[1], (Player) sender);
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("autorenew")) {
                    if (args.length < 2) {
                        if (this.autoRenews.get().containsKey(sender.getName())) {
                            if (this.autoRenews.get().get(sender.getName())) {
                                sender.sendMessage(ChatHelper.notice("RenewOn"));
                            } else {
                                sender.sendMessage(ChatHelper.notice("RenewOff"));
                            }
                        } else {
                            sender.sendMessage(ChatHelper.notice("RenewOff"));
                        }
                    } else if (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("yes") || (args[1].equalsIgnoreCase("on"))) {
                        setAutoRenew(sender.getName(), true);
                        sender.sendMessage(ChatHelper.notice("RenewTurnOn"));
                    } else if (args[1].equalsIgnoreCase("false") || args[1].equalsIgnoreCase("no") || (args[1].equalsIgnoreCase("off"))) {
                        setAutoRenew(sender.getName(), false);
                        sender.sendMessage(ChatHelper.notice("RenewTurnOff"));
                    } else {
                        sender.sendMessage(ChatHelper.notice("InvalidArg"));
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("help")) {
                    String[] help = { ChatHelper.notice("Help1"), ChatHelper.notice("Help2"), ChatHelper.notice("Help3"), ChatHelper.notice("Help4") };
                    sender.sendMessage(help);
                }
                if (sender.isOp() || (sender.hasPermission("buyregion.admin"))) {
                    if (args[0].equalsIgnoreCase("reload")) {
                        reloadConfig();
                        config = new BuyRegionConfig();
                        locale = new LocaleHelper();
                        sender.sendMessage(ChatHelper.notice("BuyRegion reloaded"));
                    } else if (args[0].equalsIgnoreCase("buycheck")) {
                        checkPlayerRegionCount(args[1], sender);
                    } else if (args[0].equalsIgnoreCase("rentcheck")) {
                        checkPlayerRentedRegionCount(args[1], sender);
                    } else if (args[0].equalsIgnoreCase("buyset")) {
                        if (args.length < 3) {
                            sender.sendMessage(ChatHelper.warning("Invalid args - /buyregion buyset <player> <amount>"));
                        } else {
                            int amount;

                            try {
                                amount = Integer.parseInt(args[2]);
                                if (amount < 0) {
                                    amount = 0;
                                }
                            } catch(Exception e) {
                                sender.sendMessage(ChatHelper.warning("Invalid amount. Enter a number for the amount."));
                                return false;
                            }
                            setBoughtRegionsCount(args[1], amount, sender);
                        }
                    } else if (args[0].equalsIgnoreCase("rentset")) {
                        if (args.length < 3) {
                            sender.sendMessage(ChatHelper.warning("Invalid args - /buyregion rentset <player> <amount>"));
                        } else {
                            int amount;
                            try {
                                amount = Integer.parseInt(args[2]);
                                if (amount < 0)
                                amount = 0;
                            } catch(Exception e) {
                                sender.sendMessage(ChatHelper.warning("Invalid amount. Enter a number for the amount."));
                                return false;
                            }
                            setRentedRegionsCount(args[1], amount, sender);
                        }
                    } else {
                        if (args[0].equalsIgnoreCase("buymax")) {
                            try {
                                if (args.length < 2) {
                                    sender.sendMessage(ChatHelper.notice("Current config.buyRegionMax: " + this.config.buyRegionMax));
                                } else {
                                    int amount;

                                    try {
                                        amount = Integer.parseInt(args[1]);
                                        if (amount < 0)
                                        amount = 0;
                                    } catch(Exception e) {
                                        sender.sendMessage(ChatHelper.warning("Invalid amount. Enter a number for the amount."));
                                        return false;
                                    }
                                    this.config.buyRegionMax = amount;
                                    getConfig().set("config.buyRegionMax", amount);
                                    saveConfig();

                                    sender.sendMessage(ChatHelper.notice("config.buyRegionMax has been updated to " + amount));
                                }
                            } catch(Exception e) {
                                sender.sendMessage("An error occurred... check all values and try again.");
                            }
                        }
                        if (args[0].equalsIgnoreCase("rentmax")) {
                            try {
                                if (args.length < 2) {
                                    sender.sendMessage(ChatHelper.notice("Current RentRegionMax: " + config.rentRegionMax));
                                } else {
                                    int amount;

                                    try {
                                        amount = Integer.parseInt(args[1]);
                                        if (amount < 0)
                                        amount = 0;
                                    } catch(Exception e) {
                                        sender.sendMessage(ChatHelper.warning("Invalid amount. Enter a number for the amount."));
                                        return false;
                                    }
                                    config.rentRegionMax = amount;
                                    getConfig().set("RentRegionMax", amount);
                                    saveConfig();

                                    sender.sendMessage(ChatHelper.warning("RentRegionMax has been updated to " + amount));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(ChatHelper.warning("An error occurred... check all values and try again."));
                            }
                        }
                        if (args[0].equalsIgnoreCase("buyperms")) {
                            try {
                                if (args.length > 1) {
                                    if (args[1].equalsIgnoreCase("true") || (args[1].equalsIgnoreCase("false"))) {
                                        boolean val = Boolean.parseBoolean(args[1]);
                                        if (val) {
                                            config.requireBuyPerms = true;
                                            getConfig().set("RequireBuyPerms", Boolean.TRUE);
                                        } else {
                                            config.requireBuyPerms = false;
                                            getConfig().set("RequireBuyPerms", Boolean.FALSE);
                                        }
                                        sender.sendMessage(ChatHelper.notice("RequireBuyPerms set."));
                                        saveConfig();
                                    } else {
                                        sender.sendMessage(ChatHelper.warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                } else {
                                    sender.sendMessage(ChatHelper.notice("RequireBuyPerms: " + getConfig().getBoolean("RequireBuyPerms")));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(ChatHelper.warning("An error occurred... Syntax: /buyregion buyperms true/false"));
                                return false;
                            }
                        } else if (args[0].equalsIgnoreCase("rentperms")) {
                            try {
                                if (args.length > 1) {
                                    if (args[1].equalsIgnoreCase("true") || (args[1].equalsIgnoreCase("false"))) {
                                        boolean val = Boolean.parseBoolean(args[1]);
                                        if (val) {
                                            config.requireRentPerms = true;
                                            getConfig().set("RequireRentPerms", Boolean.TRUE);
                                        } else {
                                            config.requireRentPerms = false;
                                            getConfig().set("RequireRentPerms", Boolean.FALSE);
                                        }
                                        sender.sendMessage(ChatHelper.notice("RequireRentPerms set."));
                                        saveConfig();
                                    } else {
                                        sender.sendMessage(ChatHelper.warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                } else {
                                    sender.sendMessage(ChatHelper.notice("RequireRentPerms: " + getConfig().getBoolean("RequireRentPerms")));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(ChatHelper.warning("An error occurred... Syntax: /buyregion rentperms true/false"));
                                return false;
                            }
                        } else if (args[0].equalsIgnoreCase("buymode")) {
                            try {
                                if (args.length > 1) {
                                    if (args[1].equalsIgnoreCase("true") || (args[1].equalsIgnoreCase("false"))) {
                                        boolean val = Boolean.parseBoolean(args[1]);
                                        if (val) {
                                            config.requireBuyMode = true;
                                            getConfig().set("RequireBuyMode", Boolean.TRUE);
                                        } else {
                                            config.requireBuyMode = false;
                                            getConfig().set("RequireBuyMode", Boolean.FALSE);
                                        }
                                        sender.sendMessage(ChatHelper.notice("RequireBuyMode set."));
                                        saveConfig();
                                    } else {
                                        sender.sendMessage(ChatHelper.warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                } else {
                                    sender.sendMessage(ChatHelper.notice("RequireBuyMode: " + getConfig().getBoolean("RequireBuyMode")));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(ChatHelper.warning("An error occurred... Syntax: /buyregion buymode true/false"));
                                return false;
                            }
                        } else if (args[0].equalsIgnoreCase("evict")) {
                            if (args.length > 1) {
                                String regionName = args[1];
                                if (new File(config.signDataLoc + regionName + ".digi").exists()) {
                                    if (evictRegion(regionName)) {
                                        sender.sendMessage(ChatHelper.notice("Region eviction completed!"));
                                    } else {
                                        sender.sendMessage(ChatHelper.warning("Region eviction failed."));
                                    }
                                } else {
                                    sender.sendMessage(ChatHelper.warning("Region is not currently rented!"));
                                }
                            } else {
                                sender.sendMessage(ChatHelper.warning("Invalid syntax: /buyregion evict <region>"));
                                return false;
                            }
                        } else {
                            String[] help = { ChatHelper.notice("Admin Commands:"), ChatHelper.notice("/buyregion buymode <true/false> - sets RequireBuyMode"), ChatHelper.notice("/buyregion buycheck <player> - checks total bought regions for <player>"), ChatHelper.notice("/buyregion rentcheck <player> - checks total rented regions for <player>"), ChatHelper.notice("/buyregion buyset <player> <amount> - sets total bought regions for <player>"), ChatHelper.notice("/buyregion rentset <player> <amount> - sets total rented regions for <player>"), ChatHelper.notice("/buyregion buymax - displays current config.buyRegionMax"), ChatHelper.notice("/buyregion buymax <amount> - sets config.buyRegionMax"), ChatHelper.notice("/buyregion rentmax - displays current RentRegionMax"), ChatHelper.notice("/buyregion rentmax <amount> - sets RentRegionMax"), ChatHelper.notice("/buyregion evict <region> - evicts renter from <region>") };
                            sender.sendMessage(help);
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean evictRegion(String regionName) {
        try {
            RentableRegion rentedRegion = loadRegion(regionName);

            this.rentedRegionExpirations.get().remove(regionName);
            rentedRegionExpirations.save();

            World world = getServer().getWorld(rentedRegion.worldName);
            PluginsHook.PluginRegion region = pluginsHooks.getRegion(regionName, rentedRegion.worldName);

            if (region == null)
            return false;

            region.removeMember(rentedRegion.renter);

            removeRentedRegionFromCount(rentedRegion.renter);

            double x = Double.parseDouble(rentedRegion.signLocationX);
            double y = Double.parseDouble(rentedRegion.signLocationY);
            double z = Double.parseDouble(rentedRegion.signLocationZ);
            float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
            float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

            Location signLoc = new Location(world, x, y, z, pitch, yaw);

            Block currentBlock = world.getBlockAt(signLoc);
            if (currentBlock.getType().name().endsWith("_SIGN") || currentBlock.getType().name().endsWith("WALL_SIGN")) {
                Sign theSign = (Sign) currentBlock.getState();

                theSign.setLine(0, rentedRegion.signLine1);
                theSign.setLine(1, rentedRegion.signLine2);
                theSign.setLine(2, rentedRegion.signLine3);
                theSign.setLine(3, rentedRegion.signLine4);

                theSign.update();
            } else {
                try {
                    if (rentedRegion.signType.endsWith("WALL_SIGN")) {
                        currentBlock.setType(Arrays.stream(Material.values()).filter(s->s.name().endsWith("WALL_SIGN")).findFirst().get());
                    } else {
                        currentBlock.setType(Arrays.stream(Material.values()).filter(s->s.name().endsWith("_SIGN")).findFirst().get());
                    }
                    Sign newSign = (Sign) currentBlock.getState();

                    newSign.setLine(0, rentedRegion.signLine1);
                    newSign.setLine(1, rentedRegion.signLine2);
                    newSign.setLine(2, rentedRegion.signLine3);
                    newSign.setLine(3, rentedRegion.signLine4);

                    newSign.update();
                } catch(Exception e) {
                    getLogger().severe("RentRegion automatic sign creation failed for region " + rentedRegion.regionName);
                }
            }
            File regionFile = new File(config.signDataLoc + regionName + ".digi");
            if (regionFile.exists()) {
                regionFile.delete();
            }
            Player player = getServer().getPlayer(rentedRegion.renter);
            if ((player != null)) {
                player.sendMessage(ChatHelper.notice("EvictedFrom", regionName));
            }
            logActivity(rentedRegion.renter, " EVICTED " + rentedRegion.regionName);

            return true;
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred during an eviction.", e);
        }
        return false;
    }

    private void saveRentableRegion(RentableRegion region) {
        try {
            saveRegion(region);
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error has occurred saving a RentableRegion.", e);
        }
    }
}

