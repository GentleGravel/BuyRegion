package com.region.buyregion.config;

import com.region.buyregion.BuyRegion;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class BuyRegionConfig {
    public final String dataLoc = "plugins" + File.separator + "BuyRegion" + File.separator;
    public final String signDataLoc = "plugins" + File.separator + "BuyRegion" + File.separator + "rent" + File.separator;

    public int buyRegionMax;
    public int rentRegionMax;
    public boolean requireBuyMode;
    public long tickRate;
    public boolean requireBuyPerms;
    public boolean requireRentPerms;
    public String dateFormatString;

    public BuyRegionConfig() {
        FileConfiguration config = BuyRegion.instance.getConfig();

        try {
            buyRegionMax = config.getInt("BuyRegionMax", 0);
        } catch(Exception e) {
            buyRegionMax = 0;
        }
        try {
            rentRegionMax = config.getInt("RentRegionMax", 0);
        } catch(Exception e) {
            rentRegionMax = 0;
        }
        try {
            requireBuyMode = config.getBoolean("RequireBuyMode", true);
        } catch(Exception e) {
            requireBuyMode = true;
        }
        try {
            tickRate = config.getLong("CheckExpirationsInMins") * 60L * 20L;
        } catch(Exception e) {
            tickRate = 6000L;
        }
        try {
            requireBuyPerms = config.getBoolean("RequireBuyPerms", false);
        } catch(Exception e) {
            requireBuyPerms = false;
        }
        try {
            requireRentPerms = config.getBoolean("RequireRentPerms", false);
        } catch(Exception e) {
            requireRentPerms = false;
        }
        try {
            setFormatString(config.getString("DateFormat", "Default"));
        } catch(Exception e) {
            dateFormatString = "yy/MM/dd h:mma";
        }

        config.options().copyDefaults(true);
    }

    private void setFormatString(String input) {
        try {
            if (input.equalsIgnoreCase("US")) {
                dateFormatString = "MM/dd/yy h:mma";
            } else if (input.equalsIgnoreCase("EU")) {
                dateFormatString = "dd/MM/yy h:mma";
            } else {
                dateFormatString = "yy/MM/dd h:mma";
            }
        } catch(Exception e) {
            dateFormatString = "yy/MM/dd h:mma";
        }
    }
}

