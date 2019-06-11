package com.region.buyregion.config;

import com.region.buyregion.BuyRegion;

import java.io.*;
import java.util.logging.Level;

public class DigiFile<T> implements Serializable {
    private final String name;
    private final File file;
    private T o;

    public DigiFile(String name, String dataLoc, T o) {
        this.name = name;
        this.o = o;
        this.file = new File(dataLoc + this.name + ".digi");

        try {
            if (file.exists()) {
                load();
            } else {
                save();
            }
        } catch(Exception e) {
            BuyRegion.instance.getLogger().log(Level.SEVERE, "Error occurred loading " + file.getPath(), e);
        }
    }

    public void save() {
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            ObjectOutputStream tmp = new ObjectOutputStream(new FileOutputStream(file.getPath()));
            tmp.writeObject(o);
            tmp.flush();
            tmp.close();
        } catch(Exception e) {
            BuyRegion.instance.getLogger().log(Level.SEVERE, "Error occurred saving " + file.getPath(), e);
        }
    }

    private void load() {
        try {
            ObjectInputStream tmp = new ObjectInputStream(new FileInputStream(file.getPath()));
            Object rv = tmp.readObject();
            tmp.close();
            o = (T) rv;
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public T get() {
        return o;
    }

}

