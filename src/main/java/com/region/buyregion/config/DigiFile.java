package com.region.buyregion.config;

import com.region.buyregion.BuyRegion;

import java.io.*;

public class DigiFile<T> {
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
            BuyRegion.instance.getLogger().info("Error occurred loading " + file.getName());
        }
    }

    public void save() {
        try {
            ObjectOutputStream tmp = new ObjectOutputStream(new FileOutputStream(file.getPath()));
            tmp.writeObject(o);
            tmp.flush();
            tmp.close();
        } catch(Exception e) {
            e.printStackTrace();
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

