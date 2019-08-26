package com.region.buyregion.regions;

import com.region.buyregion.BuyRegion;

public class RentableRegion {
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

    public RentableRegion() {
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

    public RentableRegion(String input) {
        try {
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
        } catch(Exception e) {
            BuyRegion.instance.getLogger().severe("An error occurred while instantiating a RentableRegion.");
        }
    }

    public String toString() {
        return String.format("%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s%%%%%%%s", this.worldName, this.regionName, this.renter, this.signLocationX, this.signLocationY, this.signLocationZ, this.signLocationPitch, this.signLocationYaw, this.signDirection, this.signLine1, this.signLine2, this.signLine3, this.signLine4, this.signType);
    }

}

