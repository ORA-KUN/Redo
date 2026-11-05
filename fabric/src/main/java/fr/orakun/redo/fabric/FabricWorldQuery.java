package fr.orakun.redo.fabric;

import fr.orakun.redo.core.director.WorldQuery;
import fr.orakun.redo.core.math.Vec3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Block access for the automatic director, on the replay world. */
final class FabricWorldQuery implements WorldQuery {
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private static ClientLevel level() {
        return Minecraft.getInstance().level;
    }

    @Override
    public boolean isSolid(double x, double y, double z) {
        ClientLevel l = level();
        if (l == null) return false;
        pos.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        BlockState s = l.getBlockState(pos);
        return Compat.blocksMotion(s) || s.liquid();
    }

    @Override
    public boolean isBlocked(Vec3d from, Vec3d to) {
        ClientLevel l = level();
        if (l == null) return false;
        HitResult r = l.clip(new ClipContext(new Vec3(from.x, from.y, from.z), new Vec3(to.x, to.y, to.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return r.getType() != HitResult.Type.MISS;
    }

    @Override
    public boolean isLoaded(double x, double z) {
        ClientLevel l = level();
        return l != null && l.hasChunk(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4);
    }
}
