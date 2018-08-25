package com.region.buyregion.helpers;

import com.region.buyregion.BuyRegion;

import net.md_5.bungee.api.ChatColor;

public class ChatHelper {
    private static final String name = BuyRegion.instance.getName();

    public static String notice(String msg, Object... args) {
        return format(ChatColor.AQUA, msg, args);
    }

    public static String warning(String msg, Object... args) {
        return format(ChatColor.RED, msg, args);
    }

    private static String format(ChatColor color, String msg, Object... args) {
        return String.format("%s[%s] %s%s", color, name, ChatColor.YELLOW, BuyRegion.instance.locale.get(msg, args));
    }

}

