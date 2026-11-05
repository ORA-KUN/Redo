package fr.orakun.redo.legacy;

import fr.orakun.redo.core.director.WorldQuery;
import fr.orakun.redo.core.math.Vec3d;
import net.minecraft.block.Block;
import net.minecraft.block.BlockNewLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
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

    @Override
    public String blockName(int x, int y, int z) {
        WorldClient w = world();
        if (w == null) return null;
        if (y < 0 || y > 255) return "minecraft:air";
        Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
        if (c == null || c.isEmpty()) return null;
        IBlockState state = w.getBlockState(new BlockPos(x, y, z));
        Block b = state.getBlock();
        if (b.getMaterial() == Material.air) return "minecraft:air";
        // 1.8.9 logs carry their species in the state: named like recent versions ("oak_log")
        if (b instanceof BlockOldLog) return "minecraft:" + state.getValue(BlockOldLog.VARIANT).getName() + "_log";
        if (b instanceof BlockNewLog) return "minecraft:" + state.getValue(BlockNewLog.VARIANT).getName() + "_log";
        ResourceLocation id = Block.blockRegistry.getNameForObject(b);
        return id == null ? null : id.toString();
    }

    @Override
    public int surfaceY(int x, int z) {
        WorldClient w = world();
        if (w == null) return Integer.MIN_VALUE;
        Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
        if (c == null || c.isEmpty()) return Integer.MIN_VALUE;
        // the highest block that stops movement or is liquid: leaves, glass and fences count
        return w.getPrecipitationHeight(new BlockPos(x, 0, z)).getY() - 1;
    }

    @Override
    public int minY() {
        return 0;
    }
}
