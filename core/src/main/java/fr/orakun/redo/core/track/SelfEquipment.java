package fr.orakun.redo.core.track;

import java.util.Arrays;

/** Items held and worn by the recording player, stored only when they change. */
public final class SelfEquipment {
    public final int timeMs;
    /** mainhand, offhand, feet, legs, chest, head (registry ids, "" = empty, "+" suffix = enchanted). */
    public final String[] slots;

    public SelfEquipment(int timeMs, String[] slots) {
        this.timeMs = timeMs;
        this.slots = slots;
    }

    public boolean sameAs(String[] other) {
        return Arrays.equals(slots, other);
    }
}
