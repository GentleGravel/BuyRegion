package com.region.buyregion;

import com.region.buyregion.config.BuyRegionConfig;
import com.region.buyregion.config.DigiFile;
import com.region.buyregion.regions.RentableRegion;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldGuardPlatform;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

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

public final class BuyRegion
    extends JavaPlugin implements Listener {
    private BuyRegionConfig config;
    private static Economy econ = null;
    private HashMap<String, Boolean> BuyMode = new HashMap<>();
    private DigiFile<HashMap<String, Integer>> regionCounts;
    private DigiFile<ConcurrentHashMap<String, Integer>> rentedRegionCounts;
    private DigiFile<ConcurrentHashMap<String, Long>> rentedRegionExpirations;
    private DigiFile<ConcurrentHashMap<String, Boolean>> autoRenews;
    private Messages messages = new Messages();

    public static BuyRegion instance;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("HI");

        try {
            if (!setupEconomy()) {
                getLogger().severe("No Vault-compatible economy plugin found!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            config = new BuyRegionConfig();

            getServer().getPluginManager().registerEvents(this, this);

            getLogger().info("Maintained by Luke199");
            getLogger().info("Updated to 1.13 by GentleGravel");

            messages = new Messages();
            messages.init();

            regionCounts = new DigiFile<>("RegionCounts", config.dataLoc, new HashMap<>());
            rentedRegionCounts = new DigiFile<>("RentedRegionCounts", config.dataLoc, new ConcurrentHashMap<>());
            rentedRegionExpirations = new DigiFile<>("RentedRegionExpirations", config.dataLoc, new ConcurrentHashMap<>());
            autoRenews = new DigiFile<>("AutoRenews", config.dataLoc, new ConcurrentHashMap<>());

            saveConfig();

            scheduleRenterTask();

            File file = new File(config.dataLoc + "RegionActivityLog.txt");
            if (!file.exists()) {
                try {
                    file.createNewFile();
                } catch(IOException e) {
                    getLogger().severe("Error creating log file");
                }
            }
        } catch(Exception e) {
            getLogger().log(Level.SEVERE, "An error occurred while enabling BuyRegion", e);
        }
    }

    public void onDisable() {
        try {
            regionCounts.save();
            rentedRegionExpirations.save();
            rentedRegionCounts.save();
            autoRenews.save();

            getServer().getScheduler().cancelTasks(this);
        } catch(Exception e) {
            getLogger().severe("An error occurred during shutdown.");
        }
    }

    private String getMessage(String key) {
        return this.messages.get(key);
    }

    private void scheduleRenterTask() {
        getServer().getScheduler().scheduleSyncRepeatingTask(
            this,
            () -> {
                try {
                    long now = new Date().getTime();
                    ConcurrentHashMap<String, Long> expirations = rentedRegionExpirations.get();

                    for (String regionName : expirations.keySet()) {
                        if (expirations.containsKey(regionName)) {
                            long regionExp = expirations.get(regionName);
                            if (regionExp <= now) {
                                boolean renewed = false;

                                RentableRegion rentedRegion = loadRegion(regionName);
                                if (autoRenews.get().containsKey(rentedRegion.renter)) {
                                    if (autoRenews.get().get(rentedRegion.renter)) {
                                        Player player = getServer().getPlayer(rentedRegion.renter);

                                        double regionPrice = Double.parseDouble(rentedRegion.signLine3);
                                        if (BuyRegion.econ.getBalance(rentedRegion.renter) >= regionPrice) {
                                            EconomyResponse response = BuyRegion.econ.withdrawPlayer(rentedRegion.renter, regionPrice);
                                            if (response.transactionSuccess()) {
                                                renewed = true;

                                                String[] timeSpan = rentedRegion.signLine4.split(" ");
                                                long currentExpiration = expirations.get(regionName);

                                                DateResult timeData = parseDateString(Integer.parseInt(timeSpan[0]), timeSpan[1], currentExpiration);
                                                expirations.put(regionName, timeData.Time);
                                                rentedRegionExpirations.save();

                                                logActivity(rentedRegion.renter, " AUTORENEW " + regionName);

                                                SimpleDateFormat sdf = new SimpleDateFormat(config.dateFormatString);
                                                if (player != null) {
                                                    player.sendMessage(Notice(getMessage("Renewed") + " " + regionName + " -> " + sdf.format(new Date(timeData.Time))));
                                                    player.sendMessage(Notice(getMessage("NewBalance") + " " + BuyRegion.econ.getBalance(rentedRegion.renter)));
                                                }
                                                World world = getServer().getWorld(rentedRegion.worldName);

                                                double x = Double.parseDouble(rentedRegion.signLocationX);
                                                double y = Double.parseDouble(rentedRegion.signLocationY);
                                                double z = Double.parseDouble(rentedRegion.signLocationZ);
                                                float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
                                                float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

                                                Location signLoc = new Location(world, x, y, z, pitch, yaw);

                                                Block currentBlock = world.getBlockAt(signLoc);
                                                if (currentBlock.getType() == Material.SIGN || (currentBlock.getType() == Material.WALL_SIGN)) {
                                                    Sign theSign = (Sign) currentBlock.getState();

                                                    theSign.setLine(0, regionName);
                                                    theSign.setLine(1, rentedRegion.renter);
                                                    theSign.setLine(2, ChatColor.WHITE + "Until:");
                                                    theSign.setLine(3, sdf.format(new Date(timeData.Time)));
                                                    theSign.update();

                                                    theSign.update();
                                                }
                                            }
                                        } else if (player != null) {
                                            player.sendMessage(Notice(getMessage("NotEnoughRenew") + " " + regionName + "!"));
                                            player.sendMessage(Notice(getMessage("Balance") + " " + BuyRegion.econ.getBalance(rentedRegion.renter)));
                                        }
                                    }
                                }
                                if (!renewed) {
                                    expirations.remove(regionName);
                                    rentedRegionExpirations.save();

                                    World world = getServer().getWorld(rentedRegion.worldName);
                                    ProtectedRegion region = getWorldGuardRegion(rentedRegion.worldName, regionName);

                                    if (region == null)
                                    return;
                                    DefaultDomain dd = region.getMembers();

                                    dd.removePlayer(rentedRegion.renter);

                                    region.setMembers(dd);

                                    removeRentedRegionFromCount(rentedRegion.renter);

                                    double x = Double.parseDouble(rentedRegion.signLocationX);
                                    double y = Double.parseDouble(rentedRegion.signLocationY);
                                    double z = Double.parseDouble(rentedRegion.signLocationZ);
                                    float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
                                    float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

                                    Location signLoc = new Location(world, x, y, z, pitch, yaw);

                                    Block currentBlock = world.getBlockAt(signLoc);
                                    if (currentBlock.getType() == Material.SIGN || (currentBlock.getType() == Material.WALL_SIGN)) {
                                        Sign theSign = (Sign) currentBlock.getState();

                                        theSign.setLine(0, rentedRegion.signLine1);
                                        theSign.setLine(1, rentedRegion.signLine2);
                                        theSign.setLine(2, rentedRegion.signLine3);
                                        theSign.setLine(3, rentedRegion.signLine4);

                                        theSign.update();
                                    } else {
                                        try {
                                            if (rentedRegion.signType == "WALL_SIGN") {
                                                currentBlock.setType(Material.WALL_SIGN);
                                            } else {
                                                currentBlock.setType(Material.SIGN);
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
                                        player.sendMessage(Notice(getMessage("Expired") + " " + regionName));
                                    }
                                    logActivity(rentedRegion.renter, " EXPIRED " + rentedRegion.regionName);
                                }
                            }
                        }
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            },
            config.tickRate,
            config.tickRate
        );
    }

    private void renewRental(String regionName, String playerName, CommandSender sender) {
        try {
            if (new File(config.signDataLoc + regionName + ".digi").exists() && (this.rentedRegionExpirations.get().containsKey(regionName))) {
                RentableRegion region = loadRegion(regionName);
                if (sender.getName().equalsIgnoreCase(region.renter)) {
                    double regionPrice = Double.parseDouble(region.signLine3);
                    if (econ.getBalance(playerName) >= regionPrice) {
                        EconomyResponse response = econ.withdrawPlayer(playerName, regionPrice);
                        if (response.transactionSuccess()) {
                            String[] timeSpan = region.signLine4.split(" ");
                            long currentExpiration = this.rentedRegionExpirations.get().get(regionName);

                            DateResult timeData = parseDateString(Integer.parseInt(timeSpan[0]), timeSpan[1], currentExpiration);

                            this.rentedRegionExpirations.get().put(regionName, timeData.Time);
                            rentedRegionExpirations.save();

                            logActivity(playerName, " RENEW " + regionName);

                            SimpleDateFormat sdf = new SimpleDateFormat(config.dateFormatString);

                            sender.sendMessage(Notice(getMessage("Renewed") + " " + regionName + " until " + sdf.format(new Date(timeData.Time))));
                            sender.sendMessage(Notice(getMessage("Balance") + " " + econ.getBalance(playerName)));

                            World world = getServer().getWorld(region.worldName);

                            double x = Double.parseDouble(region.signLocationX);
                            double y = Double.parseDouble(region.signLocationY);
                            double z = Double.parseDouble(region.signLocationZ);
                            float pitch = Float.parseFloat(region.signLocationPitch);
                            float yaw = Float.parseFloat(region.signLocationYaw);

                            Location signLoc = new Location(world, x, y, z, pitch, yaw);

                            Block currentBlock = world.getBlockAt(signLoc);
                            if (currentBlock.getType() == Material.SIGN || (currentBlock.getType() == Material.WALL_SIGN)) {
                                Sign theSign = (Sign) currentBlock.getState();

                                theSign.setLine(0, regionName);
                                theSign.setLine(1, playerName);
                                theSign.setLine(2, ChatColor.WHITE + "Until:");
                                theSign.setLine(3, sdf.format(new Date(timeData.Time)));
                                theSign.update();

                                theSign.update();
                            }
                        } else {
                            sender.sendMessage(Notice(getMessage("TransFailed")));
                        }
                    } else {
                        sender.sendMessage(Notice(getMessage("NotEnoughRenew")));
                        sender.sendMessage(Notice(getMessage("Balance") + " " + econ.getBalance(playerName)));
                    }
                } else {
                    sender.sendMessage(Notice(getMessage("NotRenting")));
                }
            } else {
                sender.sendMessage(Notice(regionName + " " + getMessage("NotRented")));
            }
        } catch(Exception e) {
            getLogger().severe("An error has occurred while renewing rental for: " + regionName);
        }
    }

    private void logActivity(String player, String action) {
        try {
            Date tmp = new Date();
            File file = new File(config.dataLoc + "RegionActivityLog.txt");

            FileWriter out = new FileWriter(file, true);
            out.write(tmp.toString() + " [" + player + "] " + action + "\r\n");
            out.flush();
            out.close();
        } catch(IOException e) {
            getLogger().severe("An error occurred while trying to log activity.");
        }
    }

    private int getBoughtRegionsCount(String playerName) {
        if (regionCounts.get().containsKey(playerName)) {
            return this.regionCounts.get().get(playerName);
        }

        return 0;
    }

    private void removeRentedRegionFromCount(String playerName) {
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
            getLogger().severe("An error occurred while removing a rented region from a player's count.");
        }
    }

    private int getRentedRegionsCount(String playerName) {
        if (rentedRegionCounts.get().containsKey(playerName)) {
            return rentedRegionCounts.get().get(playerName);
        }
        return 0;
    }

    private void setBoughtRegionsCount(String playerName, int amount, CommandSender sender) {
        try {
            this.regionCounts.get().put(playerName, amount);
            this.regionCounts.save();
            sender.sendMessage(Notice(playerName + " bought regions set to " + amount));
        } catch(Exception e) {
            getLogger().severe("An error occurred in setBoughtRegions");
        }
    }

    private void setRentedRegionsCount(String playerName, int amount, CommandSender sender) {
        try {
            rentedRegionCounts.get().put(playerName, amount);
            rentedRegionCounts.save();
            sender.sendMessage(Notice(playerName + " rented regions set to " + amount));
        } catch(Exception e) {
            getLogger().severe("An error occurred in setRentedRegionsCount");
        }
    }

    private void addRentedRegionFile(String playerName, String regionName, Sign sign) {
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
        if (sign.getType() == Material.WALL_SIGN) {
            region.signType = "WALL_SIGN";
        } else {
            region.signType = "SIGN";
        }
        saveRentableRegion(region);
    }

    private void addBoughtRegionToCounts(String playerName) {
        if (this.regionCounts.get().containsKey(playerName)) {
            this.regionCounts.get().put(playerName, getBoughtRegionsCount(playerName) + 1);
        } else {
            this.regionCounts.get().put(playerName, 1);
        }
        regionCounts.save();
    }

    private void addRentedRegionToCounts(String playerName) {
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
            getLogger().severe("An error has occurred saving autoRenews");
        }
    }

    private void checkPlayerRentedRegionCount(String playerName, CommandSender sender) {
        if (this.rentedRegionCounts.get().containsKey(playerName)) {
            sender.sendMessage(Notice(playerName + " has " + getRentedRegionsCount(playerName) + " rented regions."));
        } else {
            sender.sendMessage(Notice(playerName + " has no rented regions."));
        }
    }

    private void checkPlayerRegionCount(String playerName, CommandSender sender) {
        if (this.regionCounts.get().containsKey(playerName)) {
            sender.sendMessage(Notice(playerName + " has " + getBoughtRegionsCount(playerName) + " bought regions."));
        } else {
            sender.sendMessage(Notice(playerName + " has no bought regions."));
        }
    }

    private void saveRegion(RentableRegion region) {
        save(region.toString(), config.signDataLoc, region.regionName);
    }

    private RentableRegion loadRegion(String regionName) {
        String tmp = (String) load(config.signDataLoc, regionName);

        return new RentableRegion(tmp);
    }

    private static void save(Object obj, String dataLoc, String file) {
        try {
            ObjectOutputStream tmp = new ObjectOutputStream(new FileOutputStream(dataLoc + file + ".digi"));
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

    private String Notice(String msg) {
        return ChatColor.AQUA + "[BuyRegion] " + ChatColor.YELLOW + msg;
    }

    private String Warning(String msg) {
        return ChatColor.RED + "[BuyRegion] " + ChatColor.YELLOW + msg;
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

    @EventHandler
    public void onPunchSign(PlayerInteractEvent event) {
        try {
            if (event.getAction().name().equals("RIGHT_CLICK_BLOCK")) {
                Material blockType = event.getClickedBlock().getType();
                if (blockType == Material.SIGN || (blockType == Material.WALL_SIGN)) {
                    Sign sign = (Sign) event.getClickedBlock().getState();

                    String topLine = sign.getLine(0);
                    if (topLine.length() > 0 && (topLine.equalsIgnoreCase("[BuyRegion]") || (topLine.equalsIgnoreCase("[WGRSA]")))) {
                        Player sender = event.getPlayer();
                        String playerName = sender.getName();
                        if (topLine.equalsIgnoreCase("[WGRSA]")) {
                            sign.setLine(0, "[BuyRegion]");
                            sign.update();
                        }
                        if (config.requireBuyPerms && !sender.hasPermission("buyregion.buy") && (!sender.isOp())) {
                            sender.sendMessage(Notice(getMessage("BuyPerms")));
                            return;
                        }
                        if (this.config.buyRegionMax > 0 && getBoughtRegionsCount(playerName) >= this.config.buyRegionMax && !sender.isOp() && (!sender.hasPermission("buyregion.exempt"))) {
                            sender.sendMessage(Notice(getMessage("BuyMax") + " " + this.config.buyRegionMax));
                            return;
                        }
                        if (this.BuyMode.containsKey(playerName) || (!config.requireBuyMode)) {
                            double regionPrice = Double.parseDouble(sign.getLine(2));

                            String regionName = sign.getLine(1);
                            World world = sender.getWorld();

                            DefaultDomain dd = new DefaultDomain();
                            dd.addPlayer(playerName);

                            RegionManager rm = getWorldGuardRegionManager(world.getName());
                            ProtectedRegion region = rm.getRegion(regionName);

                            if (region == null) {
                                sender.sendMessage(Notice(getMessage("RegionNoExist")));
                                return;
                            }
                            if (econ.getBalance(playerName) >= regionPrice) {
                                EconomyResponse response = econ.withdrawPlayer(playerName, regionPrice);
                                if (response.transactionSuccess()) {
                                    region.setOwners(dd);
                                    rm.save();

                                    addBoughtRegionToCounts(playerName);

                                    sender.sendMessage(Notice(getMessage("Purchased") + " " + regionName + "!"));
                                    sender.sendMessage(Notice(getMessage("NewBalance") + " " + econ.getBalance(playerName)));

                                    logActivity(playerName, " BUY " + regionName);

                                    sign.setLine(0, ChatColor.RED + "## SOLD ##");
                                    sign.setLine(1, ChatColor.BLACK + "Sold To:");
                                    sign.setLine(2, ChatColor.WHITE + playerName);
                                    sign.setLine(3, ChatColor.RED + "## SOLD ##");
                                    sign.update();

                                    this.BuyMode.remove(playerName);
                                } else {
                                    sender.sendMessage(Notice(getMessage("TransFailed")));
                                }
                            } else {
                                sender.sendMessage(Warning(getMessage("NotEnoughBuy")));
                                sender.sendMessage(Warning(getMessage("Balance") + " " + econ.getBalance(playerName)));
                            }
                        } else {
                            sender.sendMessage(Warning(getMessage("BuyModeBuy")));
                            sender.sendMessage(Warning(getMessage("ToEnterBuyMode")));
                        }
                    } else if (topLine.length() > 0 && (topLine.equalsIgnoreCase("[RentRegion]"))) {
                        Player sender = event.getPlayer();
                        String regionName = sign.getLine(1);
                        String playerName = sender.getName();
                        if (config.requireRentPerms && !sender.hasPermission("buyregion.rent") && (!sender.isOp())) {
                            sender.sendMessage(Warning(getMessage("RentPerms")));
                            return;
                        }
                        if (config.rentRegionMax > 0 && getRentedRegionsCount(playerName) >= config.rentRegionMax && !sender.isOp() && (!sender.hasPermission("buyregion.exempt"))) {
                            sender.sendMessage(Notice(getMessage("RentMax") + " " + config.rentRegionMax));
                            return;
                        }
                        if (this.BuyMode.containsKey(playerName) || (!config.requireBuyMode)) {
                            if (regionName.length() > 0) {
                                String dateString = sign.getLine(3);
                                double regionPrice;

                                try {
                                    regionPrice = Double.parseDouble(sign.getLine(2));

                                    String[] expiration = dateString.split("\\s");
                                    int i = Integer.parseInt(expiration[0]);
                                    DateResult dateResult = parseDateString(i, expiration[1]);
                                    if (dateResult.IsError) {
                                        throw new Exception();
                                    }
                                } catch(Exception e) {
                                    getLogger().info("Region price or expiration");
                                    sign.setLine(0, "-invalid-");
                                    sign.setLine(1, "<region here>");
                                    sign.setLine(2, "<price here>");
                                    sign.setLine(3, "<timespan>");
                                    sign.update();
                                    getLogger().info("Invalid [RentRegion] sign cleared at " + sign.getLocation().toString());
                                    return;
                                }
                                String[] expiration = sign.getLine(3).split("\\s");
                                DateResult dateResult = parseDateString(Integer.parseInt(expiration[0]), expiration[1]);
                                if (dateResult.IsError) {
                                    throw new Exception();
                                }
                                World world = sender.getWorld();

                                RegionManager rm = getWorldGuardRegionManager(world.getName());
                                ProtectedRegion region = rm.getRegion(regionName);

                                DefaultDomain dd = new DefaultDomain();
                                dd.addPlayer(playerName);

                                if (region == null) {
                                    sender.sendMessage(Notice(getMessage("RegionNoExist")));
                                    sign.setLine(0, "-invalid-");
                                    sign.setLine(1, "<region here>");
                                    sign.setLine(2, "<price here>");
                                    sign.setLine(3, "<timespan>");
                                    sign.update();
                                    getLogger().info("Invalid [RentRegion] sign cleared at " + sign.getLocation().toString());

                                    return;
                                }
                                if (econ.getBalance(playerName) >= regionPrice) {
                                    EconomyResponse response = econ.withdrawPlayer(playerName, regionPrice);
                                    if (response.transactionSuccess()) {
                                        region.setMembers(dd);
                                        rm.save();

                                        addRentedRegionFile(playerName, regionName, sign);

                                        addRentedRegionToCounts(playerName);

                                        logActivity(playerName, " RENT " + regionName);

                                        SimpleDateFormat sdf = new SimpleDateFormat(config.dateFormatString);

                                        sign.setLine(0, regionName);
                                        sign.setLine(1, playerName);
                                        sign.setLine(2, ChatColor.WHITE + "Until:");
                                        sign.setLine(3, sdf.format(new Date(dateResult.Time)));
                                        sign.update();

                                        sender.sendMessage(Notice(getMessage("Rented") + " " + regionName + " -> " + sdf.format(new Date(dateResult.Time))));
                                        sender.sendMessage(Notice(getMessage("NewBalance") + " " + econ.getBalance(playerName)));

                                        this.rentedRegionExpirations.get().put(regionName, dateResult.Time);
                                        rentedRegionExpirations.save();

                                        this.BuyMode.remove(playerName);
                                    } else {
                                        sender.sendMessage(Warning(getMessage("TransFailed")));
                                    }
                                } else {
                                    sender.sendMessage(Warning(getMessage("NotEnoughRent")));
                                    sender.sendMessage(Warning(getMessage("Balance") + " " + econ.getBalance(playerName)));
                                }
                            }
                        } else {
                            sender.sendMessage(Warning(getMessage("BuyModeRent")));
                            sender.sendMessage(Warning(getMessage("ToEnterBuyMode")));
                        }
                    }
                }
            }
        } catch(Exception e) {
            getLogger().severe(e.getMessage());
        }
    }

    private DateResult parseDateString(int val, String type) {
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

    private DateResult parseDateString(int val, String type, long start) {
        try {
            Date tmp = new Date(start);
            if (type.equalsIgnoreCase("d") || type.equalsIgnoreCase("day") || (type.equalsIgnoreCase("days"))) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(tmp);
                cal.add(5, val);

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
        long Time;
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

        return econ != null;
    }

    private void toggleBuyMode(CommandSender sender) {
        try {
            String playerName = sender.getName();
            if (!this.BuyMode.containsKey(playerName)) {
                this.BuyMode.put(sender.getName(), Boolean.valueOf(true));
                sender.sendMessage(Notice("BuyModeEnter"));
            } else {
                this.BuyMode.remove(playerName);
                sender.sendMessage(Notice(getMessage("BuyModeExit")));
            }
        } catch(Exception e) {
            getLogger().severe("An error occurred in toggleBuyMode");
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("buyregion")) {
            if (args.length == 0) {
                toggleBuyMode(sender);
            } else {
                if (args[0].equalsIgnoreCase("renew")) {
                    if (args.length < 2) {
                        sender.sendMessage(Notice(getMessage("InvalidRenewArgs")));
                    } else {
                        renewRental(args[1], sender.getName(), sender);
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("autorenew")) {
                    if (args.length < 2) {
                        if (this.autoRenews.get().containsKey(sender.getName())) {
                            if (this.autoRenews.get().get(sender.getName())) {
                                sender.sendMessage(Notice(getMessage("RenewOn")));
                            } else {
                                sender.sendMessage(Notice(getMessage("RenewOff")));
                            }
                        } else {
                            sender.sendMessage(Notice(getMessage("RenewOff")));
                        }
                    } else if (args[1].equalsIgnoreCase("true") || args[1].equalsIgnoreCase("yes") || (args[1].equalsIgnoreCase("on"))) {
                        setAutoRenew(sender.getName(), true);
                        sender.sendMessage(Notice(getMessage("RenewTurnOn")));
                    } else if (args[1].equalsIgnoreCase("false") || args[1].equalsIgnoreCase("no") || (args[1].equalsIgnoreCase("off"))) {
                        setAutoRenew(sender.getName(), false);
                        sender.sendMessage(Notice(getMessage("RenewTurnOff")));
                    } else {
                        sender.sendMessage(Notice(getMessage("InvalidArg")));
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("help")) {
                    String[] help = { Notice(getMessage("Help1")), Notice(getMessage("Help2")), Notice(getMessage("Help3")), Notice(getMessage("Help4")) };
                    sender.sendMessage(help);
                }
                if (sender.isOp() || (sender.hasPermission("buyregion.admin"))) {
                    if (args[0].equalsIgnoreCase("buycheck")) {
                        checkPlayerRegionCount(args[1], sender);
                    } else if (args[0].equalsIgnoreCase("rentcheck")) {
                        checkPlayerRentedRegionCount(args[1], sender);
                    } else if (args[0].equalsIgnoreCase("buyset")) {
                        if (args.length < 3) {
                            sender.sendMessage(Warning("Invalid args - /buyregion buyset <player> <amount>"));
                        } else {
                            int amount;

                            try {
                                amount = Integer.parseInt(args[2]);
                                if (amount < 0) {
                                    amount = 0;
                                }
                            } catch(Exception e) {
                                sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                return false;
                            }
                            setBoughtRegionsCount(args[1], amount, sender);
                        }
                    } else if (args[0].equalsIgnoreCase("rentset")) {
                        if (args.length < 3) {
                            sender.sendMessage(Warning("Invalid args - /buyregion rentset <player> <amount>"));
                        } else {
                            int amount;
                            try {
                                amount = Integer.parseInt(args[2]);
                                if (amount < 0)
                                amount = 0;
                            } catch(Exception e) {
                                sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                return false;
                            }
                            setRentedRegionsCount(args[1], amount, sender);
                        }
                    } else {
                        if (args[0].equalsIgnoreCase("buymax")) {
                            try {
                                if (args.length < 2) {
                                    sender.sendMessage(Notice("Current config.buyRegionMax: " + this.config.buyRegionMax));
                                } else {
                                    int amount;

                                    try {
                                        amount = Integer.parseInt(args[1]);
                                        if (amount < 0)
                                        amount = 0;
                                    } catch(Exception e) {
                                        sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                        return false;
                                    }
                                    this.config.buyRegionMax = amount;
                                    getConfig().set("config.buyRegionMax", amount);
                                    saveConfig();

                                    sender.sendMessage(Notice("config.buyRegionMax has been updated to " + amount));
                                }
                            } catch(Exception e) {
                                sender.sendMessage("An error occurred... check all values and try again.");
                            }
                        }
                        if (args[0].equalsIgnoreCase("rentmax")) {
                            try {
                                if (args.length < 2) {
                                    sender.sendMessage(Notice("Current RentRegionMax: " + config.rentRegionMax));
                                } else {
                                    int amount;

                                    try {
                                        amount = Integer.parseInt(args[1]);
                                        if (amount < 0)
                                        amount = 0;
                                    } catch(Exception e) {
                                        sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                        return false;
                                    }
                                    config.rentRegionMax = amount;
                                    getConfig().set("RentRegionMax", Integer.valueOf(amount));
                                    saveConfig();

                                    sender.sendMessage(Warning("RentRegionMax has been updated to " + amount));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(Warning("An error occurred... check all values and try again."));
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
                                        sender.sendMessage(Notice("RequireBuyPerms set."));
                                        saveConfig();
                                    } else {
                                        sender.sendMessage(Warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                } else {
                                    sender.sendMessage(Notice("RequireBuyPerms: " + getConfig().getBoolean("RequireBuyPerms")));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(Warning("An error occurred... Syntax: /buyregion buyperms true/false"));
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
                                        sender.sendMessage(Notice("RequireRentPerms set."));
                                        saveConfig();
                                    } else {
                                        sender.sendMessage(Warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                } else {
                                    sender.sendMessage(Notice("RequireRentPerms: " + getConfig().getBoolean("RequireRentPerms")));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(Warning("An error occurred... Syntax: /buyregion rentperms true/false"));
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
                                        sender.sendMessage(Notice("RequireBuyMode set."));
                                        saveConfig();
                                    } else {
                                        sender.sendMessage(Warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                } else {
                                    sender.sendMessage(Notice("RequireBuyMode: " + getConfig().getBoolean("RequireBuyMode")));
                                }
                            } catch(Exception e) {
                                sender.sendMessage(Warning("An error occurred... Syntax: /buyregion buymode true/false"));
                                return false;
                            }
                        } else if (args[0].equalsIgnoreCase("evict")) {
                            if (args.length > 1) {
                                String regionName = args[1];
                                if (new File(config.signDataLoc + regionName + ".digi").exists()) {
                                    if (evictRegion(regionName)) {
                                        sender.sendMessage(Notice("Region eviction completed!"));
                                    } else {
                                        sender.sendMessage(Warning("Region eviction failed."));
                                    }
                                } else {
                                    sender.sendMessage(Warning("Region is not currently rented!"));
                                }
                            } else {
                                sender.sendMessage(Warning("Invalid syntax: /buyregion evict <region>"));
                                return false;
                            }
                        } else {
                            String[] help = { Notice("Admin Commands:"), Notice("/buyregion buymode <true/false> - sets RequireBuyMode"), Notice("/buyregion buycheck <player> - checks total bought regions for <player>"), Notice("/buyregion rentcheck <player> - checks total rented regions for <player>"), Notice("/buyregion buyset <player> <amount> - sets total bought regions for <player>"), Notice("/buyregion rentset <player> <amount> - sets total rented regions for <player>"), Notice("/buyregion buymax - displays current config.buyRegionMax"), Notice("/buyregion buymax <amount> - sets config.buyRegionMax"), Notice("/buyregion rentmax - displays current RentRegionMax"), Notice("/buyregion rentmax <amount> - sets RentRegionMax"), Notice("/buyregion evict <region> - evicts renter from <region>") };
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
            ProtectedRegion region = getWorldGuardRegion(rentedRegion.worldName, regionName);

            if (region == null)
            return false;
            DefaultDomain dd = region.getMembers();

            dd.removePlayer(rentedRegion.renter);

            region.setMembers(dd);

            removeRentedRegionFromCount(rentedRegion.renter);

            double x = Double.parseDouble(rentedRegion.signLocationX);
            double y = Double.parseDouble(rentedRegion.signLocationY);
            double z = Double.parseDouble(rentedRegion.signLocationZ);
            float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
            float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

            Location signLoc = new Location(world, x, y, z, pitch, yaw);

            Block currentBlock = world.getBlockAt(signLoc);
            if (currentBlock.getType() == Material.SIGN || (currentBlock.getType() == Material.WALL_SIGN)) {
                Sign theSign = (Sign) currentBlock.getState();

                theSign.setLine(0, rentedRegion.signLine1);
                theSign.setLine(1, rentedRegion.signLine2);
                theSign.setLine(2, rentedRegion.signLine3);
                theSign.setLine(3, rentedRegion.signLine4);

                theSign.update();
            } else {
                try {
                    if (rentedRegion.signType == "WALL_SIGN") {
                        currentBlock.setType(Material.WALL_SIGN);
                    } else {
                        currentBlock.setType(Material.SIGN);
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
                player.sendMessage(Notice(getMessage("EvictedFrom") + " " + regionName));
            }
            logActivity(rentedRegion.renter, " EVICTED " + rentedRegion.regionName);

            return true;
        } catch(Exception e) {
            getLogger().severe("An error occurred during an eviction.");
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void signChangeMonitor(SignChangeEvent event) {
        try {
            Player player = event.getPlayer();
            if (event.getLine(0).equalsIgnoreCase("[WGRSA]") || event.getLine(0).equalsIgnoreCase("[BuyRegion]") || (event.getLine(0).equalsIgnoreCase("[RentRegion]"))) {
                if (!player.hasPermission("buyregion.create") && (!player.isOp())) {
                    event.setLine(0, "-restricted-");
                } else {
                    String regionName = event.getLine(1);
                    World world = event.getBlock().getWorld();
                    ProtectedRegion region = getWorldGuardRegion(world.getName(), regionName);

                    if (region == null) {
                        event.getPlayer().sendMessage(Warning(getMessage("RegionNoExist")));

                        event.setLine(0, "-invalid-");
                        return;
                    }
                    try {
                        String dateString = event.getLine(3);
                        try {
                            double regionPrice = Double.parseDouble(event.getLine(2));
                            if (regionPrice <= 0.0D) {
                                throw new Exception();
                            }
                            if (event.getLine(0).equalsIgnoreCase("[RentRegion]")) {
                                String[] expiration = dateString.split("\\s");
                                int i = Integer.parseInt(expiration[0]);
                                DateResult dateResult = parseDateString(i, expiration[1]);
                                if (dateResult.IsError) {
                                    throw new Exception();
                                }
                            }
                        } catch(Exception e) {
                            event.getPlayer().sendMessage(Notice(getMessage("InvalidPriceTime")));
                            event.setLine(0, "-invalid-");

                            return;
                        }
                        if (!event.getLine(0).equalsIgnoreCase("[RentRegion]")) {
                            event.setLine(0, "[BuyRegion]");
                        } else {
                            event.setLine(0, "[RentRegion]");
                        }
                    } catch(Exception e) {
                        event.getPlayer().sendMessage(Notice("Invalid amount!"));
                        event.setLine(0, "-invalid-");
                        return;
                    }
                    event.getPlayer().sendMessage(Notice("A BuyRegion sign has been created!"));
                }
            }
        } catch(Exception e) {
            getLogger().severe("An error occurred in signChangeMonitor");
        }
    }

    private void saveRentableRegion(RentableRegion region) {
        try {
            saveRegion(region);
        } catch(Exception e) {
            getLogger().severe("An error has occurred saving a RentableRegion.");
        }
    }

    private RegionManager getWorldGuardRegionManager(String world) {
        BukkitWorldGuardPlatform wgPlatform = (BukkitWorldGuardPlatform) WorldGuard.getInstance().getPlatform();
        com.sk89q.worldedit.world.World wgWorld = wgPlatform.getWorldByName(world);
        return wgPlatform.getRegionContainer().get(wgWorld);
    }

    private ProtectedRegion getWorldGuardRegion(String world, String regionName) {
        RegionManager regionManager = getWorldGuardRegionManager(world);

        return regionManager != null ? regionManager.getRegion(regionName) : null;
    }
}

