package fr.orakun.redo.core.director;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sorts blocks by registry name ("minecraft:oak_log" on recent versions, "minecraft:log" on 1.8.9)
 * into what matters to read a scenery: trees, built things, water, natural ground...
 */
public final class BlockKinds {
    public enum Kind {
        AIR, PLANT, LOG, LEAVES, WATER, LAVA, NATURAL, BUILT, NOTABLE
    }

    private static final Map<String, Kind> CACHE = new HashMap<String, Kind>();

    private BlockKinds() {
    }

    static String path(String name) {
        if (name == null) return "";
        int i = name.indexOf(':');
        return (i >= 0 ? name.substring(i + 1) : name).toLowerCase(Locale.ROOT);
    }

    public static Kind of(String name) {
        if (name == null) return Kind.AIR;
        synchronized (CACHE) {
            Kind k = CACHE.get(name);
            if (k == null) {
                k = classify(path(name));
                CACHE.put(name, k);
            }
            return k;
        }
    }

    private static boolean any(String p, String... parts) {
        for (String s : parts) if (p.contains(s)) return true;
        return false;
    }

    private static boolean is(String p, String... names) {
        for (String s : names) if (p.equals(s)) return true;
        return false;
    }

    static Kind classify(String p) {
        if (p.isEmpty() || is(p, "air", "cave_air", "void_air", "light", "structure_void", "barrier")) return Kind.AIR;
        if (is(p, "water", "flowing_water", "bubble_column")) return Kind.WATER;
        if (is(p, "lava", "flowing_lava")) return Kind.LAVA;
        if (is(p, "beacon", "nether_portal", "portal", "end_portal", "end_portal_frame", "end_gateway", "enchanting_table",
                "spawner", "mob_spawner", "trial_spawner", "vault", "conduit", "bell", "campfire", "soul_campfire", "jukebox",
                "lectern", "anvil", "chipped_anvil", "damaged_anvil", "dragon_egg", "respawn_anchor", "lodestone", "brewing_stand",
                "ender_chest", "dragon_head")) {
            return Kind.NOTABLE;
        }
        if (p.endsWith("_log") || p.endsWith("_wood") || p.endsWith("_stem") || p.endsWith("_hyphae") || is(p, "log", "log2")) {
            return p.startsWith("stripped_") ? Kind.BUILT : Kind.LOG;
        }
        if (p.contains("leaves") || is(p, "nether_wart_block", "warped_wart_block", "shroomlight", "red_mushroom_block",
                "brown_mushroom_block")) {
            return Kind.LEAVES;
        }
        // small things on the ground or walls: never the surface of a place
        if (p.endsWith("flower") || p.endsWith("_tulip") || p.endsWith("sapling") || p.endsWith("_carpet") || p.endsWith("torch")
                || p.endsWith("_button") || p.endsWith("pressure_plate") || p.endsWith("_sign") || p.endsWith("_banner")
                || p.endsWith("rail") || p.endsWith("_head") || p.endsWith("_skull") || p.endsWith("_coral") || p.endsWith("_fan")
                || p.startsWith("potted_") || p.endsWith("_vines") || p.endsWith("_roots") || p.endsWith("_sprouts")
                || is(p, "short_grass", "tall_grass", "tallgrass", "double_plant", "fern", "large_fern", "dead_bush", "deadbush",
                "dandelion", "poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower",
                "lilac", "rose_bush", "peony", "brown_mushroom", "red_mushroom", "sugar_cane", "reeds", "vine", "carpet",
                "redstone_wire", "lever", "sign", "wall_sign", "standing_sign", "banner", "wall_banner", "standing_banner",
                "flower_pot", "skull", "ladder", "tripwire", "tripwire_hook", "web", "cobweb", "seagrass", "tall_seagrass", "kelp",
                "kelp_plant", "lily_pad", "waterlily", "wheat", "carrots", "potatoes", "beetroots", "pumpkin_stem", "melon_stem",
                "bamboo", "glow_lichen", "moss_carpet", "pointed_dripstone", "snow_layer", "torchflower", "pink_petals",
                "hanging_roots", "spore_blossom", "nether_sprouts", "string", "fire", "soul_fire", "pitcher_plant", "wildflowers",
                "leaf_litter", "bush", "firefly_bush", "short_dry_grass", "tall_dry_grass")) {
            return Kind.PLANT;
        }
        if (p.endsWith("_ore") || is(p, "grass", "grass_block", "dirt", "coarse_dirt", "podzol", "mycelium", "rooted_dirt", "mud",
                "farmland", "dirt_path", "grass_path", "sand", "red_sand", "gravel", "clay", "stone", "granite", "diorite",
                "andesite", "deepslate", "tuff", "calcite", "dripstone_block", "bedrock", "netherrack", "soul_sand", "soul_soil",
                "basalt", "blackstone", "end_stone", "ice", "packed_ice", "blue_ice", "frosted_ice", "snow", "snow_block",
                "powder_snow", "sandstone", "red_sandstone", "moss_block", "cactus", "pumpkin", "melon", "magma_block", "magma",
                "amethyst_block", "budding_amethyst", "sculk", "pale_moss_block", "crimson_nylium",
                "warped_nylium", "suspicious_sand", "suspicious_gravel", "mangrove_roots", "muddy_mangrove_roots")) {
            return Kind.NATURAL;
        }
        // anything else is made by hand: planks, bricks, glass, wool, concrete, slabs, stairs, fences...
        // obsidian too: mostly portal frames, and the scan must look under it for the portal
        return Kind.BUILT;
    }

    /** French name of a tree species from its log ("chêne"), or null when unknown (1.8.9 logs). */
    public static String species(String name) {
        String p = path(name);
        if (p.startsWith("dark_oak")) return "chêne noir";
        if (p.startsWith("oak")) return "chêne";
        if (p.startsWith("birch")) return "bouleau";
        if (p.startsWith("spruce")) return "sapin";
        if (p.startsWith("jungle")) return "arbre de la jungle";
        if (p.startsWith("acacia")) return "acacia";
        if (p.startsWith("mangrove")) return "palétuvier";
        if (p.startsWith("cherry")) return "cerisier";
        if (p.startsWith("pale_oak")) return "chêne pâle";
        if (p.startsWith("crimson")) return "champignon carmin";
        if (p.startsWith("warped")) return "champignon biscornu";
        if (p.contains("mushroom")) return "champignon géant";
        return null;
    }

    /** French name of a building material ("briques de pierre"), from its most common block. */
    public static String material(String name) {
        String p = path(name);
        if (p.contains("deepslate")) return "ardoise";
        if (p.contains("stone_brick") || p.equals("stonebrick")) return "briques de pierre";
        if (p.contains("nether_brick")) return "briques du Nether";
        if (p.contains("end_stone")) return "pierre de l'End";
        if (p.contains("mud_brick")) return "briques de boue";
        if (p.contains("brick")) return "briques";
        if (p.contains("cobble")) return "pierre";
        if (p.contains("planks") || p.contains("log") || p.contains("wood") || p.endsWith("_stem") || p.contains("fence")
                || p.contains("_door") || p.contains("bamboo")) {
            return "bois";
        }
        if (p.contains("glass")) return "verre";
        if (p.contains("obsidian")) return "obsidienne";
        if (p.contains("quartz")) return "quartz";
        if (p.contains("sandstone")) return "grès";
        if (p.contains("wool")) return "laine";
        if (p.contains("concrete")) return "béton";
        if (p.contains("terracotta") || p.contains("hardened_clay")) return "terre cuite";
        if (p.contains("prismarine") || p.contains("sea_lantern")) return "prismarine";
        if (p.contains("purpur")) return "purpur";
        if (p.contains("blackstone")) return "roche noire";
        if (p.contains("copper")) return "cuivre";
        if (p.contains("iron")) return "fer";
        if (p.contains("gold")) return "or";
        if (p.contains("diamond")) return "diamant";
        if (p.contains("emerald")) return "émeraude";
        if (p.contains("granite")) return "granite";
        if (p.contains("diorite")) return "diorite";
        if (p.contains("andesite")) return "andésite";
        if (p.contains("stone")) return "pierre";
        if (p.contains("snow")) return "neige";
        if (p.contains("ice")) return "glace";
        if (p.contains("hay")) return "foin";
        return p.replace('_', ' ');
    }

    /** French name of a remarkable block. */
    public static String notableLabel(String name) {
        String p = path(name);
        if (p.equals("beacon")) return "Balise (beacon)";
        if (p.equals("nether_portal") || p.equals("portal")) return "Portail du Nether";
        if (p.startsWith("end_portal") || p.equals("end_gateway")) return "Portail de l'End";
        if (p.equals("enchanting_table")) return "Table d'enchantement";
        if (p.contains("spawner")) return "Générateur de monstres";
        if (p.equals("vault")) return "Coffre-fort des épreuves";
        if (p.equals("conduit")) return "Conduit";
        if (p.equals("bell")) return "Cloche";
        if (p.contains("campfire")) return "Feu de camp";
        if (p.equals("jukebox")) return "Jukebox";
        if (p.equals("lectern")) return "Pupitre";
        if (p.contains("anvil")) return "Enclume";
        if (p.equals("dragon_egg")) return "Œuf de dragon";
        if (p.equals("dragon_head")) return "Tête de dragon";
        if (p.equals("respawn_anchor")) return "Ancre de réapparition";
        if (p.equals("lodestone")) return "Magnétite";
        if (p.equals("brewing_stand")) return "Alambic";
        if (p.equals("ender_chest")) return "Coffre de l'Ender";
        return p.replace('_', ' ');
    }
}
