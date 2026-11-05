package fr.orakun.redo.legacy;

import fr.orakun.redo.core.record.TrackSource;
import fr.orakun.redo.core.track.EntitySample;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.List;

/** The 1.8.9 client world as seen by the recorder. */
final class LegacyTrackSource implements TrackSource {
    static final LegacyTrackSource INSTANCE = new LegacyTrackSource();
    private final EntityState state = new EntityState();

    private LegacyTrackSource() {
    }

    @Override
    public boolean inWorld() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.theWorld != null && mc.thePlayer != null;
    }

    @Override
    public boolean self(EntityState out) {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return false;
        fill(out, p);
        return true;
    }

    @Override
    public void entities(Visitor v) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient w = mc.theWorld;
        if (w == null) return;
        List<Entity> list = new ArrayList<Entity>(w.loadedEntityList);
        for (Entity e : list) {
            if (e == mc.thePlayer || e instanceof CameraEntity) continue;
            state.reset();
            fill(state, e);
            v.visit(state);
        }
    }

    static void fill(EntityState s, Entity e) {
        s.id = e.getEntityId();
        s.uuid = e.getUniqueID().toString();
        s.name = e.getName();
        s.player = e instanceof EntityPlayer;
        if (s.player) {
            s.type = "minecraft:player";
        } else {
            String n = EntityList.getEntityString(e);
            s.type = n == null ? "minecraft:unknown" : "minecraft:" + n;
        }
        s.x = e.posX;
        s.y = e.posY;
        s.z = e.posZ;
        s.yaw = e.rotationYaw;
        s.pitch = e.rotationPitch;
        s.headYaw = e.getRotationYawHead();
        s.bodyYaw = e.rotationYaw;
        int f = 0;
        if (e.isSneaking()) f |= EntitySample.SNEAK;
        if (e.isSprinting()) f |= EntitySample.SPRINT;
        if (e.onGround) f |= EntitySample.ON_GROUND;
        if (e.isInWater()) f |= EntitySample.SWIMMING;
        if (e.isRiding()) f |= EntitySample.RIDING;
        if (e.isInvisible()) f |= EntitySample.INVISIBLE;
        if (e instanceof EntityLivingBase) {
            EntityLivingBase le = (EntityLivingBase) e;
            if (le.isPlayerSleeping()) f |= EntitySample.SLEEPING;
            s.hurt = le.hurtTime > 0;
            s.dead = le.getHealth() <= 0.0F || le.deathTime > 0;
            s.swinging = le.isSwingInProgress;
            s.bodyYaw = le.renderYawOffset;
            if (le instanceof EntityPlayer) s.usingItem = ((EntityPlayer) le).isUsingItem();
        }
        s.flags = f;
    }

    private static String item(ItemStack st) {
        if (st == null || st.getItem() == null) return "";
        ResourceLocation id = Item.itemRegistry.getNameForObject(st.getItem());
        return id == null ? "" : id.toString();
    }

    @Override
    public String[] equipment() {
        EntityPlayerSP p = Minecraft.getMinecraft().thePlayer;
        if (p == null) return null;
        return new String[]{item(p.getHeldItem()), "", item(p.getCurrentArmor(3)), item(p.getCurrentArmor(2)),
                item(p.getCurrentArmor(1)), item(p.getCurrentArmor(0))};
    }

    @Override
    public int height(int x, int z) {
        WorldClient w = Minecraft.getMinecraft().theWorld;
        if (w == null) return Integer.MIN_VALUE;
        Chunk c = w.getChunkFromChunkCoords(x >> 4, z >> 4);
        if (c == null || c.isEmpty()) return Integer.MIN_VALUE;
        return c.getHeightValue(x & 15, z & 15) - 1;
    }

    @Override
    public String block(int x, int y, int z) {
        WorldClient w = Minecraft.getMinecraft().theWorld;
        if (w == null) return "minecraft:air";
        Block b = w.getBlockState(new BlockPos(x, y, z)).getBlock();
        ResourceLocation id = Block.blockRegistry.getNameForObject(b);
        return id == null ? "minecraft:air" : id.toString();
    }
}
