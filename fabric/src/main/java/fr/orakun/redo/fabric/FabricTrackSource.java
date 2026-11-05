package fr.orakun.redo.fabric;

import fr.orakun.redo.core.record.TrackSource;
import fr.orakun.redo.core.track.EntitySample;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/** The client world as seen by the recorder. */
final class FabricTrackSource implements TrackSource {
    static final FabricTrackSource INSTANCE = new FabricTrackSource();
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final EntityState state = new EntityState();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private FabricTrackSource() {
    }

    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.player != null;
    }

    @Override
    public boolean self(EntityState out) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        fill(out, p);
        return true;
    }

    @Override
    public void entities(Visitor v) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        for (Entity e : level.entitiesForRendering()) {
            if (e == mc.player || e.isRemoved()) continue;
            state.reset();
            fill(state, e);
            v.visit(state);
        }
    }

    static void fill(EntityState s, Entity e) {
        s.id = e.getId();
        s.uuid = e.getStringUUID();
        s.name = e.getName().getString();
        s.type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        s.player = e instanceof Player;
        s.x = e.getX();
        s.y = e.getY();
        s.z = e.getZ();
        s.yaw = e.getYRot();
        s.pitch = e.getXRot();
        s.headYaw = e.getYHeadRot();
        s.bodyYaw = e.getYRot();
        int f = 0;
        if (e.isShiftKeyDown()) f |= EntitySample.SNEAK;
        if (e.isSprinting()) f |= EntitySample.SPRINT;
        if (e.onGround()) f |= EntitySample.ON_GROUND;
        if (e.isSwimming()) f |= EntitySample.SWIMMING;
        if (e.isPassenger()) f |= EntitySample.RIDING;
        if (e.isInvisible()) f |= EntitySample.INVISIBLE;
        if (e instanceof LivingEntity) {
            LivingEntity le = (LivingEntity) e;
            if (le.isFallFlying()) f |= EntitySample.ELYTRA;
            if (le.isSleeping()) f |= EntitySample.SLEEPING;
            s.hurt = le.hurtTime > 0;
            s.dead = le.isDeadOrDying();
            s.swinging = le.swinging;
            s.usingItem = le.isUsingItem();
            s.bodyYaw = le.yBodyRot;
        }
        s.flags = f;
    }

    @Override
    public String[] equipment() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        String[] out = new String[SLOTS.length];
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack st = p.getItemBySlot(SLOTS[i]);
            out[i] = st.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
        }
        return out;
    }

    @Override
    public int height(int x, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
    }

    @Override
    public String block(int x, int y, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return "minecraft:air";
        pos.set(x, y, z);
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
}
