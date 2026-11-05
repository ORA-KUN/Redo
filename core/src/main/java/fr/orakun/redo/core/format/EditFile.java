package fr.orakun.redo.core.format;

import fr.orakun.redo.core.RedoLog;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.timeline.Sequence;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Edits and proposed shots of a recording, saved next to it ("name.redo.edit.json"). */
public final class EditFile {
    public final List<Sequence> sequences = new ArrayList<Sequence>();
    public final List<ShotSuggestion> suggestions = new ArrayList<ShotSuggestion>();
    public int active;
    /** True once the automatic director ran on this recording. */
    public boolean analysed;

    public Sequence activeSequence() {
        if (sequences.isEmpty()) sequences.add(new Sequence());
        if (active < 0 || active >= sequences.size()) active = 0;
        return sequences.get(active);
    }

    public static EditFile load(File f) {
        EditFile e = new EditFile();
        if (!f.isFile()) return e;
        try {
            InputStream in = new FileInputStream(f);
            String text;
            try {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
                text = new String(bo.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
            Map<String, Object> m = Json.parseObject(text);
            for (Map<String, Object> s : Json.objects(Json.getList(m, "sequences"))) e.sequences.add(Sequence.fromJson(s));
            for (Map<String, Object> s : Json.objects(Json.getList(m, "suggestions"))) e.suggestions.add(ShotSuggestion.fromJson(s));
            e.active = Json.getInt(m, "active", 0);
            e.analysed = Json.getBool(m, "analysed", false);
        } catch (Exception ex) {
            RedoLog.error("Could not read " + f, ex);
        }
        return e;
    }

    public void save(File f) {
        Map<String, Object> m = Json.obj();
        m.put("active", active);
        m.put("analysed", analysed);
        List<Object> seq = new ArrayList<Object>();
        for (Sequence s : sequences) seq.add(s.toJson());
        m.put("sequences", seq);
        List<Object> sug = new ArrayList<Object>();
        for (ShotSuggestion s : suggestions) sug.add(s.toJson());
        m.put("suggestions", sug);
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                w.write(Json.pretty(m));
            } finally {
                w.close();
            }
            if (f.exists() && !f.delete()) throw new IOException("Impossible de remplacer " + f);
            if (!tmp.renameTo(f)) throw new IOException("Impossible de renommer " + tmp);
        } catch (IOException ex) {
            RedoLog.error("Could not save " + f, ex);
        }
    }
}
