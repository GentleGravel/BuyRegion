package com.region.buyregion.helpers;

import com.region.buyregion.BuyRegion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.logging.Level;

import org.bukkit.configuration.file.YamlConfiguration;

public class LocaleHelper {
    private YamlConfiguration bundle;
    private File folder = new File(String.format("%s/locale", BuyRegion.instance.getDataFolder()));
    private File file = new File(folder, String.format("%s.yml", Locale.getDefault().getLanguage()));

    public LocaleHelper() {
        String localeDisplay = Locale.getDefault().getDisplayName();

        if (localeDisplay == null)
        localeDisplay = Locale.getDefault().getDisplayLanguage();
        if (localeDisplay == null)
        localeDisplay = Locale.getDefault().getDisplayCountry();
        if (localeDisplay == null)
        localeDisplay = Locale.getDefault().toString();
        BuyRegion.instance.getLogger().info(String.format("Locale: %s", localeDisplay));

        copyFiles();

        updateBundle();
    }

    private void updateBundle() {
        if (!folder.exists()) {
            BuyRegion.instance.getLogger().severe(String.format("Unable to create folder %s", folder.getPath()));
            return;
        }
        if (!file.exists()) {
            BuyRegion.instance.getLogger().severe(String.format("The file %s does not exist, falling back to English", file.getPath()));
            file = new File(folder, "en.yml");
        }
        bundle = YamlConfiguration.loadConfiguration(file);
    }

    private String get(String key) {
        return bundle.getString(key);
    }

    public String get(String key, Object... args) {
        if (!bundle.contains(key))
        return key;
        try {
            return String.format(get(key), args);
        } catch(Exception e) {
            BuyRegion.instance.getLogger().log(Level.SEVERE, String.format("An error occurred while translating '%s' with %d args", key, args.length), e);
        }
        return key;
    }

    private void copyFiles() {
        String[] locales = { "en" };
        for (String locale : locales) {
            String filename = locale + ".yml";
            File file = new File(folder, filename);

            if (file.exists())
            continue;
            try {
                file.getParentFile().mkdirs();

                InputStream stream = BuyRegion.class.getResourceAsStream("/locale/" + filename);

                Files.copy(stream, file.toPath());
            } catch(IOException e) {
                BuyRegion.instance.getLogger().log(Level.SEVERE, "Failed copying " + filename + " to the data folder", e);
            }
        }
    }

}

