package fr.orakun.redo.core.prompt;

import fr.orakun.redo.core.director.Landmark;
import fr.orakun.redo.core.director.Scene;

import java.util.EnumSet;
import java.util.Set;

/** A part of the scenery named in a request: "l'arbre au centre du hub", "la plus grande tour"... */
public final class PlaceQuery {
    public final Set<Landmark.Kind> kinds = EnumSet.noneOf(Landmark.Kind.class);
    public Scene.Pick pick = Scene.Pick.ANY;
    /** Compass direction from the centre ("nord", "sud-est"...), or null. */
    public String direction;
    /** Words its description must contain ("Balise"), or null. */
    public String labelHint;
    /** The centre of the place itself ("autour du hub"), when no kind is named. */
    public boolean wholePlace;
    /** What the user called it, for the reply ("arbre"). */
    public String noun = "";

    @Override
    public String toString() {
        return (wholePlace ? "place" : kinds.toString()) + " " + pick + (direction == null ? "" : " " + direction)
                + (labelHint == null ? "" : " '" + labelHint + "'");
    }
}
