package fr.orakun.redo.core.timeline;

import fr.orakun.redo.core.json.Json;

import java.util.ArrayDeque;
import java.util.Deque;

/** Unlimited undo/redo by storing JSON snapshots of the sequence. */
public final class EditHistory {
    private final Deque<String> undo = new ArrayDeque<String>();
    private final Deque<String> redo = new ArrayDeque<String>();
    private static final int MAX = 500;

    /** Call before every change. */
    public void push(Sequence current) {
        undo.push(Json.write(current.toJson()));
        if (undo.size() > MAX) undo.removeLast();
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public Sequence undo(Sequence current) {
        if (undo.isEmpty()) return current;
        redo.push(Json.write(current.toJson()));
        return Sequence.fromJson(Json.parseObject(undo.pop()));
    }

    public Sequence redo(Sequence current) {
        if (redo.isEmpty()) return current;
        undo.push(Json.write(current.toJson()));
        return Sequence.fromJson(Json.parseObject(redo.pop()));
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
