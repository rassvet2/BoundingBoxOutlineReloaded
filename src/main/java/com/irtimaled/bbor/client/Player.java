package com.irtimaled.bbor.client;

import com.irtimaled.bbor.client.models.Point;
import com.irtimaled.bbor.common.models.Coords;
import com.irtimaled.bbor.common.models.DimensionId;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.util.math.Vec3d;

public class Player {
    private static double x;
    private static double y;
    private static double z;
    private static double activeY;
    private static DimensionId dimensionId;

    public static void setPosition(double partialTicks, ClientPlayerEntity player) {
        Vec3d pos = player.getTrackedPosition().getPos(); // TODO: ???

        x = pos.x + (player.getX() - pos.x) * partialTicks;
        y = pos.y + (player.getY() - pos.y) * partialTicks;
        z = pos.z + (player.getZ() - pos.z) * partialTicks;
        dimensionId = DimensionId.from(player.getEntityWorld().getRegistryKey());
    }

    static void setActiveY() {
        activeY = y;
    }

    public static double getX() {
        return x;
    }

    public static double getY() {
        return y;
    }

    public static double getZ() {
        return z;
    }

    public static double getMaxY(double configMaxY) {
        if (configMaxY == -1) {
            return activeY;
        }
        if (configMaxY == 0) {
            return y;
        }
        return configMaxY;
    }

    public static DimensionId getDimensionId() {
        return dimensionId;
    }

    public static Coords getCoords() {
        return new Coords(x, y, z);
    }

    public static Point getPoint() {
        return new Point(x, y, z);
    }
}
