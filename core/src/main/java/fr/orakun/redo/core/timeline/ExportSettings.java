package fr.orakun.redo.core.timeline;

import fr.orakun.redo.core.export.ExportFormat;
import fr.orakun.redo.core.json.Json;
import fr.orakun.redo.core.json.JsonSerializable;

import java.util.Map;

public final class ExportSettings implements JsonSerializable {
    public ExportFormat format = ExportFormat.MP4_H264;
    public int width = 1920;
    public int height = 1080;
    public int fps = 60;
    /** 0 = best quality, 51 = worst (x264 CRF). */
    public int crf = 18;
    /** "sequence" = whole edit, "clips" = one file per clip. */
    public boolean onePerClip = false;

    public ExportSettings copy() {
        return fromJson(Json.parseObject(Json.write(toJson())));
    }

    @Override
    public Object toJson() {
        Map<String, Object> m = Json.obj();
        m.put("format", format.name());
        m.put("width", width);
        m.put("height", height);
        m.put("fps", fps);
        m.put("crf", crf);
        m.put("onePerClip", onePerClip);
        return m;
    }

    public static ExportSettings fromJson(Map<String, Object> m) {
        ExportSettings e = new ExportSettings();
        e.format = ExportFormat.byName(Json.getString(m, "format", null));
        e.width = Json.getInt(m, "width", 1920);
        e.height = Json.getInt(m, "height", 1080);
        e.fps = Json.getInt(m, "fps", 60);
        e.crf = Json.getInt(m, "crf", 18);
        e.onePerClip = Json.getBool(m, "onePerClip", false);
        return e;
    }
}
