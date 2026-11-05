package fr.orakun.redo.core.editor;

import fr.orakun.redo.core.camera.CameraState;

/**
 * The camera the user flies in the replay. Movement is relative to where it looks, like the
 * spectator mode: forward/strafe on the horizontal plane, up/down on the vertical axis.
 */
public final class FreeCamera {
    private double x, y, z, yaw, pitch, roll, fov = CameraState.DEFAULT_FOV;
    private double vx, vy, vz;
    private boolean placed;

    public boolean isPlaced() {
        return placed;
    }

    public void place(CameraState s) {
        if (s == null) return;
        x = s.x;
        y = s.y;
        z = s.z;
        yaw = s.yaw;
        pitch = s.pitch;
        roll = s.roll;
        fov = s.fov;
        vx = vy = vz = 0;
        placed = true;
    }

    public CameraState state() {
        return new CameraState(x, y, z, yaw, pitch, roll, fov);
    }

    /** Mouse look, in screen pixels. */
    public void look(double dx, double dy) {
        yaw += dx * 0.25;
        pitch = Math.max(-90, Math.min(90, pitch + dy * 0.25));
    }

    public void roll(double deg) {
        roll = Math.max(-45, Math.min(45, roll + deg));
    }

    public void zoom(double deltaFov) {
        fov = Math.max(10, Math.min(120, fov + deltaFov));
    }

    public void resetLens() {
        roll = 0;
        fov = CameraState.DEFAULT_FOV;
    }

    /**
     * Moves the camera.
     *
     * @param in forward, strafe (right is positive), up, fast (non-zero = sprint)
     * @param speed blocks per second
     */
    public void update(double dtMs, double[] in, double speed) {
        double dt = Math.min(dtMs, 100) / 1000.0;
        double f = in[0], s = in[1], u = in[2];
        double sp = speed * (in[3] != 0 ? 3 : 1);
        double rad = Math.toRadians(yaw);
        // Minecraft yaw: 0 = +Z (south), 90 = -X (west)
        double fx = -Math.sin(rad), fz = Math.cos(rad);
        double rx = -fz, rz = fx;
        double tx = (fx * f - rx * s) * sp, tz = (fz * f - rz * s) * sp, ty = u * sp;
        double len = Math.sqrt(f * f + s * s);
        if (len > 1) {
            tx /= len;
            tz /= len;
        }
        // smooth acceleration so movements look like a camera operator, not a teleport
        double k = 1 - Math.exp(-dt * 10);
        vx += (tx - vx) * k;
        vy += (ty - vy) * k;
        vz += (tz - vz) * k;
        x += vx * dt;
        y += vy * dt;
        z += vz * dt;
    }
}
