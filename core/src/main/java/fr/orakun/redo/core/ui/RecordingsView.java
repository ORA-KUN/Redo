package fr.orakun.redo.core.ui;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.format.RedoArchive;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The list of recordings: open, rename, delete, open the folder. */
public final class RecordingsView implements View {
    /** What the list needs from the platform. */
    public interface Host {
        void openReplay(RedoArchive archive);

        void openFolder(File folder);

        void close();

        String clipboard();
    }

    private static final int ROW = 26;

    private static final class Entry {
        final File file;
        RedoArchive archive;
        String error;

        Entry(File file) {
            this.file = file;
        }
    }

    private final Host host;
    private final List<Entry> entries = new ArrayList<Entry>();
    private final List<Widget> widgets = new ArrayList<Widget>();
    private final TextField rename = new TextField();
    private Entry selected;
    private Entry confirmDelete;
    private boolean renaming;
    private int scroll;
    private int W = -1, H = -1;
    private int listX, listY, listW, listH;
    private long lastClick;
    private Entry lastClicked;
    private Widget pressed;
    private Button openButton, renameButton, deleteButton;
    private String status = "";

    public RecordingsView(Host host) {
        this.host = host;
        rename.maxLength = 80;
        rename.onSubmit = new TextField.Listener() {
            @Override
            public void submit(String text) {
                applyRename();
            }
        };
        reload();
    }

    public void reload() {
        entries.clear();
        for (File f : RedoArchive.list(Redo.recordingsDir())) {
            Entry e = new Entry(f);
            try {
                e.archive = RedoArchive.open(f);
            } catch (Exception ex) {
                e.error = ex.getMessage();
            }
            entries.add(e);
        }
        if (selected != null) {
            Entry keep = null;
            for (Entry e : entries) if (e.file.equals(selected.file)) keep = e;
            selected = keep;
        }
        if (selected == null && !entries.isEmpty()) selected = entries.get(0);
        updateButtons();
    }

    private boolean canOpen(Entry e) {
        if (e == null || e.archive == null) return false;
        String v = e.archive.meta.minecraftVersion;
        return v == null || v.isEmpty() || v.equals(Redo.minecraftVersion());
    }

    private void updateButtons() {
        if (openButton != null) openButton.enabled = canOpen(selected);
        if (renameButton != null) renameButton.enabled = selected != null;
        if (deleteButton != null) {
            deleteButton.enabled = selected != null;
            deleteButton.label = confirmDelete != null && confirmDelete == selected ? "Confirmer ?" : "Supprimer";
        }
    }

    private void layout(Gfx g) {
        if (g.width() == W && g.height() == H) return;
        W = g.width();
        H = g.height();
        listW = Math.min(W - 20, 520);
        listX = (W - listW) / 2;
        listY = 40;
        listH = H - listY - 46;
        widgets.clear();
        int by = H - 34;
        int bx = listX;
        openButton = add("Ouvrir", bx, by, 70, new Runnable() {
            @Override
            public void run() {
                open(selected);
            }
        });
        openButton.accent();
        bx += 74;
        renameButton = add("Renommer", bx, by, 70, new Runnable() {
            @Override
            public void run() {
                if (selected == null) return;
                renaming = true;
                rename.setText(selected.file.getName().replace(RedoArchive.EXTENSION, ""));
                rename.focusChanged(true);
            }
        });
        bx += 74;
        deleteButton = add("Supprimer", bx, by, 74, new Runnable() {
            @Override
            public void run() {
                delete();
            }
        });
        bx += 78;
        add("Dossier", bx, by, 60, new Runnable() {
            @Override
            public void run() {
                host.openFolder(Redo.recordingsDir());
            }
        });
        bx += 64;
        add("Actualiser", bx, by, 70, new Runnable() {
            @Override
            public void run() {
                reload();
            }
        });
        final Cycle<Boolean> auto = new Cycle<Boolean>("Enregistrer dès la connexion", new Boolean[]{true, false},
                Redo.config().autoRecord, new Cycle.Namer<Boolean>() {
            @Override
            public String name(Boolean v) {
                return v ? "oui" : "non";
            }
        }, new Cycle.Listener<Boolean>() {
            @Override
            public void changed(Boolean v) {
                Redo.config().autoRecord = v;
                Redo.config().save();
            }
        });
        auto.bounds(listX, by + 16, 200, 14);
        auto.tooltip = "Sinon, l'enregistrement se lance avec le bouton ● Enregistrer du menu Échap.";
        widgets.add(auto);
        add("Fermer", listX + listW - 60, by, 60, new Runnable() {
            @Override
            public void run() {
                host.close();
            }
        });
        rename.bounds(listX, by - 20, listW, 14);
        updateButtons();
    }

    private Button add(String label, int x, int y, int w, Runnable r) {
        Button b = new Button(label, r);
        b.bounds(x, y, w, 14);
        widgets.add(b);
        return b;
    }

    private void open(Entry e) {
        if (e == null) return;
        if (e.archive == null) {
            status = "Fichier illisible : " + e.error;
            return;
        }
        if (!canOpen(e)) {
            status = "Cet enregistrement vient de Minecraft " + e.archive.meta.minecraftVersion
                    + " : ouvrez-le avec cette version du jeu.";
            return;
        }
        host.openReplay(e.archive);
    }

    private void delete() {
        if (selected == null) return;
        if (confirmDelete != selected) {
            confirmDelete = selected;
            updateButtons();
            return;
        }
        File f = selected.file;
        boolean ok = f.delete();
        File edit = new File(f.getParentFile(), f.getName() + ".edit.json");
        if (edit.exists()) //noinspection ResultOfMethodCallIgnored
            edit.delete();
        status = ok ? "Enregistrement supprimé." : "Impossible de supprimer " + f.getName();
        confirmDelete = null;
        selected = null;
        reload();
    }

    private void applyRename() {
        if (selected == null) return;
        String name = RedoArchive.sanitize(rename.text());
        File target = new File(selected.file.getParentFile(), name + RedoArchive.EXTENSION);
        if (target.exists() && !target.equals(selected.file)) {
            status = "Un enregistrement porte déjà ce nom.";
            return;
        }
        File oldEdit = new File(selected.file.getParentFile(), selected.file.getName() + ".edit.json");
        if (selected.file.renameTo(target)) {
            if (oldEdit.exists()) //noinspection ResultOfMethodCallIgnored
                oldEdit.renameTo(new File(target.getParentFile(), target.getName() + ".edit.json"));
            selected = new Entry(target);
            status = "Renommé.";
        } else {
            status = "Impossible de renommer.";
        }
        renaming = false;
        rename.focusChanged(false);
        reload();
    }

    // ------------------------------------------------------------------ View

    @Override
    public void render(Gfx g, int mx, int my, double dtMs) {
        layout(g);
        g.fill(0, 0, W, H, Theme.BG);
        g.text("Redo", listX, 12, Theme.ACCENT, true);
        g.text("Enregistrements", listX + g.textWidth("Redo") + 8, 12, Theme.TEXT, false);
        String right = entries.size() + (entries.size() > 1 ? " parties" : " partie");
        g.text(right, listX + listW - g.textWidth(right), 12, Theme.TEXT_DIM, false);
        g.text(Widget.fit(g, Redo.recordingsDir().getAbsolutePath(), listW), listX, 26, Theme.TEXT_DIM, false);

        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, Theme.BORDER);
        g.fill(listX, listY, listX + listW, listY + listH, Theme.PANEL);
        int maxScroll = Math.max(0, entries.size() * ROW - listH);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        g.pushClip(listX, listY, listX + listW, listY + listH);
        if (entries.isEmpty()) {
            Widget.centered(g, "Aucun enregistrement pour l'instant.", listX + listW / 2, listY + listH / 2 - 10, Theme.TEXT);
            Widget.centered(g, "Rejoignez un serveur : Redo enregistre la partie tout seul.", listX + listW / 2, listY + listH / 2 + 4, Theme.TEXT_DIM);
        }
        SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        for (int i = 0; i < entries.size(); i++) {
            int y = listY + i * ROW - scroll;
            if (y + ROW < listY || y > listY + listH) continue;
            Entry e = entries.get(i);
            boolean sel = e == selected;
            boolean hover = mx >= listX && mx < listX + listW && my >= y && my < y + ROW && my >= listY && my < listY + listH;
            if (sel) g.fill(listX, y, listX + listW, y + ROW, Theme.BUTTON_ON);
            else if (hover) g.fill(listX, y, listX + listW, y + ROW, Theme.BUTTON_HOVER);
            String name = e.file.getName().replace(RedoArchive.EXTENSION, "");
            g.text(Widget.fit(g, name, listW - 120), listX + 6, y + 4, Theme.TEXT, false);
            String sub;
            if (e.archive == null) {
                sub = "Illisible : " + e.error;
            } else {
                StringBuilder b = new StringBuilder();
                b.append(df.format(new Date(e.archive.meta.startedAt)));
                if (!e.archive.meta.serverName.isEmpty()) b.append(" · ").append(e.archive.meta.serverName);
                b.append(" · ").append(e.archive.meta.minecraftVersion);
                sub = b.toString();
            }
            int subColor = canOpen(e) ? Theme.TEXT_DIM : Theme.RED;
            g.text(Widget.fit(g, sub, listW - 120), listX + 6, y + 15, subColor, false);
            String dur = e.archive == null ? "" : Director.formatTime(e.archive.meta.durationMs - Math.max(0, e.archive.meta.clipStartMs));
            String size = String.format("%.1f Mo", e.file.length() / (1024.0 * 1024.0));
            g.text(dur, listX + listW - 6 - g.textWidth(dur), y + 4, Theme.TEXT, false);
            g.text(size, listX + listW - 6 - g.textWidth(size), y + 15, Theme.TEXT_DIM, false);
        }
        g.popClip();

        for (Widget w : widgets) w.render(g, mx, my);
        if (renaming) {
            g.text("Nouveau nom (Entrée pour valider, Échap pour annuler) :", listX, rename.y - 11, Theme.TEXT_DIM, false);
            rename.render(g, mx, my);
        } else if (!status.isEmpty()) {
            g.text(Widget.fit(g, status, listW), listX, H - 48, Theme.ACCENT, false);
        }
        for (Widget w : widgets) {
            if (w.tooltip != null && w.contains(mx, my)) {
                int tw = g.textWidth(w.tooltip);
                int tx = Math.min(mx + 8, W - tw - 6);
                g.fill(tx - 3, my - 14, tx + tw + 3, my - 2, 0xF0000000);
                g.text(w.tooltip, tx, my - 12, Theme.TEXT, false);
            }
        }
    }

    @Override
    public void setModifiers(int mods) {
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (renaming && rename.contains(mx, my)) {
            rename.mouseDown(mx, my, button);
            return true;
        }
        for (Widget w : widgets) {
            if (w.visible && w.contains(mx, my)) {
                pressed = w;
                w.mouseDown(mx, my, button);
                return true;
            }
        }
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int i = (int) ((my - listY + scroll) / ROW);
            if (i >= 0 && i < entries.size()) {
                Entry e = entries.get(i);
                long now = System.currentTimeMillis();
                if (e == lastClicked && now - lastClick < 350) open(e);
                if (selected != e) confirmDelete = null;
                selected = e;
                lastClicked = e;
                lastClick = now;
                renaming = false;
                status = "";
                updateButtons();
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseDrag(double x, double y, int button, double dx, double dy) {
        if (pressed != null) pressed.mouseDrag(x, y, button);
    }

    @Override
    public void mouseUp(double x, double y, int button) {
        if (pressed != null) pressed.mouseUp(x, y, button);
        pressed = null;
    }

    @Override
    public boolean scroll(double x, double y, double amount) {
        scroll -= (int) (amount * ROW);
        return true;
    }

    @Override
    public boolean key(int key, int mods) {
        if (renaming) {
            if (key == Keys.ESCAPE) {
                renaming = false;
                rename.focusChanged(false);
                return true;
            }
            if (Keys.ctrl(mods) && key == Keys.V) {
                rename.paste(host.clipboard());
                return true;
            }
            rename.key(key, mods);
            return true;
        }
        int i = selected == null ? -1 : entries.indexOf(selected);
        switch (key) {
            case Keys.ESCAPE: host.close(); return true;
            case Keys.ENTER:
            case Keys.KP_ENTER: open(selected); return true;
            case Keys.DELETE: delete(); return true;
            case Keys.UP:
                if (i > 0) selected = entries.get(i - 1);
                updateButtons();
                return true;
            case Keys.DOWN:
                if (i + 1 < entries.size()) selected = entries.get(i + 1);
                updateButtons();
                return true;
            default: return false;
        }
    }

    @Override
    public boolean chr(char c) {
        return renaming && rename.chr(c);
    }

    @Override
    public boolean typing() {
        return renaming;
    }

    @Override
    public boolean showsWorld() {
        return false;
    }

    @Override
    public void closed() {
    }
}
