package fr.orakun.redo.core.track;

/** Static description of an entity seen during the recording. */
public final class EntityInfo {
    public final int id;
    public final String uuid;
    public final String name;
    /** Registry id such as "minecraft:player" or "minecraft:zombie". */
    public final String type;
    public final boolean player;
    public final boolean self;

    public EntityInfo(int id, String uuid, String name, String type, boolean player, boolean self) {
        this.id = id;
        this.uuid = uuid == null ? "" : uuid;
        this.name = name == null ? "" : name;
        this.type = type == null ? "" : type;
        this.player = player;
        this.self = self;
    }

    /** Short readable name: player name, or entity type without namespace. */
    public String displayName() {
        if (!name.isEmpty()) return name;
        String t = type;
        int i = t.indexOf(':');
        return i >= 0 ? t.substring(i + 1) : t;
    }

    public boolean isHostileMob() {
        String t = type;
        return t.endsWith("zombie") || t.endsWith("skeleton") || t.endsWith("creeper") || t.endsWith("spider")
                || t.endsWith("enderman") || t.endsWith("witch") || t.endsWith("blaze") || t.endsWith("ghast")
                || t.endsWith("wither") || t.endsWith("ender_dragon") || t.endsWith("pillager")
                || t.endsWith("vindicator") || t.endsWith("ravager") || t.endsWith("warden") || t.endsWith("piglin_brute")
                || t.endsWith("slime") || t.endsWith("magma_cube") || t.endsWith("guardian") || t.endsWith("phantom")
                || t.endsWith("drowned") || t.endsWith("husk") || t.endsWith("stray") || t.endsWith("hoglin")
                || t.equalsIgnoreCase("Zombie") || t.equalsIgnoreCase("Skeleton") || t.equalsIgnoreCase("Creeper");
    }
}
