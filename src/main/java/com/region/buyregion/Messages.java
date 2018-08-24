package com.region.buyregion;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.concurrent.ConcurrentHashMap;

public class Messages extends ConcurrentHashMap<String, String> {
    void init() {
        FileConfiguration config = BuyRegion.instance.getConfig();

        this.put("InvalidPriceTime", config.getString("messages.InvalidPriceTime", "Invalid sign. Invalid price or timespan!"));
        this.put("RegionNoExist", config.getString("messages.RegionNoExist", "Invalid sign. Region doesn't exist!"));
        this.put("EvictedFrom", config.getString("messages.EvictedFrom", "You have been evicted from"));
        this.put("InvalidArg", config.getString("messages.InvalidArg", "Invalid argument. Accepted values: true, false, yes, no, off, on"));
        this.put("RenewTurnOff", config.getString("messages.RenewTurnOff", "Auto-Renew has been turned OFF for you."));
        this.put("RenewTurnOn", config.getString("messages.RenewTurnOn", "Auto-Renew has been turned ON for you."));
        this.put("RenewOff", config.getString("messages.RenewOff", "Auto-Renew is off!"));
        this.put("RenewOn", config.getString("messages.RenewOn", "Auto-Renew is on!"));
        this.put("InvalidRenewArgs", config.getString("messages.InvalidRenewArgs", "Invalid args: /buyregion renew <region>"));
        this.put("BuyModeExit", config.getString("messages.BuyModeExit", "You have exited Buy Mode."));
        this.put("BuyModeEnter", config.getString("messages.BuyModeEnter", "You are now in Buy Mode - right-click the BuyRegion sign!"));
        this.put("ToEnterBuyMode", config.getString("messages.ToEnterBuyMode", "To enter Buy Mode type: /buyregion"));
        this.put("BuyModeRent", config.getString("messages.BuyModeRent", "You must be in Buy Mode to rent this region!"));
        this.put("BuyModeBuy", config.getString("messages.BuyModeBuy", "You must be in Buy Mode to buy this region!"));
        this.put("NotEnoughRent", config.getString("messages.NotEnoughRent", "Not enough money to rent this region!"));
        this.put("NotEnoughBuy", config.getString("messages.NotEnoughBuy", "Not enough money to buy this region!"));
        this.put("TransFailed", config.getString("messages.TransFailed", "Transaction Failed!"));
        this.put("Rented", config.getString("messages.Rented", "Congrats! You have rented"));
        this.put("NewBalance", config.getString("messages.NewBalance", "Your new balance is"));
        this.put("RentMax", config.getString("messages.RentMax", "You are not allowed to rent more regions. Max"));
        this.put("RentPerms", config.getString("messages.RentPerms", "You do not have permission to rent regions."));
        this.put("Balance", config.getString("messages.Balance", "Your balance is"));
        this.put("Purchased", config.getString("messages.Purchased", "Congrats! You have purchased"));
        this.put("BuyMax", config.getString("messages.BuyMax", "You are not allowed to buy more regions. Max"));
        this.put("BuyPerms", config.getString("messages.BuyPerms", "You do not have permission to buy regions."));
        this.put("NotRented", config.getString("messages.NotRented", "is not currently rented."));
        this.put("NotRenting", config.getString("messages.NotRenting", "You are not renting this region. You cannot renew it!"));
        this.put("NotEnoughRenew", config.getString("messages.NotEnoughRenew", "Not enough money to renew rental for this region!"));
        this.put("Renewed", config.getString("messages.Renewed", "Congrats! You have renewed"));
        this.put("Expired", config.getString("messages.Expired", "Your rental has expired for region"));
        this.put("Help1", config.getString("messages.Help1", "BuyRegion Commands"));
        this.put("Help2", config.getString("messages.Help2", "/buyregion - toggles buy mode"));
        this.put("Help3", config.getString("messages.Help3", "/buyregion renew <region> - renews rental of <region>"));
        this.put("Help4", config.getString("messages.Help4", "/buyregion autorenew <true/false> - sets auto-renew for all rented regions"));
    }
}
