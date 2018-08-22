package com.region.buyregion;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class BuyRegion extends JavaPlugin implements Listener {
    static final String dataLoc = "plugins" + File.separator + "BuyRegion" + File.separator;
    static final String signDataLoc = "plugins" + File.separator + "BuyRegion" + File.separator + "rent" + File.separator;
    static final String rentedRE = "RentedRegionExpirations";
    static final String rentedRC = "RentedRegionCounts";
    static final String autoRenewsFileName = "AutoRenews";
    static long tickRate = 60L;
    public static Economy econ = null;
    public int buyRegionMax = 0;
    public int rentRegionMax = 0;
    public boolean requireBuyMode = true;
    public boolean requireBuyPerms = false;
    public boolean requireRentPerms = false;
    public String dateFormatString = "yy/MM/dd h:mma";
    public HashMap<String, Boolean> BuyMode = new HashMap();
    public HashMap<String, Integer> RegionCounts = new HashMap();
    public ConcurrentHashMap<String, Integer> RentedRegionCounts = new ConcurrentHashMap();
    public ConcurrentHashMap<String, Long> RentedRegionExpirations = new ConcurrentHashMap();
    public ConcurrentHashMap<String, Boolean> AutoRenews = new ConcurrentHashMap();
    public ConcurrentHashMap<String, String> Messages = new ConcurrentHashMap();

    public void onEnable()
    {
        try
        {
            new File(dataLoc).mkdirs();
            new File(signDataLoc).mkdirs();

            getServer().getPluginManager().registerEvents(this, this);

            getLogger().info("BuyRegion - Maintained by Luke199");
            if (!setupEconomy())
            {
                getLogger().info("Disabled due to no Vault dependency found!");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            this.Messages = getDefaultMessages();

            getConfig().options().copyDefaults(true);

            loadRegionCounts();
            loadRentedRegionExpirations();
            loadRentedRegionCounts();
            loadAutoRenews();
            try
            {
                this.buyRegionMax = getConfig().getInt("BuyRegionMax", 0);
            }
            catch (Exception e)
            {
                this.buyRegionMax = 0;
            }
            try
            {
                this.rentRegionMax = getConfig().getInt("RentRegionMax", 0);
            }
            catch (Exception e)
            {
                this.rentRegionMax = 0;
            }
            try
            {
                this.requireBuyMode = getConfig().getBoolean("RequireBuyMode", true);
            }
            catch (Exception e)
            {
                this.requireBuyMode = true;
            }
            try
            {
                tickRate = getConfig().getLong("CheckExpirationsInMins");tickRate = tickRate * 60L * 20L;
            }
            catch (Exception e)
            {
                tickRate = 6000L;
            }
            try
            {
                this.requireBuyPerms = getConfig().getBoolean("RequireBuyPerms", false);
            }
            catch (Exception e)
            {
                this.requireBuyPerms = false;
            }
            try
            {
                this.requireRentPerms = getConfig().getBoolean("RequireRentPerms", false);
            }
            catch (Exception e)
            {
                this.requireRentPerms = false;
            }
            try
            {
                setFormatString(getConfig().getString("DateFormat", "Default"));
            }
            catch (Exception e)
            {
                this.dateFormatString = "yy/MM/dd h:mma";
            }
            loadMessageConfig();

            saveConfig();

            scheduleRenterTask();

            File file = new File(dataLoc + "RegionActivityLog.txt");
            if (!file.exists()) {
                try
                {
                    file.createNewFile();
                }
                catch (IOException e)
                {
                    getLogger().info("Error creating log file");
                }
            }
            return;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void loadMessageConfig()
    {
        this.Messages.put("InvalidPriceTime", getConfig().getString("Messages.InvalidPriceTime", "Invalid sign. Invalid price or timespan!"));
        this.Messages.put("RegionNoExist", getConfig().getString("Messages.RegionNoExist", "Invalid sign. Region doesn't exist!"));
        this.Messages.put("EvictedFrom", getConfig().getString("Messages.EvictedFrom", "You have been evicted from"));
        this.Messages.put("InvalidArg", getConfig().getString("Messages.InvalidArg", "Invalid argument. Accepted values: true, false, yes, no, off, on"));
        this.Messages.put("RenewTurnOff", getConfig().getString("Messages.RenewTurnOff", "Auto-Renew has been turned OFF for you."));
        this.Messages.put("RenewTurnOn", getConfig().getString("Messages.RenewTurnOn", "Auto-Renew has been turned ON for you."));
        this.Messages.put("RenewOff", getConfig().getString("Messages.RenewOff", "Auto-Renew is off!"));
        this.Messages.put("RenewOn", getConfig().getString("Messages.RenewOn", "Auto-Renew is on!"));
        this.Messages.put("InvalidRenewArgs", getConfig().getString("Messages.InvalidRenewArgs", "Invalid args: /buyregion renew <region>"));
        this.Messages.put("BuyModeExit", getConfig().getString("Messages.BuyModeExit", "You have exited Buy Mode."));
        this.Messages.put("BuyModeEnter", getConfig().getString("Messages.BuyModeEnter", "You are now in Buy Mode - right-click the BuyRegion sign!"));
        this.Messages.put("ToEnterBuyMode", getConfig().getString("Messages.ToEnterBuyMode", "To enter Buy Mode type: /buyregion"));
        this.Messages.put("BuyModeRent", getConfig().getString("Messages.BuyModeRent", "You must be in Buy Mode to rent this region!"));
        this.Messages.put("BuyModeBuy", getConfig().getString("Messages.BuyModeBuy", "You must be in Buy Mode to buy this region!"));
        this.Messages.put("NotEnoughRent", getConfig().getString("Messages.NotEnoughRent", "Not enough money to rent this region!"));
        this.Messages.put("NotEnoughBuy", getConfig().getString("Messages.NotEnoughBuy", "Not enough money to buy this region!"));
        this.Messages.put("TransFailed", getConfig().getString("Messages.TransFailed", "Transaction Failed!"));
        this.Messages.put("Rented", getConfig().getString("Messages.Rented", "Congrats! You have rented"));
        this.Messages.put("NewBalance", getConfig().getString("Messages.NewBalance", "Your new balance is"));
        this.Messages.put("RentMax", getConfig().getString("Messages.RentMax", "You are not allowed to rent more regions. Max"));
        this.Messages.put("RentPerms", getConfig().getString("Messages.RentPerms", "You do not have permission to rent regions."));
        this.Messages.put("Balance", getConfig().getString("Messages.Balance", "Your balance is"));
        this.Messages.put("Purchased", getConfig().getString("Messages.Purchased", "Congrats! You have purchased"));
        this.Messages.put("BuyMax", getConfig().getString("Messages.BuyMax", "You are not allowed to buy more regions. Max"));
        this.Messages.put("BuyPerms", getConfig().getString("Messages.BuyPerms", "You do not have permission to buy regions."));
        this.Messages.put("NotRented", getConfig().getString("Messages.NotRented", "is not currently rented."));
        this.Messages.put("NotRenting", getConfig().getString("Messages.NotRenting", "You are not renting this region. You cannot renew it!"));
        this.Messages.put("NotEnoughRenew", getConfig().getString("Messages.NotEnoughRenew", "Not enough money to renew rental for this region!"));
        this.Messages.put("Renewed", getConfig().getString("Messages.Renewed", "Congrats! You have renewed"));
        this.Messages.put("Expired", getConfig().getString("Messages.Expired", "Your rental has expired for region"));
        this.Messages.put("Help1", getConfig().getString("Messages.Help1", "BuyRegion Commands"));
        this.Messages.put("Help2", getConfig().getString("Messages.Help2", "/buyregion - toggles buy mode"));
        this.Messages.put("Help3", getConfig().getString("Messages.Help3", "/buyregion renew <region> - renews rental of <region>"));
        this.Messages.put("Help4", getConfig().getString("Messages.Help4", "/buyregion autorenew <true/false> - sets auto-renew for all rented regions"));
    }

    public void onDisable()
    {
        try
        {
            saveRegionCounts();
            saveRentedRegionExpirations();
            saveRentedRegionCounts();
            saveAutoRenews();

            getServer().getScheduler().cancelTasks(this);
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred during shutdown.");
        }
    }

    private ConcurrentHashMap<String, String> getDefaultMessages()
    {
        ConcurrentHashMap<String, String> msgs = new ConcurrentHashMap();

        msgs.put("InvalidPriceTime", "Invalid sign. Invalid price or timespan!");
        msgs.put("RegionNoExist", "Invalid sign. Region doesn't exist!");
        msgs.put("EvictedFrom", "You have been evicted from");
        msgs.put("InvalidArg", "Invalid argument. Accepted values: true, false, yes, no, off, on");
        msgs.put("RenewTurnOff", "Auto-Renew has been turned OFF for you.");
        msgs.put("RenewTurnOn", "Auto-Renew has been turned ON for you.");
        msgs.put("RenewOff", "Auto-Renew is off!");
        msgs.put("RenewOn", "Auto-Renew is on!");
        msgs.put("InvalidRenewArgs", "Invalid args: /buyregion renew <region>");
        msgs.put("BuyModeExit", "You have exited Buy Mode.");
        msgs.put("BuyModeEnter", "You are now in Buy Mode - right-click the BuyRegion sign!");
        msgs.put("ToEnterBuyMode", "To enter Buy Mode type: /buyregion");
        msgs.put("BuyModeRent", "You must be in Buy Mode to rent this region!");
        msgs.put("BuyModeBuy", "You must be in Buy Mode to buy this region!");
        msgs.put("NotEnoughRent", "Not enough money to rent this region!");
        msgs.put("NotEnoughBuy", "Not enough money to buy this region!");
        msgs.put("TransFailed", "Transaction Failed!");
        msgs.put("Rented", "Congrats! You have rented");
        msgs.put("NewBalance", "Your new balance is");
        msgs.put("RentMax", "You are not allowed to rent more regions. Max");
        msgs.put("RentPerms", "You do not have permission to rent regions.");
        msgs.put("Balance", "Your balance is");
        msgs.put("Purchased", "Congrats! You have purchased");
        msgs.put("BuyMax", "You are not allowed to buy more regions. Max");
        msgs.put("BuyPerms", "You do not have permission to buy regions.");
        msgs.put("NotRented", "is not currently rented.");
        msgs.put("NotRenting", "You are not renting this region. You cannot renew it!");
        msgs.put("NotEnoughRenew", "Not enough money to renew rental for this region!");
        msgs.put("Renewed", "Congrats! You have renewed");
        msgs.put("Expired", "Your rental has expired for region");
        msgs.put("Help1", "BuyRegion Commands");
        msgs.put("Help2", "/buyregion - toggles buy mode");
        msgs.put("Help3", "/buyregion renew <region> - renews rental of <region>");
        msgs.put("Help4", "/buyregion autorenew <true/false> - sets auto-renew for all rented regions");

        return msgs;
    }

    private String getMessage(String key)
    {
        return (String)this.Messages.get(key);
    }

    public void scheduleRenterTask()
    {
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
        {
            public void run()
            {
                try
                {
                    long now = new Date().getTime();
                    for (String regionName : BuyRegion.this.RentedRegionExpirations.keySet()) {
                        if (BuyRegion.this.RentedRegionExpirations.containsKey(regionName))
                        {
                            long regionExp = BuyRegion.this.RentedRegionExpirations.get(regionName);
                            if (regionExp <= now)
                            {
                                boolean renewed = false;

                                BuyRegion.RentableRegion rentedRegion = BuyRegion.this.loadRegion(regionName);
                                if (BuyRegion.this.AutoRenews.containsKey(rentedRegion.renter)) {
                                    if (BuyRegion.this.AutoRenews.get(rentedRegion.renter))
                                    {
                                        Player player = BuyRegion.this.getServer().getPlayer(rentedRegion.renter);

                                        Double regionPrice = Double.valueOf(Double.parseDouble(rentedRegion.signLine3));
                                        if (BuyRegion.econ.getBalance(rentedRegion.renter) >= regionPrice.doubleValue())
                                        {
                                            EconomyResponse response = BuyRegion.econ.withdrawPlayer(rentedRegion.renter, regionPrice.doubleValue());
                                            if (response.transactionSuccess())
                                            {
                                                renewed = true;

                                                String[] timeSpan = rentedRegion.signLine4.split(" ");

                                                long currentExpiration = ((Long)BuyRegion.this.RentedRegionExpirations.get(regionName)).longValue();

                                                BuyRegion.DateResult timeData = BuyRegion.this.parseDateString(Integer.parseInt(timeSpan[0]), timeSpan[1], currentExpiration);

                                                BuyRegion.this.RentedRegionExpirations.put(regionName, Long.valueOf(timeData.Time));
                                                BuyRegion.this.saveRentedRegionExpirations();

                                                BuyRegion.this.logActivity(rentedRegion.renter, " AUTORENEW " + regionName);

                                                SimpleDateFormat sdf = new SimpleDateFormat(BuyRegion.this.dateFormatString);
                                                if ((player instanceof Player))
                                                {
                                                    player.sendMessage(BuyRegion.this.Notice(BuyRegion.this.getMessage("Renewed") + " " + regionName + " -> " + sdf.format(new Date(timeData.Time))));
                                                    player.sendMessage(BuyRegion.this.Notice(BuyRegion.this.getMessage("NewBalance") + " " + BuyRegion.econ.getBalance(rentedRegion.renter)));
                                                }
                                                World world = BuyRegion.this.getServer().getWorld(rentedRegion.worldName);

                                                double x = Double.parseDouble(rentedRegion.signLocationX);
                                                double y = Double.parseDouble(rentedRegion.signLocationY);
                                                double z = Double.parseDouble(rentedRegion.signLocationZ);
                                                float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
                                                float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

                                                Location signLoc = new Location(world, x, y, z, pitch, yaw);

                                                Block currentBlock = world.getBlockAt(signLoc);
                                                if ((currentBlock.getType() == Material.SIGN_POST) || (currentBlock.getType() == Material.WALL_SIGN))
                                                {
                                                    Sign theSign = (Sign)currentBlock.getState();

                                                    theSign.setLine(0, regionName);
                                                    theSign.setLine(1, rentedRegion.renter);
                                                    theSign.setLine(2, ChatColor.WHITE + "Until:");
                                                    theSign.setLine(3, sdf.format(new Date(timeData.Time)));
                                                    theSign.update();

                                                    theSign.update();
                                                }
                                            }
                                        }
                                        else if (player != null)
                                        {
                                            player.sendMessage(BuyRegion.this.Notice(BuyRegion.this.getMessage("NotEnoughRenew") + " " + regionName + "!"));
                                            player.sendMessage(BuyRegion.this.Notice(BuyRegion.this.getMessage("Balance") + " " + BuyRegion.econ.getBalance(rentedRegion.renter)));
                                        }
                                    }
                                }
                                if (!renewed)
                                {
                                    BuyRegion.this.RentedRegionExpirations.remove(regionName);
                                    BuyRegion.this.saveRentedRegionExpirations();

                                    World world = BuyRegion.this.getServer().getWorld(rentedRegion.worldName);
                                    RegionManager rm = BuyRegion.this.getWorldGuard().getRegionManager(world);
                                    ProtectedRegion region = rm.getRegion(regionName);
                                    DefaultDomain dd = region.getMembers();

                                    dd.removePlayer(rentedRegion.renter);

                                    region.setMembers(dd);

                                    BuyRegion.this.removeRentedRegionFromCount(rentedRegion.renter);

                                    double x = Double.parseDouble(rentedRegion.signLocationX);
                                    double y = Double.parseDouble(rentedRegion.signLocationY);
                                    double z = Double.parseDouble(rentedRegion.signLocationZ);
                                    float pitch = Float.parseFloat(rentedRegion.signLocationPitch);
                                    float yaw = Float.parseFloat(rentedRegion.signLocationYaw);

                                    Location signLoc = new Location(world, x, y, z, pitch, yaw);

                                    Block currentBlock = world.getBlockAt(signLoc);
                                    if ((currentBlock.getType() == Material.SIGN_POST) || (currentBlock.getType() == Material.WALL_SIGN))
                                    {
                                        Sign theSign = (Sign)currentBlock.getState();

                                        theSign.setLine(0, rentedRegion.signLine1);
                                        theSign.setLine(1, rentedRegion.signLine2);
                                        theSign.setLine(2, rentedRegion.signLine3);
                                        theSign.setLine(3, rentedRegion.signLine4);

                                        theSign.update();
                                    }
                                    else
                                    {
                                        try
                                        {
                                            if (rentedRegion.signType == "WALL_SIGN") {
                                                currentBlock.setType(Material.WALL_SIGN);
                                            } else {
                                                currentBlock.setType(Material.SIGN_POST);
                                            }
                                            Sign newSign = (Sign)currentBlock.getState();

                                            newSign.setLine(0, rentedRegion.signLine1);
                                            newSign.setLine(1, rentedRegion.signLine2);
                                            newSign.setLine(2, rentedRegion.signLine3);
                                            newSign.setLine(3, rentedRegion.signLine4);

                                            newSign.update();
                                        }
                                        catch (Exception e)
                                        {
                                            BuyRegion.this.getLogger().info("RentRegion automatic sign creation failed for region " + rentedRegion.regionName);
                                        }
                                    }
                                    File regionFile = new File(BuyRegion.signDataLoc + regionName + ".digi");
                                    if (regionFile.exists()) {
                                        regionFile.delete();
                                    }
                                    Player player = BuyRegion.this.getServer().getPlayer(rentedRegion.renter);
                                    if ((player != null)) {
                                        player.sendMessage(BuyRegion.this.Notice(BuyRegion.this.getMessage("Expired") + " " + regionName));
                                    }
                                    BuyRegion.this.logActivity(rentedRegion.renter, " EXPIRED " + rentedRegion.regionName);
                                }
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        }, tickRate, tickRate);
    }

    public void renewRental(String regionName, String playerName, CommandSender sender)
    {
        try
        {
            if ((new File(signDataLoc + regionName + ".digi").exists()) && (this.RentedRegionExpirations.containsKey(regionName)))
            {
                RentableRegion region = loadRegion(regionName);
                if (sender.getName().equalsIgnoreCase(region.renter))
                {
                    double regionPrice = Double.parseDouble(region.signLine3);
                    if (econ.getBalance(playerName) >= regionPrice)
                    {
                        EconomyResponse response = econ.withdrawPlayer(playerName, regionPrice);
                        if (response.transactionSuccess())
                        {
                            String[] timeSpan = region.signLine4.split(" ");

                            long currentExpiration = this.RentedRegionExpirations.get(regionName);

                            DateResult timeData = parseDateString(Integer.parseInt(timeSpan[0]), timeSpan[1], currentExpiration);

                            this.RentedRegionExpirations.put(regionName, timeData.Time);
                            saveRentedRegionExpirations();

                            logActivity(playerName, " RENEW " + regionName);

                            SimpleDateFormat sdf = new SimpleDateFormat(this.dateFormatString);

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
                            if ((currentBlock.getType() == Material.SIGN_POST) || (currentBlock.getType() == Material.WALL_SIGN))
                            {
                                Sign theSign = (Sign)currentBlock.getState();

                                theSign.setLine(0, regionName);
                                theSign.setLine(1, playerName);
                                theSign.setLine(2, ChatColor.WHITE + "Until:");
                                theSign.setLine(3, sdf.format(new Date(timeData.Time)));
                                theSign.update();

                                theSign.update();
                            }
                        }
                        else
                        {
                            sender.sendMessage(Notice(getMessage("TransFailed")));
                        }
                    }
                    else
                    {
                        sender.sendMessage(Notice(getMessage("NotEnoughRenew")));
                        sender.sendMessage(Notice(getMessage("Balance") + " " + econ.getBalance(playerName)));
                    }
                }
                else
                {
                    sender.sendMessage(Notice(getMessage("NotRenting")));
                }
            }
            else
            {
                sender.sendMessage(Notice(regionName + " " + getMessage("NotRented")));
            }
        }
        catch (Exception e)
        {
            getLogger().info("An error has occurred while renewing rental for: " + regionName);
        }
    }

    private void logActivity(String player, String action)
    {
        try
        {
            Date tmp = new Date();
            File file = new File(dataLoc + "RegionActivityLog.txt");

            FileWriter out = new FileWriter(file, true);
            out.write(tmp.toString() + " [" + player + "] " + action + "\r\n");
            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            getLogger().info("An error occurred while trying to log activity.");
        }
    }

    private void setFormatString(String input)
    {
        try
        {
            if (input.equalsIgnoreCase("US")) {
                this.dateFormatString = "MM/dd/yy h:mma";
            } else if (input.equalsIgnoreCase("EU")) {
                this.dateFormatString = "dd/MM/yy h:mma";
            } else {
                this.dateFormatString = "yy/MM/dd h:mma";
            }
        }
        catch (Exception e)
        {
            this.dateFormatString = "yy/MM/dd h:mma";
        }
    }

    private void loadRegionCounts()
    {
        try
        {
            if (new File(dataLoc + "RegionCounts.digi").exists()) {
                this.RegionCounts = ((HashMap)load(dataLoc, "RegionCounts"));
            } else {
                save(this.RegionCounts, dataLoc, "RegionCounts");
            }
        }
        catch (Exception e)
        {
            getLogger().info("Error occurred loading RegionCounts.digi");
            return;
        }
    }

    private void loadAutoRenews()
    {
        try
        {
            if (new File(dataLoc + "AutoRenews" + ".digi").exists()) {
                this.AutoRenews = ((ConcurrentHashMap)load(dataLoc, "AutoRenews"));
            } else {
                save(this.AutoRenews, dataLoc, "AutoRenews");
            }
        }
        catch (Exception e)
        {
            getLogger().info("Error occurred loading AutoRenews");
            return;
        }
    }

    private void loadRentedRegionCounts()
    {
        try
        {
            if (new File(dataLoc + "RentedRegionCounts" + ".digi").exists()) {
                this.RentedRegionCounts = ((ConcurrentHashMap)load(dataLoc, "RentedRegionCounts"));
            } else {
                save(this.RentedRegionCounts, dataLoc, "RentedRegionCounts");
            }
        }
        catch (Exception e)
        {
            getLogger().info("Error occurred loading RentedRegionCounts");
            return;
        }
    }

    private void loadRentedRegionExpirations()
    {
        try
        {
            if (new File(dataLoc + "RentedRegionExpirations" + ".digi").exists()) {
                this.RentedRegionExpirations = ((ConcurrentHashMap)load(dataLoc, "RentedRegionExpirations"));
            } else {
                save(this.RentedRegionExpirations, dataLoc, "RentedRegionExpirations");
            }
        }
        catch (Exception e)
        {
            getLogger().info("Error occurred loading RentedRegionExpirations");
            return;
        }
    }

    private int getBoughtRegionsCount(String playerName)
    {
        if (this.RegionCounts.containsKey(playerName)) {
            return this.RegionCounts.get(playerName);
        }
        return 0;
    }

    private void removeRentedRegionFromCount(String playerName)
    {
        try
        {
            if (this.RentedRegionCounts.containsKey(playerName))
            {
                int amount = getRentedRegionsCount(playerName);
                if (amount > 0) {
                    amount--;
                }
                if (amount >= 0) {
                    this.RentedRegionCounts.put(playerName, amount);
                } else {
                    this.RentedRegionCounts.put(playerName, 0);
                }
                saveRentedRegionCounts();
            }
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred while removing a rented region from a player's count.");
        }
    }

    private int getRentedRegionsCount(String playerName)
    {
        if (this.RentedRegionCounts.containsKey(playerName)) {
            return this.RentedRegionCounts.get(playerName);
        }
        return 0;
    }

    private void setBoughtRegionsCount(String playerName, int amount, CommandSender sender)
    {
        try
        {
            this.RegionCounts.put(playerName, amount);
            saveRegionCounts();
            sender.sendMessage(Notice(playerName + " bought regions set to " + amount));
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred in setBoughtRegions");
        }
    }

    private void setRentedRegionsCount(String playerName, int amount, CommandSender sender)
    {
        try
        {
            this.RentedRegionCounts.put(playerName, amount);
            saveRentedRegionCounts();
            sender.sendMessage(Notice(playerName + " rented regions set to " + amount));
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred in setRentedRegionsCount");
        }
    }

    private void addRentedRegionFile(String playerName, String regionName, Sign sign)
    {
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
            region.signType = "SIGN_POST";
        }
        saveRentableRegion(region);
    }

    private void addBoughtRegionToCounts(String playerName)
    {
        if (this.RegionCounts.containsKey(playerName)) {
            this.RegionCounts.put(playerName, Integer.valueOf(getBoughtRegionsCount(playerName) + 1));
        } else {
            this.RegionCounts.put(playerName, Integer.valueOf(1));
        }
        saveRegionCounts();
    }

    private void addRentedRegionToCounts(String playerName)
    {
        if (this.RentedRegionCounts.containsKey(playerName)) {
            this.RentedRegionCounts.put(playerName, Integer.valueOf(getRentedRegionsCount(playerName) + 1));
        } else {
            this.RentedRegionCounts.put(playerName, Integer.valueOf(1));
        }
        saveRentedRegionCounts();
    }

    private void saveAutoRenews()
    {
        try
        {
            save(this.AutoRenews, dataLoc, "AutoRenews");
        }
        catch (Exception e)
        {
            getLogger().info("An error has occurred saving AutoRenews");
        }
    }

    private void saveRentedRegionCounts()
    {
        try
        {
            save(this.RentedRegionCounts, dataLoc, "RentedRegionCounts");
        }
        catch (Exception e)
        {
            getLogger().info("An error has occurred saving Rented Region Counts");
        }
    }

    private void saveRegionCounts()
    {
        try
        {
            save(this.RegionCounts, dataLoc, "RegionCounts");
        }
        catch (Exception e)
        {
            getLogger().info("An error has occurred saving Region counts.");
        }
    }

    private void saveRentedRegionExpirations()
    {
        try
        {
            save(this.RentedRegionExpirations, dataLoc, "RentedRegionExpirations");
        }
        catch (Exception e)
        {
            getLogger().info("An error has occurred saving RentedRegionExpirations");
        }
    }

    private void checkPlayerRentedRegionCount(String playerName, CommandSender sender)
    {
        if (this.RentedRegionCounts.containsKey(playerName)) {
            sender.sendMessage(Notice(playerName + " has " + getRentedRegionsCount(playerName) + " rented regions."));
        } else {
            sender.sendMessage(Notice(playerName + " has no rented regions."));
        }
    }

    private void checkPlayerRegionCount(String playerName, CommandSender sender)
    {
        if (this.RegionCounts.containsKey(playerName)) {
            sender.sendMessage(Notice(playerName + " has " + getBoughtRegionsCount(playerName) + " bought regions."));
        } else {
            sender.sendMessage(Notice(playerName + " has no bought regions."));
        }
    }

    public static void saveRegion(RentableRegion region)
    {
        save(region.toString(), signDataLoc, region.regionName);
    }

    public RentableRegion loadRegion(String regionName)
    {
        String tmp = (String)load(signDataLoc, regionName);

        return new RentableRegion(tmp);
    }

    public static void save(Object obj, String dataLoc, String file)
    {
        try
        {
            ObjectOutputStream tmp = new ObjectOutputStream(new FileOutputStream(dataLoc + file + ".digi"));
            tmp.writeObject(obj);
            tmp.flush();
            tmp.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static Object load(String dataLoc, String file)
    {
        try
        {
            ObjectInputStream tmp = new ObjectInputStream(new FileInputStream(dataLoc + file + ".digi"));
            Object rv = tmp.readObject();
            tmp.close();
            return rv;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private String Notice(String msg)
    {
        return ChatColor.AQUA + "[BuyRegion] " + ChatColor.YELLOW + msg;
    }

    private String Warning(String msg)
    {
        return ChatColor.RED + "[BuyRegion] " + ChatColor.YELLOW + msg;
    }

    private void setAutoRenew(String playerName, boolean autoRenew)
    {
        if (autoRenew)
        {
            this.AutoRenews.put(playerName, Boolean.valueOf(true));
            saveAutoRenews();
        }
        else
        {
            this.AutoRenews.remove(playerName);
            saveAutoRenews();
        }
    }

    @EventHandler
    public void onPunchSign(PlayerInteractEvent event)
    {
        try
        {
            if (event.getAction().name().equals("RIGHT_CLICK_BLOCK"))
            {
                Material blockType = event.getClickedBlock().getType();
                if ((blockType == Material.SIGN_POST) || (blockType == Material.WALL_SIGN))
                {
                    Sign sign = (Sign)event.getClickedBlock().getState();

                    String topLine = sign.getLine(0);
                    if ((topLine.length() > 0) && ((topLine.equalsIgnoreCase("[BuyRegion]")) || (topLine.equalsIgnoreCase("[WGRSA]"))))
                    {
                        Player sender = event.getPlayer();
                        String playerName = sender.getName();
                        if (topLine.equalsIgnoreCase("[WGRSA]"))
                        {
                            sign.setLine(0, "[BuyRegion]");
                            sign.update();
                        }
                        if ((this.requireBuyPerms) && (!sender.hasPermission("buyregion.buy")) && (!sender.isOp()))
                        {
                            sender.sendMessage(Notice(getMessage("BuyPerms")));
                            return;
                        }
                        if ((this.buyRegionMax > 0) &&
                                (getBoughtRegionsCount(playerName) >= this.buyRegionMax) &&
                                (!sender.isOp()) &&
                                (!sender.hasPermission("buyregion.exempt")))
                        {
                            sender.sendMessage(Notice(getMessage("BuyMax") + " " + this.buyRegionMax));
                            return;
                        }
                        if ((this.BuyMode.containsKey(playerName)) || (!this.requireBuyMode))
                        {
                            double regionPrice = Double.parseDouble(sign.getLine(2));

                            String regionName = sign.getLine(1);
                            World world = sender.getWorld();
                            RegionManager rm = getWorldGuard().getRegionManager(world);

                            DefaultDomain dd = new DefaultDomain();
                            dd.addPlayer(playerName);

                            ProtectedRegion region = rm.getRegion(regionName);
                            if (region == null)
                            {
                                sender.sendMessage(Notice(getMessage("RegionNoExist")));
                                return;
                            }
                            if (econ.getBalance(playerName) >= regionPrice)
                            {
                                EconomyResponse response = econ.withdrawPlayer(playerName, regionPrice);
                                if (response.transactionSuccess())
                                {
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
                                }
                                else
                                {
                                    sender.sendMessage(Notice(getMessage("TransFailed")));
                                }
                            }
                            else
                            {
                                sender.sendMessage(Warning(getMessage("NotEnoughBuy")));
                                sender.sendMessage(Warning(getMessage("Balance") + " " + econ.getBalance(playerName)));
                            }
                        }
                        else
                        {
                            sender.sendMessage(Warning(getMessage("BuyModeBuy")));
                            sender.sendMessage(Warning(getMessage("ToEnterBuyMode")));
                        }
                    }
                    else if ((topLine.length() > 0) && (topLine.equalsIgnoreCase("[RentRegion]")))
                    {
                        Player sender = event.getPlayer();
                        String regionName = sign.getLine(1);
                        String playerName = sender.getName();
                        if ((this.requireRentPerms) && (!sender.hasPermission("buyregion.rent")) && (!sender.isOp()))
                        {
                            sender.sendMessage(Warning(getMessage("RentPerms")));
                            return;
                        }
                        if ((this.rentRegionMax > 0) &&
                                (getRentedRegionsCount(playerName) >= this.rentRegionMax) &&
                                (!sender.isOp()) &&
                                (!sender.hasPermission("buyregion.exempt")))
                        {
                            sender.sendMessage(Notice(getMessage("RentMax") + " " + this.rentRegionMax));
                            return;
                        }
                        if ((this.BuyMode.containsKey(playerName)) || (!this.requireBuyMode))
                        {
                            if (regionName.length() > 0)
                            {
                                String dateString = sign.getLine(3);
                                try
                                {
                                    double regionPrice = Double.parseDouble(sign.getLine(2));

                                    String[] expiration = dateString.split("\\s");
                                    int i = Integer.parseInt(expiration[0]);
                                    DateResult dateResult = parseDateString(i, expiration[1]);
                                    if (dateResult.IsError) {
                                        throw new Exception();
                                    }
                                }
                                catch (Exception e)
                                {
                                    getLogger().info("Region price or expiration");
                                    sign.setLine(0, "-invalid-");
                                    sign.setLine(1, "<region here>");
                                    sign.setLine(2, "<price here>");
                                    sign.setLine(3, "<timespan>");
                                    sign.update();
                                    getLogger().info("Invalid [RentRegion] sign cleared at " + sign.getLocation().toString()); return;
                                }
                                DateResult dateResult;
                                String[] expiration;
                                double regionPrice = Double.parseDouble(sign.getLine(2));
                                World world = sender.getWorld();
                                RegionManager rm = getWorldGuard().getRegionManager(world);

                                DefaultDomain dd = new DefaultDomain();
                                dd.addPlayer(playerName);

                                ProtectedRegion region = rm.getRegion(regionName);
                                if (region == null)
                                {
                                    sender.sendMessage(Notice(getMessage("RegionNoExist")));
                                    sign.setLine(0, "-invalid-");
                                    sign.setLine(1, "<region here>");
                                    sign.setLine(2, "<price here>");
                                    sign.setLine(3, "<timespan>");
                                    sign.update();
                                    getLogger().info("Invalid [RentRegion] sign cleared at " + sign.getLocation().toString());

                                    return;
                                }

                                if (econ.getBalance(playerName) >= regionPrice)
                                {
                                    EconomyResponse response = econ.withdrawPlayer(playerName, regionPrice);
                                    if (response.transactionSuccess())
                                    {
                                        region.setMembers(dd);
                                        rm.save();

                                        addRentedRegionFile(playerName, regionName, sign);

                                        addRentedRegionToCounts(playerName);

                                        logActivity(playerName, " RENT " + regionName);

                                        SimpleDateFormat sdf = new SimpleDateFormat(this.dateFormatString);

                                        sign.setLine(0, regionName);
                                        sign.setLine(1, playerName);
                                        sign.setLine(2, ChatColor.WHITE + "Until:");
                                        sign.setLine(3, sdf.format(new Date(dateResult.Time)));
                                        sign.update();

                                        sender.sendMessage(Notice(getMessage("Rented") + " " + regionName + " -> " + sdf.format(new Date(dateResult.Time))));
                                        sender.sendMessage(Notice(getMessage("NewBalance") + " " + econ.getBalance(playerName)));

                                        this.RentedRegionExpirations.put(regionName, Long.valueOf(dateResult.Time));
                                        saveRentedRegionExpirations();

                                        this.BuyMode.remove(playerName);
                                    }
                                    else
                                    {
                                        sender.sendMessage(Warning(getMessage("TransFailed")));
                                    }
                                }
                                else
                                {
                                    sender.sendMessage(Warning(getMessage("NotEnoughRent")));
                                    sender.sendMessage(Warning(getMessage("Balance") + " " + econ.getBalance(playerName)));
                                }
                            }
                        }
                        else
                        {
                            sender.sendMessage(Warning(getMessage("BuyModeRent")));
                            sender.sendMessage(Warning(getMessage("ToEnterBuyMode")));
                        }
                    }
                }
            }
        }
        catch (Exception e)
        {
            getLogger().info(e.getMessage());
        }
    }

    private DateResult parseDateString(int val, String type)
    {
        try
        {
            Date tmp = new Date();
            if ((type.equalsIgnoreCase("d")) || (type.equalsIgnoreCase("day")) || (type.equalsIgnoreCase("days")))
            {
                Calendar cal = Calendar.getInstance();
                cal.setTime(tmp);
                cal.add(5, val);

                return new DateResult(cal.getTime().getTime(), val + " days", false);
            }
            if ((type.equalsIgnoreCase("h")) || (type.equalsIgnoreCase("hour")) || (type.equalsIgnoreCase("hours"))) {
                return new DateResult(tmp.getTime() + val * 60 * 60 * 1000, val + " hours", false);
            }
            if ((type.equalsIgnoreCase("m")) || (type.equalsIgnoreCase("mins")) || (type.equalsIgnoreCase("min")) || (type.equalsIgnoreCase("minutes")) || (type.equalsIgnoreCase("minute"))) {
                return new DateResult(tmp.getTime() + val * 60 * 1000, val + " minutes", false);
            }
            if ((type.equalsIgnoreCase("s")) || (type.equalsIgnoreCase("sec")) || (type.equalsIgnoreCase("secs")) || (type.equalsIgnoreCase("seconds")) || (type.equalsIgnoreCase("second"))) {
                return new DateResult(tmp.getTime() + val * 1000, val + " seconds", false);
            }
            return new DateResult(-1L, "ERROR", true);
        }
        catch (Exception e) {}
        return new DateResult(-1L, "ERROR", true);
    }

    private DateResult parseDateString(int val, String type, long start)
    {
        try
        {
            Date tmp = new Date(start);
            if ((type.equalsIgnoreCase("d")) || (type.equalsIgnoreCase("day")) || (type.equalsIgnoreCase("days")))
            {
                Calendar cal = Calendar.getInstance();
                cal.setTime(tmp);
                cal.add(5, val);

                return new DateResult(cal.getTime().getTime(), val + " days", false);
            }
            if ((type.equalsIgnoreCase("h")) || (type.equalsIgnoreCase("hour")) || (type.equalsIgnoreCase("hours"))) {
                return new DateResult(tmp.getTime() + val * 60 * 60 * 1000, val + " hours", false);
            }
            if ((type.equalsIgnoreCase("m")) || (type.equalsIgnoreCase("mins")) || (type.equalsIgnoreCase("min")) || (type.equalsIgnoreCase("minutes")) || (type.equalsIgnoreCase("minute"))) {
                return new DateResult(tmp.getTime() + val * 60 * 1000, val + " minutes", false);
            }
            if ((type.equalsIgnoreCase("s")) || (type.equalsIgnoreCase("sec")) || (type.equalsIgnoreCase("secs")) || (type.equalsIgnoreCase("seconds")) || (type.equalsIgnoreCase("second"))) {
                return new DateResult(tmp.getTime() + val * 1000, val + " seconds", false);
            }
            return new DateResult(-1L, "ERROR", true);
        }
        catch (Exception e) {}
        return new DateResult(-1L, "ERROR", true);
    }

    public class DateResult
    {
        public long Time;
        public String Text;
        boolean IsError;

        public DateResult(long time, String text, boolean isError)
        {
            this.Time = time;
            this.Text = text;
            this.IsError = isError;
        }
    }

    private WorldGuardPlugin getWorldGuard()
    {
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");
        if ((!(plugin instanceof WorldGuardPlugin))) {
            return null;
        }
        return (WorldGuardPlugin)plugin;
    }

    private boolean setupEconomy()
    {
        try
        {
            if (getServer().getPluginManager().getPlugin("Vault") == null) {
                return false;
            }
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            econ = (Economy)rsp.getProvider();
            return econ != null;
        }
        catch (Exception e)
        {
            getLogger().info("Error loading Vault! Plugin shutting down.");
        }
        return false;
    }

    private void toggleBuyMode(CommandSender sender)
    {
        try
        {
            String playerName = sender.getName();
            if (!this.BuyMode.containsKey(playerName))
            {
                this.BuyMode.put(sender.getName(), Boolean.valueOf(true));
                sender.sendMessage(Notice("BuyModeEnter"));
            }
            else
            {
                this.BuyMode.remove(playerName);
                sender.sendMessage(Notice(getMessage("BuyModeExit")));
            }
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred in toggleBuyMode");
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (cmd.getName().equalsIgnoreCase("buyregion")) {
            if (args.length == 0)
            {
                toggleBuyMode(sender);
            }
            else
            {
                if (args[0].equalsIgnoreCase("renew"))
                {
                    if (args.length < 2) {
                        sender.sendMessage(Notice(getMessage("InvalidRenewArgs")));
                    } else {
                        renewRental(args[1], sender.getName(), sender);
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("autorenew"))
                {
                    if (args.length < 2)
                    {
                        if (this.AutoRenews.containsKey(sender.getName()))
                        {
                            if (this.AutoRenews.get(sender.getName())) {
                                sender.sendMessage(Notice(getMessage("RenewOn")));
                            } else {
                                sender.sendMessage(Notice(getMessage("RenewOff")));
                            }
                        }
                        else {
                            sender.sendMessage(Notice(getMessage("RenewOff")));
                        }
                    }
                    else if ((args[1].equalsIgnoreCase("true")) || (args[1].equalsIgnoreCase("yes")) || (args[1].equalsIgnoreCase("on")))
                    {
                        setAutoRenew(sender.getName(), true);
                        sender.sendMessage(Notice(getMessage("RenewTurnOn")));
                    }
                    else if ((args[1].equalsIgnoreCase("false")) || (args[1].equalsIgnoreCase("no")) || (args[1].equalsIgnoreCase("off")))
                    {
                        setAutoRenew(sender.getName(), false);
                        sender.sendMessage(Notice(getMessage("RenewTurnOff")));
                    }
                    else
                    {
                        sender.sendMessage(Notice(getMessage("InvalidArg")));
                    }
                    return false;
                }
                if (args[0].equalsIgnoreCase("help"))
                {
                    String[] help = {
                            Notice(getMessage("Help1")),
                            Notice(getMessage("Help2")),
                            Notice(getMessage("Help3")),
                            Notice(getMessage("Help4")) };

                    sender.sendMessage(help);
                }
                if ((sender.isOp()) || (sender.hasPermission("buyregion.admin"))) {
                    if (args[0].equalsIgnoreCase("buycheck"))
                    {
                            checkPlayerRegionCount(args[1], sender);
                    }
                    else if (args[0].equalsIgnoreCase("rentcheck"))
                    {
                        checkPlayerRentedRegionCount(args[1], sender);
                    }
                    else if (args[0].equalsIgnoreCase("buyset"))
                    {
                        if (args.length < 3)
                        {
                            sender.sendMessage(Warning("Invalid args - /buyregion buyset <player> <amount>"));
                        }
                        else
                        {
                            int amount;

                            try
                            {
                                amount = Integer.parseInt(args[2]);
                                if (amount < 0) {
                                    amount = 0;
                                }
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                return false;
                            }
                            setBoughtRegionsCount(args[1], amount, sender);
                        }
                    }
                    else if (args[0].equalsIgnoreCase("rentset"))
                    {
                        if (args.length < 3)
                        {
                            sender.sendMessage(Warning("Invalid args - /buyregion rentset <player> <amount>"));
                        }
                        else
                        {
                            int amount;
                            try
                            {
                                amount = Integer.parseInt(args[2]);
                                if (amount < 0) amount = 0;
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                return false;
                            }
                            setRentedRegionsCount(args[1], amount, sender);
                        }
                    }
                    else
                    {
                        if (args[0].equalsIgnoreCase("buymax")) {
                            try
                            {
                                if (args.length < 2)
                                {
                                    sender.sendMessage(Notice("Current BuyRegionMax: " + this.buyRegionMax));
                                }
                                else
                                {
                                    int amount;

                                    try
                                    {
                                        amount = Integer.parseInt(args[1]);
                                        if (amount < 0) amount = 0;
                                    }
                                    catch (Exception e)
                                    {
                                        sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                        return false;
                                    }
                                    this.buyRegionMax = amount;
                                    getConfig().set("BuyRegionMax", amount);
                                    saveConfig();

                                    sender.sendMessage(Notice("BuyRegionMax has been updated to " + amount));
                                }
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(
                                        "An error occurred... check all values and try again.");
                            }
                        }
                        if (args[0].equalsIgnoreCase("rentmax")) {
                            try
                            {
                                if (args.length < 2)
                                {
                                    sender.sendMessage(Notice("Current RentRegionMax: " + this.rentRegionMax));
                                }
                                else
                                {
                                    int amount;

                                    try
                                    {
                                        amount = Integer.parseInt(args[1]);
                                        if (amount < 0) amount = 0;
                                    }
                                    catch (Exception e)
                                    {
                                        sender.sendMessage(Warning("Invalid amount. Enter a number for the amount."));
                                        return false;
                                    }
                                    this.rentRegionMax = amount;
                                    getConfig().set("RentRegionMax", Integer.valueOf(amount));
                                    saveConfig();

                                    sender.sendMessage(Warning("RentRegionMax has been updated to " + amount));
                                }
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(Warning("An error occurred... check all values and try again."));
                            }
                        }
                        if (args[0].equalsIgnoreCase("buyperms"))
                        {
                            try
                            {
                                if (args.length > 1)
                                {
                                    if ((args[1].equalsIgnoreCase("true")) || (args[1].equalsIgnoreCase("false")))
                                    {
                                        boolean val = Boolean.parseBoolean(args[1]);
                                        if (val)
                                        {
                                            this.requireBuyPerms = true;
                                            getConfig().set("RequireBuyPerms", Boolean.valueOf(true));
                                        }
                                        else
                                        {
                                            this.requireBuyPerms = false;
                                            getConfig().set("RequireBuyPerms", Boolean.valueOf(false));
                                        }
                                        sender.sendMessage(Notice("RequireBuyPerms set."));
                                        saveConfig();
                                    }
                                    else
                                    {
                                        sender.sendMessage(Warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                }
                                else {
                                    sender.sendMessage(Notice("RequireBuyPerms: " + getConfig().getBoolean("RequireBuyPerms")));
                                }
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(Warning("An error occurred... Syntax: /buyregion buyperms true/false"));
                                return false;
                            }
                        }
                        else if (args[0].equalsIgnoreCase("rentperms"))
                        {
                            try
                            {
                                if (args.length > 1)
                                {
                                    if ((args[1].equalsIgnoreCase("true")) || (args[1].equalsIgnoreCase("false")))
                                    {
                                        boolean val = Boolean.parseBoolean(args[1]);
                                        if (val)
                                        {
                                            this.requireRentPerms = true;
                                            getConfig().set("RequireRentPerms", Boolean.TRUE);
                                        }
                                        else
                                        {
                                            this.requireRentPerms = false;
                                            getConfig().set("RequireRentPerms", Boolean.FALSE);
                                        }
                                        sender.sendMessage(Notice("RequireRentPerms set."));
                                        saveConfig();
                                    }
                                    else
                                    {
                                        sender.sendMessage(Warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                }
                                else {
                                    sender.sendMessage(Notice("RequireRentPerms: " + getConfig().getBoolean("RequireRentPerms")));
                                }
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(Warning("An error occurred... Syntax: /buyregion rentperms true/false"));
                                return false;
                            }
                        }
                        else if (args[0].equalsIgnoreCase("buymode"))
                        {
                            try
                            {
                                if (args.length > 1)
                                {
                                    if ((args[1].equalsIgnoreCase("true")) || (args[1].equalsIgnoreCase("false")))
                                    {
                                        boolean val = Boolean.parseBoolean(args[1]);
                                        if (val)
                                        {
                                            this.requireBuyMode = true;
                                            getConfig().set("RequireBuyMode", Boolean.TRUE);
                                        }
                                        else
                                        {
                                            this.requireBuyMode = false;
                                            getConfig().set("RequireBuyMode", Boolean.FALSE);
                                        }
                                        sender.sendMessage(Notice("RequireBuyMode set."));
                                        saveConfig();
                                    }
                                    else
                                    {
                                        sender.sendMessage(Warning("Invalid value. Enter 'true' or 'false'"));
                                    }
                                }
                                else {
                                    sender.sendMessage(Notice("RequireBuyMode: " + getConfig().getBoolean("RequireBuyMode")));
                                }
                            }
                            catch (Exception e)
                            {
                                sender.sendMessage(Warning("An error occurred... Syntax: /buyregion buymode true/false"));
                                return false;
                            }
                        }
                        else if (args[0].equalsIgnoreCase("evict"))
                        {
                            if (args.length > 1)
                            {
                                String regionName = args[1];
                                if (new File(signDataLoc + regionName + ".digi").exists())
                                {
                                    if (evictRegion(regionName)) {
                                        sender.sendMessage(Notice("Region eviction completed!"));
                                    } else {
                                        sender.sendMessage(Warning("Region eviction failed."));
                                    }
                                }
                                else {
                                    sender.sendMessage(Warning("Region is not currently rented!"));
                                }
                            }
                            else
                            {
                                sender.sendMessage(Warning("Invalid syntax: /buyregion evict <region>"));
                                return false;
                            }
                        }
                        else
                        {
                            String[] help = {
                                    Notice("Admin Commands:"),
                                    Notice("/buyregion buymode <true/false> - sets RequireBuyMode"),
                                    Notice("/buyregion buycheck <player> - checks total bought regions for <player>"),
                                    Notice("/buyregion rentcheck <player> - checks total rented regions for <player>"),
                                    Notice("/buyregion buyset <player> <amount> - sets total bought regions for <player>"),
                                    Notice("/buyregion rentset <player> <amount> - sets total rented regions for <player>"),
                                    Notice("/buyregion buymax - displays current BuyRegionMax"),
                                    Notice("/buyregion buymax <amount> - sets BuyRegionMax"),
                                    Notice("/buyregion rentmax - displays current RentRegionMax"),
                                    Notice("/buyregion rentmax <amount> - sets RentRegionMax"),
                                    Notice("/buyregion evict <region> - evicts renter from <region>") };

                            sender.sendMessage(help);
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean evictRegion(String regionName)
    {
        try
        {
            RentableRegion rentedRegion = loadRegion(regionName);

            this.RentedRegionExpirations.remove(regionName);
            saveRentedRegionExpirations();

            World world = getServer().getWorld(rentedRegion.worldName);
            RegionManager rm = getWorldGuard().getRegionManager(world);
            ProtectedRegion region = rm.getRegion(regionName);
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
            if ((currentBlock.getType() == Material.SIGN_POST) || (currentBlock.getType() == Material.WALL_SIGN))
            {
                Sign theSign = (Sign)currentBlock.getState();

                theSign.setLine(0, rentedRegion.signLine1);
                theSign.setLine(1, rentedRegion.signLine2);
                theSign.setLine(2, rentedRegion.signLine3);
                theSign.setLine(3, rentedRegion.signLine4);

                theSign.update();
            }
            else
            {
                try
                {
                    if (rentedRegion.signType == "WALL_SIGN") {
                        currentBlock.setType(Material.WALL_SIGN);
                    } else {
                        currentBlock.setType(Material.SIGN_POST);
                    }
                    Sign newSign = (Sign)currentBlock.getState();

                    newSign.setLine(0, rentedRegion.signLine1);
                    newSign.setLine(1, rentedRegion.signLine2);
                    newSign.setLine(2, rentedRegion.signLine3);
                    newSign.setLine(3, rentedRegion.signLine4);

                    newSign.update();
                }
                catch (Exception e)
                {
                    getLogger().info("RentRegion automatic sign creation failed for region " + rentedRegion.regionName);
                }
            }
            File regionFile = new File(signDataLoc + regionName + ".digi");
            if (regionFile.exists()) {
                regionFile.delete();
            }
            Player player = getServer().getPlayer(rentedRegion.renter);
            if ((player instanceof Player)) {
                player.sendMessage(Notice(getMessage("EvictedFrom") + " " + regionName));
            }
            logActivity(rentedRegion.renter, " EVICTED " + rentedRegion.regionName);

            return true;
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred during an eviction.");
        }
        return false;
    }

    @EventHandler(priority= EventPriority.HIGHEST)
    public void signChangeMonitor(SignChangeEvent event)
    {
        try
        {
            Player player = event.getPlayer();
            if ((event.getLine(0).equalsIgnoreCase("[WGRSA]")) || (event.getLine(0).equalsIgnoreCase("[BuyRegion]")) || (event.getLine(0).equalsIgnoreCase("[RentRegion]"))) {
                if ((!player.hasPermission("buyregion.create")) && (!player.isOp()))
                {
                    event.setLine(0, "-restricted-");
                }
                else
                {
                    String regionName = event.getLine(1);
                    World world = event.getBlock().getWorld();
                    RegionManager rm = getWorldGuard().getRegionManager(world);

                    ProtectedRegion region = rm.getRegion(regionName);
                    if (region == null)
                    {
                        event.getPlayer().sendMessage(Warning(getMessage("RegionNoExist")));

                        event.setLine(0, "-invalid-");
                        return;
                    }
                    try
                    {
                        String dateString = event.getLine(3);
                        try
                        {
                            double regionPrice = Double.parseDouble(event.getLine(2));
                            if (regionPrice <= 0.0D) {
                                throw new Exception();
                            }
                            if (event.getLine(0).equalsIgnoreCase("[RentRegion]"))
                            {
                                String[] expiration = dateString.split("\\s");
                                int i = Integer.parseInt(expiration[0]);
                                DateResult dateResult = parseDateString(i, expiration[1]);
                                if (dateResult.IsError) {
                                    throw new Exception();
                                }
                            }
                        }
                        catch (Exception e)
                        {
                            event.getPlayer().sendMessage(Notice(getMessage("InvalidPriceTime")));
                            event.setLine(0, "-invalid-");

                            return;
                        }
                        if (!event.getLine(0).equalsIgnoreCase("[RentRegion]")) {
                            event.setLine(0, "[BuyRegion]");
                        }
                    }
                    catch (Exception e)
                    {
                        event.getPlayer().sendMessage(Notice("Invalid amount!"));
                        event.setLine(0, "-invalid-");
                        return;
                    }
                    event.setLine(0, "[RentRegion]");
                    event.getPlayer().sendMessage(Notice("A BuyRegion sign has been created!"));
                }
            }
        }
        catch (Exception e)
        {
            getLogger().info("An error occurred in signChangeMonitor");
        }
    }

    private void saveRentableRegion(RentableRegion region)
    {
        try
        {
            saveRegion(region);
        }
        catch (Exception e)
        {
            getLogger().info("An error has occurred saving a RentableRegion.");
        }
    }

    public class RentableRegion
    {
        public String worldName;
        public String regionName;
        public String renter;
        public String signLocationX;
        public String signLocationY;
        public String signLocationZ;
        public String signLocationPitch;
        public String signLocationYaw;
        public String signDirection;
        public String signLine1;
        public String signLine2;
        public String signLine3;
        public String signLine4;
        public String signType;

        public RentableRegion()
        {
            this.worldName = "";
            this.regionName = "";
            this.renter = "";
            this.signLocationX = "";
            this.signLocationY = "";
            this.signLocationZ = "";
            this.signLocationPitch = "";
            this.signLocationYaw = "";
            this.signDirection = "";
            this.signLine1 = "";
            this.signLine2 = "";
            this.signLine3 = "";
            this.signLine4 = "";
            this.signType = "";
        }

        public RentableRegion(String input)
        {
            try
            {
                String[] tmp = input.split("%%%");

                this.worldName = tmp[0];
                this.regionName = tmp[1];
                this.renter = tmp[2];
                this.signLocationX = tmp[3];
                this.signLocationY = tmp[4];
                this.signLocationZ = tmp[5];
                this.signLocationPitch = tmp[6];
                this.signLocationYaw = tmp[7];
                this.signDirection = tmp[8];
                this.signLine1 = tmp[9];
                this.signLine2 = tmp[10];
                this.signLine3 = tmp[11];
                this.signLine4 = tmp[12];
                this.signType = tmp[13];
            }
            catch (Exception e)
            {
                BuyRegion.this.getLogger().info("An error occurred while instantiating a RentableRegion.");
            }
        }

        public String toString()
        {
            return this.worldName + "%%%" + this.regionName + "%%%" + this.renter + "%%%" + this.signLocationX + "%%%" + this.signLocationY + "%%%" + this.signLocationZ + "%%%" + this.signLocationPitch + "%%%" + this.signLocationYaw + "%%%" + this.signDirection + "%%%" + this.signLine1 + "%%%" + this.signLine2 + "%%%" + this.signLine3 + "%%%" + this.signLine4 + "%%%" + this.signType;
        }
    }
}
