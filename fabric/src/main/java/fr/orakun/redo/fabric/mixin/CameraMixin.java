package fr.orakun.redo.fabric.mixin;

import fr.orakun.redo.core.camera.CameraState;
import fr.orakun.redo.fabric.RedoClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the camera where the editor wants it during a replay. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow @Final private Quaternionf rotation;
    @Shadow @Final private Vector3f forwards;
    @Shadow @Final private Vector3f up;
    @Shadow @Final private Vector3f left;
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void redo$override(Level level, Entity entity, boolean detachedCamera, boolean mirrored, float partial, CallbackInfo ci) {
        CameraState c = RedoClient.cameraOverride();
        if (c != null) {
            setPosition(c.x, c.y, c.z);
            setRotation((float) c.yaw, (float) c.pitch);
            if (Math.abs(c.roll) > 1e-4) {
                float d = (float) (Math.PI / 180.0);
                rotation.rotationYXZ((float) Math.PI - (float) c.yaw * d, -(float) c.pitch * d, -(float) c.roll * d);
                forwards.set(0.0F, 0.0F, -1.0F).rotate(rotation);
                up.set(0.0F, 1.0F, 0.0F).rotate(rotation);
                left.set(-1.0F, 0.0F, 0.0F).rotate(rotation);
            }
            detached = true;
        }
        Camera self = (Camera) (Object) this;
        Vec3 p = self.position();
        RedoClient.cameraRendered(p.x, p.y, p.z, self.yRot(), self.xRot());
    }
}
