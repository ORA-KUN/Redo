package fr.orakun.redo.core.export;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum ExportFormat {
    MP4_H264("MP4 (H.264)", ".mp4", false),
    MP4_H265("MP4 (H.265)", ".mp4", false),
    PRORES_422HQ("ProRes 422 HQ", ".mov", false),
    PRORES_4444("ProRes 4444 (alpha)", ".mov", true),
    PNG_SEQUENCE("Séquence PNG", "", true);

    public final String label;
    public final String extension;
    public final boolean alpha;

    ExportFormat(String label, String extension, boolean alpha) {
        this.label = label;
        this.extension = extension;
        this.alpha = alpha;
    }

    public boolean isSequence() {
        return this == PNG_SEQUENCE;
    }

    public ExportFormat next() {
        ExportFormat[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    /** Encoder arguments placed after the input and filter arguments. */
    public List<String> encoderArgs(int crf) {
        switch (this) {
            case MP4_H265:
                return Arrays.asList("-c:v", "libx265", "-preset", "medium", "-crf", String.valueOf(Math.min(51, crf + 4)),
                        "-pix_fmt", "yuv420p", "-tag:v", "hvc1", "-movflags", "+faststart");
            case PRORES_422HQ:
                return Arrays.asList("-c:v", "prores_ks", "-profile:v", "3", "-pix_fmt", "yuv422p10le",
                        "-vendor", "apl0");
            case PRORES_4444:
                return Arrays.asList("-c:v", "prores_ks", "-profile:v", "4", "-pix_fmt", "yuva444p10le",
                        "-alpha_bits", "16", "-vendor", "apl0");
            case PNG_SEQUENCE:
                return Arrays.asList("-c:v", "png", "-pix_fmt", "rgba", "-f", "image2");
            case MP4_H264:
            default:
                return new ArrayList<String>(Arrays.asList("-c:v", "libx264", "-preset", "medium",
                        "-crf", String.valueOf(crf), "-pix_fmt", "yuv420p", "-movflags", "+faststart"));
        }
    }

    public static ExportFormat byName(String name) {
        if (name != null) {
            for (ExportFormat f : values()) if (f.name().equalsIgnoreCase(name)) return f;
        }
        return MP4_H264;
    }
}
