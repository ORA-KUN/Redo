package fr.orakun.redo.core.json;

/** An object that can describe itself as a JSON tree (see {@link Json}). */
public interface JsonSerializable {
    Object toJson();
}
