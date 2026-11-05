package fr.orakun.redo.legacy;

import fr.orakun.redo.core.director.WorldQuery;
import fr.orakun.redo.core.math.Vec3d;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.chunk.Chunk;

/** Block access for the camera director on 1.8.9. */
final class LegacyWorldQuery implements WorldQuery {
    private static WorldClient world() {
        return Minecraft.getMinecraft().theWorld;
    }

    @Override
    public boolean isSolid(double x, double y, double z) {
        WorldClient w = world();
        if (w == null) return false;
        Material m = w.getBlockState(new BlockPos(Math.floor(x), Math.floor(y), Math.floor(z))).getBlock().getMaterial();
        return m.blocksMovement() || m.isLiquid();
    }

    @Override
    public boolean isBlocked(Vec3d from, Vec3d to) {
        WorldClient w = world();
        if (w == null) return false;
        MovingObjectPosition r = w.rayTraceBlocks(new Vec3(from.x, from.y, from.z), new Vec3(to.x, to.y, to.z), false, true, false);
        return r != null && r.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    @Override
    public boolean isLoaded(double x, double z) {
        WorldClient w = world();
        if (w == null) return false;
        Chunk c = w.getChunkFromChunkCoords(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
        return c != null && !c.isEmpty();
    }
}
