package fr.orakun.redo.legacy;

import com.mojang.authlib.GameProfile;
import fr.orakun.redo.core.camera.CameraState;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * The point of view of the editor's camera on 1.8.9. It is never added to the world: the game just
 * renders from it while the recorded player stays visible.
 */
public final class CameraEntity extends EntityOtherPlayerMP {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5f1d2c1e-0b7a-4e7c-9d2e-7265646f6361"), "RedoCamera");

    public CameraEntity(World world) {
        super(world, PROFILE);
        noClip = true;
    }

    /** Puts the camera exactly at this pose (no interpolation from the previous frame). */
    void place(CameraState c) {
        posX = prevPosX = lastTickPosX = c.x;
        posY = prevPosY = lastTickPosY = c.y;
        posZ = prevPosZ = lastTickPosZ = c.z;
        rotationYaw = prevRotationYaw = rotationYawHead = prevRotationYawHead = (float) c.yaw;
        rotationPitch = prevRotationPitch = (float) c.pitch;
    }

    @Override
    public float getEyeHeight() {
        return 0.0F;
    }

    @Override
    public void onUpdate() {
    }

    @Override
    public void onLivingUpdate() {
    }

    @Override
    public boolean isInvisible() {
        return true;
    }
}
