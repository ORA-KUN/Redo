package fr.orakun.redo.core.editor;

import fr.orakun.redo.core.Redo;
import fr.orakun.redo.core.RedoConfig;
import fr.orakun.redo.core.director.Director;
import fr.orakun.redo.core.director.ShotSuggestion;
import fr.orakun.redo.core.director.ShotType;
import fr.orakun.redo.core.director.TrailerBuilder;
import fr.orakun.redo.core.export.ExportFormat;
import fr.orakun.redo.core.export.ExportJob;
import fr.orakun.redo.core.export.FfmpegDownloader;
import fr.orakun.redo.core.timeline.Clip;
import fr.orakun.redo.core.timeline.ExportSettings;
import fr.orakun.redo.core.ui.Button;
import fr.orakun.redo.core.ui.Cycle;
import fr.orakun.redo.core.ui.Gfx;
import fr.orakun.redo.core.ui.Keys;
import fr.orakun.redo.core.ui.Slider;
import fr.orakun.redo.core.ui.TextField;
import fr.orakun.redo.core.ui.Theme;
import fr.orakun.redo.core.ui.View;
import fr.orakun.redo.core.ui.Widget;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The montage screen: bin of shots on the left, preview in the middle, inspector on the right,
 * prompt bar and timeline at the bottom. Platform screens forward drawing and input here.
 */
public final class EditorView implements View {
    private static final String[] FILTERS = {"Tous", "Auto", "Prompt", "Libres", "★"};
    private static final String[] FILTER_TIPS = {"Tous les plans", "Plans trouvés par le réalisateur automatique",
            "Plans créés avec le prompt", "Plans faits en caméra libre", "Plans favoris (☆ sur un plan)"};
    private static final int[][] RESOLUTIONS = {{1280, 720}, {1920, 1080}, {2560, 1440}, {3840, 2160}, {1080, 1920}};
    private static final Integer[] FPS = {24, 30, 60};

    private enum Panel { NONE, EXPORT, SETTINGS, HELP }

    private final EditorSession s;
    private final EditorHost host;
    private final TimelineView timeline;
    private final TextField prompt = new TextField();
    private final List<Widget> widgets = new ArrayList<Widget>();
    private final List<Widget> panelWidgets = new ArrayList<Widget>();
    private Widget focus;
    private Widget pressed;
    private Panel panel = Panel.NONE;
    private int filter;
    private int binScroll;
    private long lastClickAt;
    private ShotSuggestion lastClickShot;
    private int mods;
    private int W, H;
    private FfmpegDownloader downloader;
    private Clip inspected;
    private int lastLayoutW = -1, lastLayoutH = -1;

    // layout
    private int topH, binW, inspW, vpX, vpY, vpW, vpH, ctrlY, promptY, tlY;
    private final int[] tabX0 = new int[FILTERS.length], tabX1 = new int[FILTERS.length];
    private Gfx measure;
    private Button previewBtn, addBtn;
    private String tipText;
    private long tipSince;

    // inspector widgets (rebuilt when the selection changes)
    private final List<Widget> inspector = new ArrayList<Widget>();
    private final List<Widget> freeWidgets = new ArrayList<Widget>();
    private EditorSession.Mode lastMode = EditorSession.Mode.MONTAGE;
    private long modeSince;
    private boolean lookDrag;

    public EditorView(EditorSession s, EditorHost host) {
        this.s = s;
        this.host = host;
        this.timeline = new TimelineView(s);
        prompt.placeholder = "Décris un plan… ex : traveling lent autour de l'arbre au centre du hub";
        prompt.onSubmit = new TextField.Listener() {
            @Override
            public void submit(String text) {
                runPrompt();
            }
        };
    }

    private void runPrompt() {
        String t = prompt.text().trim();
        if (t.isEmpty()) return;
        s.runPrompt(t);
        prompt.setText("");
    }

    // ------------------------------------------------------------------ layout

    private void layout(Gfx g) {
        W = g.width();
        H = g.height();
        if (W == lastLayoutW && H == lastLayoutH) return;
        lastLayoutW = W;
        lastLayoutH = H;
        topH = 18;
        binW = clamp(W * 25 / 100, 160, 200);
        inspW = clamp(W * 20 / 100, 118, 170);
        int tlH = Math.max(70, H * 24 / 100);
        tlY = H - tlH;
        promptY = tlY - 20;
        ctrlY = promptY - 16;
        vpX = binW;
        vpY = topH;
        vpW = W - binW - inspW;
        vpH = ctrlY - topH;
        timeline.bounds(0, tlY + 14, W, tlH - 14);
        prompt.bounds(binW + 4, promptY + 3, vpW - 70, 14);
        measure = g;
        buildWidgets();
        buildFreeWidgets();
        inspected = null;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private int textW(String s) {
        return measure != null ? measure.textWidth(s) : s.length() * 6;
    }

    private void buildWidgets() {
        widgets.clear();
        // top bar
        int x = W - 4;
        x = addRight(widgets, x, 2, "Quitter", new Runnable() {
            @Override
            public void run() {
                s.close();
                host.exitReplay();
            }
        });
        Button export = addRightButton(widgets, x, 2, "Exporter", new Runnable() {
            @Override
            public void run() {
                openPanel(Panel.EXPORT);
            }
        }, 14);
        export.accent();
        x = export.x - 3;
        x = addRight(widgets, x, 2, "Réglages", new Runnable() {
            @Override
            public void run() {
                openPanel(Panel.SETTINGS);
            }
        });
        x = addRight(widgets, x, 2, "?", new Runnable() {
            @Override
            public void run() {
                openPanel(Panel.HELP);
            }
        });
        int tx = 120;
        Button montage = new Button("Montage", null);
        montage.on = true;
        montage.bounds(tx, 2, 56, 14);
        widgets.add(montage);
        widgets.add(new Button("Caméra libre", new Runnable() {
            @Override
            public void run() {
                s.setMode(EditorSession.Mode.FREE_CAMERA);
            }
        }).tip("Voler librement et placer des images clés (Tab)").bounds(tx + 58, 2, 72, 14));
        widgets.add(new Button("Plein écran", new Runnable() {
            @Override
            public void run() {
                s.setMode(EditorSession.Mode.FULLSCREEN);
            }
        }).tip("Lire le montage en plein écran (F)").bounds(tx + 132, 2, 64, 14));

        // playback controls
        int cx = vpX + 4, cy = ctrlY + 1;
        widgets.add(new Button("|◀", new Runnable() {
            @Override
            public void run() {
                s.setPlayhead(0);
            }
        }).tip("Début (Début)").bounds(cx, cy, 18, 14));
        widgets.add(new Button("◀", new Runnable() {
            @Override
            public void run() {
                s.step(-1000);
            }
        }).tip("Reculer d'une seconde (←)").bounds(cx + 20, cy, 18, 14));
        widgets.add(new Button("▶", new Runnable() {
            @Override
            public void run() {
                s.togglePlay();
            }
        }) {
            @Override
            public void render(Gfx g, int mx, int my) {
                label = s.playing ? "❚❚" : "▶";
                super.render(g, mx, my);
            }
        }.tip("Lecture / pause (Espace)").bounds(cx + 40, cy, 24, 14));
        widgets.add(new Button("▶", new Runnable() {
            @Override
            public void run() {
                s.step(1000);
            }
        }).tip("Avancer d'une seconde (→)").bounds(cx + 66, cy, 18, 14));
        widgets.add(new Button("▶|", new Runnable() {
            @Override
            public void run() {
                s.setPlayhead(s.sequence.duration());
            }
        }).tip("Fin (Fin)").bounds(cx + 86, cy, 18, 14));

        // prompt
        widgets.add(prompt);
        widgets.add(new Button("Générer", new Runnable() {
            @Override
            public void run() {
                runPrompt();
            }
        }).accent().tip(Redo.config().claudeApiKey.isEmpty() ? "Mode hors ligne (mots-clés). Ajoutez une clé Claude dans Réglages pour comprendre toutes les demandes."
                : "Envoie la demande à Claude").bounds(binW + vpW - 62, promptY + 3, 58, 14));

        // timeline toolbar
        int bx = 4, by = tlY + 1;
        bx = addLeft(widgets, bx, by, "Couper (S)", new Runnable() {
            @Override
            public void run() {
                s.splitAtPlayhead();
            }
        });
        bx = addLeft(widgets, bx, by, "Supprimer", new Runnable() {
            @Override
            public void run() {
                s.deleteSelected(true);
            }
        });
        bx = addLeft(widgets, bx, by, "Fermer les trous", new Runnable() {
            @Override
            public void run() {
                s.closeGaps();
            }
        });
        bx = addLeft(widgets, bx, by, "Annuler", new Runnable() {
            @Override
            public void run() {
                s.undo();
            }
        });
        bx = addLeft(widgets, bx, by, "Rétablir", new Runnable() {
            @Override
            public void run() {
                s.redo();
            }
        });
        final Button snap = new Button("Aimant", null);
        snap.on = timeline.snap;
        snap.action = new Runnable() {
            @Override
            public void run() {
                timeline.snap = !timeline.snap;
                snap.on = timeline.snap;
            }
        };
        snap.tip("Coller aux bords des plans (Maj pour ignorer)").bounds(bx, by, 44, 12);
        widgets.add(snap);
        bx += 46;
        int zx = W - 4;
        zx = addRight(widgets, zx, by, "Ajuster", new Runnable() {
            @Override
            public void run() {
                timeline.fit();
            }
        }, 12).x - 2;
        zx = addRight(widgets, zx, by, "+", new Runnable() {
            @Override
            public void run() {
                timeline.zoom(1.5, timeline.x + timeline.w / 2.0);
            }
        }, 12).x - 2;
        addRight(widgets, zx, by, "−", new Runnable() {
            @Override
            public void run() {
                timeline.zoom(1 / 1.5, timeline.x + timeline.w / 2.0);
            }
        }, 12);
        widgets.add(timeline);

        // bin buttons: the chosen shot, then the automatic trailer
        int bby = tlY - 50;
        int half = (binW - 10) / 2;
        previewBtn = new Button("Aperçu", new Runnable() {
            @Override
            public void run() {
                if (s.selectedShot != null) s.previewShot(s.selectedShot);
            }
        }).tip("Lire le plan choisi seul dans l'aperçu (clic sur le plan)");
        previewBtn.bounds(4, bby, half, 14);
        widgets.add(previewBtn);
        addBtn = new Button("Ajouter ▸", new Runnable() {
            @Override
            public void run() {
                if (s.selectedShot != null) {
                    s.insertShot(s.selectedShot);
                    timeline.follow();
                }
            }
        }).tip("Ajouter le plan choisi à la tête de lecture (double-clic sur le plan)");
        addBtn.bounds(6 + half, bby, half, 14);
        widgets.add(addBtn);
        final Cycle<TrailerBuilder.Style> style = new Cycle<TrailerBuilder.Style>("Style", TrailerBuilder.Style.values(), s.trailerStyle,
                new Cycle.Namer<TrailerBuilder.Style>() {
                    @Override
                    public String name(TrailerBuilder.Style v) {
                        return v.label;
                    }
                }, new Cycle.Listener<TrailerBuilder.Style>() {
            @Override
            public void changed(TrailerBuilder.Style v) {
                s.trailerStyle = v;
            }
        });
        style.tip("Style de la bande-annonce (clic : suivant, clic droit : précédent)").bounds(4, bby + 16, binW - 8, 14);
        widgets.add(style);
        widgets.add(new Button("Créer la bande-annonce", new Runnable() {
            @Override
            public void run() {
                s.buildTrailer(s.trailerStyle);
                timeline.fit();
            }
        }).accent().tip("Remplace le montage par une bande-annonce automatique (Ctrl+Z pour revenir)").bounds(4, bby + 32, binW - 8, 14));
        int rw = textW("Réanalyser") + 10;
        widgets.add(new Button("Réanalyser", new Runnable() {
            @Override
            public void run() {
                s.analyse();
            }
        }).tip("Relance le réalisateur automatique (garde les favoris et vos plans)").bounds(binW - 4 - rw, topH + 2, rw, 12));
    }

    private Button addRightButton(List<Widget> list, int right, int y, String label, Runnable r, int h) {
        Button b = new Button(label, r);
        int w = Math.max(18, textW(label) + 10);
        b.bounds(right - w, y, w, h);
        list.add(b);
        return b;
    }

    private int addRight(List<Widget> list, int right, int y, String label, Runnable r) {
        return addRightButton(list, right, y, label, r, 14).x - 3;
    }

    private Button addRight(List<Widget> list, int right, int y, String label, Runnable r, int h) {
        return addRightButton(list, right, y, label, r, h);
    }

    private int addLeft(List<Widget> list, int left, int y, String label, Runnable r) {
        Button b = new Button(label, r);
        int w = label.length() * 6 + 6;
        b.bounds(left, y, w, 12);
        list.add(b);
        return left + w + 2;
    }

    // ------------------------------------------------------------------ inspector

    private void buildInspector() {
        inspector.clear();
        final Clip c = s.selected;
        inspected = c;
        int x = W - inspW + 4, w = inspW - 8;
        int y = topH + 30;
        if (c == null) {
            inspector.add(new Button("Nouveau montage", new Runnable() {
                @Override
                public void run() {
                    s.newSequence();
                }
            }).bounds(x, y + 40, w, 14));
            if (s.edit.sequences.size() > 1) {
                Integer[] idx = new Integer[s.edit.sequences.size()];
                for (int i = 0; i < idx.length; i++) idx[i] = i;
                inspector.add(new Cycle<Integer>("Montage", idx, s.edit.active, new Cycle.Namer<Integer>() {
                    @Override
                    public String name(Integer v) {
                        return s.edit.sequences.get(v).name;
                    }
                }, new Cycle.Listener<Integer>() {
                    @Override
                    public void changed(Integer v) {
                        s.switchSequence(v);
                        timeline.fit();
                        inspected = null;
                    }
                }).bounds(x, y + 56, w, 14));
            }
            return;
        }
        ShotType[] types = ShotType.values();
        ShotType current = ShotType.byName(c.shotType);
        if (current != null) {
            inspector.add(new Cycle<ShotType>("", types, current, new Cycle.Namer<ShotType>() {
                @Override
                public String name(ShotType v) {
                    return v.label;
                }
            }, new Cycle.Listener<ShotType>() {
                @Override
                public void changed(ShotType v) {
                    s.changeType(s.selected, v);
                    inspected = null;
                }
            }).tip("Type de plan : le plan est reconstruit").bounds(x, y, w, 14));
            inspector.add(new Button("Variante", new Runnable() {
                @Override
                public void run() {
                    s.variant(s.selected);
                    inspected = null;
                }
            }).tip("Même type de plan, autre placement de caméra").bounds(x, y + 16, w, 14));
        }
        inspector.add(new Slider("Durée", 0.5, 30, 0.1, c.durationMs / 1000.0, new Slider.Listener() {
            boolean started;

            @Override
            public void changed(double v, boolean released) {
                if (!started) {
                    s.beginGesture();
                    started = true;
                }
                s.trimEnd(c, (long) (v * 1000));
                if (released) started = false;
            }
        }).unit(" s", 1).bounds(x, y + 34, w, 14));
        inspector.add(new Slider("Vitesse", 0.1, 4, 0.05, c.speed, new Slider.Listener() {
            @Override
            public void changed(double v, boolean released) {
                if (released) s.setSpeed(c, v);
                else c.speed = v;
            }
        }).unit("x", 2).bounds(x, y + 50, w, 14));
        inspector.add(new Cycle<Clip.Transition>("Entrée", Clip.Transition.values(), c.transitionIn, new Cycle.Namer<Clip.Transition>() {
            @Override
            public String name(Clip.Transition v) {
                return v == Clip.Transition.CUT ? "Coupe" : "Fondu";
            }
        }, new Cycle.Listener<Clip.Transition>() {
            @Override
            public void changed(Clip.Transition v) {
                s.setTransition(c, v);
            }
        }).bounds(x, y + 66, w, 14));
        inspector.add(new Slider("Fondu", 0.1, 3, 0.1, c.transitionMs / 1000.0, new Slider.Listener() {
            @Override
            public void changed(double v, boolean released) {
                s.setTransitionMs(c, (long) (v * 1000));
            }
        }).unit(" s", 1).bounds(x, y + 82, w, 14));
        inspector.add(new Button("Couper à la tête", new Runnable() {
            @Override
            public void run() {
                s.splitAtPlayhead();
                inspected = null;
            }
        }).bounds(x, y + 102, w, 14));
        inspector.add(new Button("Supprimer le plan", new Runnable() {
            @Override
            public void run() {
                s.deleteSelected(true);
                inspected = null;
            }
        }).bounds(x, y + 118, w, 14));
    }

    // ------------------------------------------------------------------ panels

    private void openPanel(Panel p) {
        panel = p;
        panelWidgets.clear();
        setFocus(null);
        int pw = Math.min(300, W - 40), ph = Math.min(220, H - 40);
        int px = (W - pw) / 2, py = (H - ph) / 2;
        int x = px + 10, w = pw - 20, y = py + 26;
        if (p == Panel.EXPORT) {
            final ExportSettings e = s.sequence.export;
            panelWidgets.add(new Cycle<ExportFormat>("Format", ExportFormat.values(), e.format, new Cycle.Namer<ExportFormat>() {
                @Override
                public String name(ExportFormat v) {
                    return v.label;
                }
            }, new Cycle.Listener<ExportFormat>() {
                @Override
                public void changed(ExportFormat v) {
                    e.format = v;
                    s.markDirty();
                }
            }).bounds(x, y, w, 14));
            Integer[] res = new Integer[RESOLUTIONS.length];
            int cur = 1;
            for (int i = 0; i < res.length; i++) {
                res[i] = i;
                if (RESOLUTIONS[i][0] == e.width && RESOLUTIONS[i][1] == e.height) cur = i;
            }
            panelWidgets.add(new Cycle<Integer>("Taille", res, cur, new Cycle.Namer<Integer>() {
                @Override
                public String name(Integer v) {
                    int[] r = RESOLUTIONS[v];
                    return r[0] + "×" + r[1] + (r[1] > r[0] ? " (vertical)" : "");
                }
            }, new Cycle.Listener<Integer>() {
                @Override
                public void changed(Integer v) {
                    e.width = RESOLUTIONS[v][0];
                    e.height = RESOLUTIONS[v][1];
                    s.markDirty();
                }
            }).bounds(x, y + 16, w, 14));
            panelWidgets.add(new Cycle<Integer>("Images/s", FPS, e.fps, new Cycle.Namer<Integer>() {
                @Override
                public String name(Integer v) {
                    return String.valueOf(v);
                }
            }, new Cycle.Listener<Integer>() {
                @Override
                public void changed(Integer v) {
                    e.fps = v;
                    s.markDirty();
                }
            }).bounds(x, y + 32, w, 14));
            panelWidgets.add(new Slider("Qualité (CRF, bas = mieux)", 12, 32, 1, e.crf, new Slider.Listener() {
                @Override
                public void changed(double v, boolean released) {
                    e.crf = (int) v;
                    s.markDirty();
                }
            }).unit("", 0).bounds(x, y + 48, w, 14));
            panelWidgets.add(new Button("Ouvrir le dossier des vidéos", new Runnable() {
                @Override
                public void run() {
                    host.openFolder(Redo.exportDir());
                }
            }).bounds(x, y + 70, w, 14));
            if (s.ffmpeg() == null && FfmpegDownloader.supported()) {
                panelWidgets.add(new Button("Télécharger ffmpeg (≈ 80 Mo)", new Runnable() {
                    @Override
                    public void run() {
                        if (downloader == null || downloader.isDone()) {
                            downloader = new FfmpegDownloader(Redo.home());
                            downloader.start();
                        }
                    }
                }).bounds(x, y + 88, w, 14));
            }
            panelWidgets.add(new Button("Lancer l'export", new Runnable() {
                @Override
                public void run() {
                    if (s.startExport(host.framebufferWidth(), host.framebufferHeight(), host.framesBottomUp())) panel = Panel.NONE;
                }
            }).accent().bounds(x, py + ph - 24, w / 2 - 2, 16));
            panelWidgets.add(new Button("Fermer", new Runnable() {
                @Override
                public void run() {
                    panel = Panel.NONE;
                }
            }).bounds(x + w / 2 + 2, py + ph - 24, w / 2 - 2, 16));
        } else if (p == Panel.SETTINGS) {
            final RedoConfig c = Redo.config();
            final TextField key = new TextField();
            key.password = true;
            key.placeholder = "Clé API Claude (sk-ant-…), facultative";
            key.setText(c.claudeApiKey);
            key.bounds(x, y + 10, w, 14);
            panelWidgets.add(key);
            final TextField model = new TextField();
            model.placeholder = "Modèle Claude";
            model.setText(c.claudeModel);
            model.bounds(x, y + 38, w, 14);
            panelWidgets.add(model);
            final Button auto = new Button("", null);
            auto.action = new Runnable() {
                @Override
                public void run() {
                    c.autoRecord = !c.autoRecord;
                    auto.label = "Enregistrer dès la connexion : " + (c.autoRecord ? "oui" : "non");
                }
            };
            auto.label = "Enregistrer dès la connexion : " + (c.autoRecord ? "oui" : "non");
            auto.bounds(x, y + 58, w, 14);
            panelWidgets.add(auto);
            // size of the Redo screens, applied with "Enregistrer et fermer"
            final Cycle<Integer> size = new Cycle<Integer>("Taille de l'interface", new Integer[]{0, 1, 2, 3, 4, 5, 6}, c.uiScale,
                    new Cycle.Namer<Integer>() {
                        @Override
                        public String name(Integer v) {
                            return v == 0 ? "auto" : "×" + v;
                        }
                    }, null);
            size.bounds(x, y + 74, w, 14);
            panelWidgets.add(size);
            panelWidgets.add(new Slider("Vitesse de la caméra libre", 2, 60, 1, c.flySpeed, new Slider.Listener() {
                @Override
                public void changed(double v, boolean released) {
                    c.flySpeed = v;
                }
            }).unit(" blocs/s", 0).bounds(x, y + 90, w, 14));
            panelWidgets.add(new Button("Ouvrir le dossier des enregistrements", new Runnable() {
                @Override
                public void run() {
                    host.openFolder(Redo.recordingsDir());
                }
            }).bounds(x, y + 110, w, 14));
            panelWidgets.add(new Button("Enregistrer et fermer", new Runnable() {
                @Override
                public void run() {
                    c.claudeApiKey = key.text().trim();
                    c.claudeModel = model.text().trim().isEmpty() ? fr.orakun.redo.core.prompt.ClaudeClient.DEFAULT_MODEL : model.text().trim();
                    c.uiScale = size.value();
                    c.save();
                    panel = Panel.NONE;
                    lastLayoutW = -1;
                }
            }).accent().bounds(x, py + ph - 24, w, 16));
        } else if (p == Panel.HELP) {
            panelWidgets.add(new Button("Fermer", new Runnable() {
                @Override
                public void run() {
                    panel = Panel.NONE;
                }
            }).bounds(x, py + ph - 24, w, 16));
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(Gfx g, int mx, int my, double dtMs) {
        if (s.mode == EditorSession.Mode.FREE_CAMERA && !typing()) host.movement(s.move);
        else Arrays.fill(s.move, 0);
        s.frame(dtMs);
        if (s.mode != lastMode) {
            lastMode = s.mode;
            modeSince = System.currentTimeMillis();
            lookDrag = false;
        }
        layout(g);
        if (s.export == null || s.export.state() == ExportJob.State.CANCELLED) {
            if (s.mode == EditorSession.Mode.FREE_CAMERA) {
                renderFreeCamera(g, mx, my);
                return;
            }
            if (s.mode == EditorSession.Mode.FULLSCREEN) {
                renderFullscreen(g);
                return;
            }
        }
        renderMontage(g, mx, my);
    }

    @Override
    public boolean showsWorld() {
        return true;
    }

    @Override
    public void closed() {
        s.save();
    }

    /** Where the world shows between the panels, in interface pixels {x, y, w, h}. */
    public int[] viewport() {
        return new int[]{vpX, vpY, vpW, vpH};
    }

    private void renderFullscreen(Gfx g) {
        long since = System.currentTimeMillis() - modeSince;
        if (since < 2500) {
            String hint = "Plein écran : Échap pour revenir, Espace pour lecture/pause";
            int tw = g.textWidth(hint);
            g.fill(W / 2 - tw / 2 - 6, 8, W / 2 + tw / 2 + 6, 22, 0x99000000);
            Widget.centered(g, hint, W / 2, 11, Theme.TEXT);
        }
    }

    private void renderFreeCamera(Gfx g, int mx, int my) {
        g.fill(0, 0, W, 18, 0xA0000000);
        g.text("Caméra libre", 5, 5, Theme.ACCENT, true);
        String info = time(s.backend.timeMs()) + " / " + time(s.backend.durationMs())
                + "   " + (s.freePlaying ? "Lecture" : "Pause") + " x" + fmtSpeed(s.freeSpeed())
                + "   Vol " + fmtSpeed(Redo.config().flySpeed) + " blocs/s"
                + "   Images clés : " + s.pendingKeys.size();
        int infoX = 5 + g.textWidth("Caméra libre") + 12;
        int right = freeWidgets.isEmpty() ? W : freeWidgets.get(freeWidgets.size() - 1).x - 6;
        g.text(Widget.fit(g, info, right - infoX), infoX, 5, Theme.TEXT, false);
        for (Widget w : freeWidgets) w.render(g, mx, my);
        // crosshair
        int cx = W / 2, cy = H / 2;
        g.fill(cx - 4, cy, cx + 5, cy + 1, 0x90FFFFFF);
        g.fill(cx, cy - 4, cx + 1, cy + 5, 0x90FFFFFF);
        // key help, on as many lines as the width needs
        String[] hints = {"Clic + souris : viser", "Touches de déplacement : voler", "Molette : vitesse de vol", "Ctrl+molette : zoom",
                "Alt+molette : inclinaison", "R : objectif par défaut", "K : image clé", "Entrée : créer le plan",
                "Retour arrière : effacer les images", "P : lecture/pause", "← → : 5 s", "↑ ↓ : vitesse du replay", "Tab/Échap : montage"};
        List<String> lines = new ArrayList<String>();
        String line = "";
        for (String hnt : hints) {
            String next = line.isEmpty() ? hnt : line + "   " + hnt;
            if (!line.isEmpty() && g.textWidth(next) > W - 10) {
                lines.add(line);
                line = hnt;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) lines.add(line);
        int hy = H - lines.size() * 11 - 4;
        g.fill(0, hy, W, H, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) g.text(Widget.fit(g, lines.get(i), W - 10), 5, hy + 3 + i * 11, Theme.TEXT_DIM, false);
        renderToasts(g);
    }

    private static String fmtSpeed(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-6) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, v < 1 ? "%.2f" : "%.1f", v);
    }

    private void buildFreeWidgets() {
        freeWidgets.clear();
        int x = W - 4;
        x = addRight(freeWidgets, x, 2, "Montage", new Runnable() {
            @Override
            public void run() {
                s.setMode(EditorSession.Mode.MONTAGE);
            }
        });
        Button make = addRightButton(freeWidgets, x, 2, "Créer le plan", new Runnable() {
            @Override
            public void run() {
                finishShot();
            }
        }, 14);
        make.accent();
        x = make.x - 3;
        x = addRight(freeWidgets, x, 2, "Effacer", new Runnable() {
            @Override
            public void run() {
                s.clearKeyframes();
            }
        });
        addRight(freeWidgets, x, 2, "Image clé", new Runnable() {
            @Override
            public void run() {
                s.addKeyframe();
            }
        });
        // keep them ordered right to left so the info text stops before the leftmost one
        java.util.Collections.sort(freeWidgets, new java.util.Comparator<Widget>() {
            @Override
            public int compare(Widget a, Widget b) {
                return b.x - a.x;
            }
        });
    }

    private void finishShot() {
        ShotSuggestion shot = s.finishManualShot();
        if (shot != null) {
            s.selectedShot = shot;
            filter = 0;
            s.setMode(EditorSession.Mode.MONTAGE);
        }
    }

    private boolean freeCameraKey(int key, int mods) {
        switch (key) {
            case Keys.TAB:
            case Keys.ESCAPE: s.setMode(EditorSession.Mode.MONTAGE); return true;
            case Keys.K: s.addKeyframe(); return true;
            case Keys.ENTER:
            case Keys.KP_ENTER: finishShot(); return true;
            case Keys.BACKSPACE:
            case Keys.DELETE:
                s.clearKeyframes();
                s.toast("Images clés effacées.", false);
                return true;
            case Keys.P: s.freePlaying = !s.freePlaying; return true;
            case Keys.LEFT: s.freeSeek(-5000); return true;
            case Keys.RIGHT: s.freeSeek(5000); return true;
            case Keys.UP: s.stepFreeSpeed(1); return true;
            case Keys.DOWN: s.stepFreeSpeed(-1); return true;
            case Keys.R: s.freeCam.resetLens(); return true;
            default: return true;
        }
    }

    private void renderMontage(Gfx g, int mx, int my) {
        if (s.export != null && s.export.state() != ExportJob.State.CANCELLED) {
            renderExport(g, mx, my);
            return;
        }
        if (s.playing && s.previewShot == null) timeline.follow();
        if (inspected != s.selected || (s.selected != null && !s.sequence.clips.contains(s.selected))) {
            if (s.selected != null && !s.sequence.clips.contains(s.selected)) s.selected = null;
            buildInspector();
        }
        int hoverX = panel == Panel.NONE ? mx : -1, hoverY = panel == Panel.NONE ? my : -1;

        // panels
        g.fill(0, 0, W, topH, Theme.HEADER);
        g.text("Redo", 5, 5, Theme.ACCENT, true);
        g.text(Widget.fit(g, s.archive.displayName(), 80), 32, 5, Theme.TEXT_DIM, false);
        g.fill(0, topH, binW, tlY, Theme.PANEL);
        g.fill(W - inspW, topH, W, tlY, Theme.PANEL);
        g.fill(binW, ctrlY, W - inspW, tlY, Theme.PANEL);
        g.fill(0, tlY, W, tlY + 14, Theme.HEADER);
        Widget.frame(g, vpX - 1, vpY - 1, vpX + vpW + 1, vpY + vpH + 1, Theme.BORDER);

        renderBin(g, hoverX, hoverY);
        renderInspector(g, hoverX, hoverY);
        renderViewportOverlay(g);

        // timecode
        String tc = s.previewShot != null
                ? "Aperçu " + time(s.previewTime()) + " / " + time(s.previewShot.clip.durationMs)
                : time(s.playheadMs) + " / " + time(s.sequence.duration());
        g.text(tc, vpX + 112, ctrlY + 4, Theme.TEXT, false);
        if (s.previewShot != null) {
            String back = "Clic sur la timeline pour revenir au montage";
            g.text(Widget.fit(g, back, vpW - 220), vpX + vpW - 4 - Math.min(g.textWidth(back), vpW - 220), ctrlY + 4, Theme.TEXT_DIM, false);
        }

        previewBtn.enabled = addBtn.enabled = s.selectedShot != null;
        for (Widget w : widgets) w.render(g, hoverX, hoverY);
        for (Widget w : inspector) w.render(g, hoverX, hoverY);

        renderToasts(g);
        if (panel != Panel.NONE) renderPanel(g, mx, my);
        else renderTooltip(g, mx, my);
    }

    private static String time(double ms) {
        long t = (long) ms;
        return Director.formatTime(t) + "." + (t % 1000) / 100;
    }

    private List<ShotSuggestion> filtered() {
        List<ShotSuggestion> out = new ArrayList<ShotSuggestion>();
        for (ShotSuggestion sh : s.edit.suggestions) {
            switch (filter) {
                case 1: if (!"auto".equals(sh.origin)) continue; break;
                case 2: if (!"prompt".equals(sh.origin)) continue; break;
                case 3: if (!"manual".equals(sh.origin)) continue; break;
                case 4: if (!sh.favorite) continue; break;
                default: break;
            }
            out.add(sh);
        }
        return out;
    }

    private int binTop() {
        return topH + 30;
    }

    private int binBottom() {
        return tlY - 52;
    }

    private static final int ROW = 24;

    private void renderBin(Gfx g, int mx, int my) {
        List<ShotSuggestion> list = filtered();
        g.text("Chutier", 5, topH + 4, Theme.TEXT, false);
        g.text(String.valueOf(list.size()), 9 + g.textWidth("Chutier"), topH + 4, Theme.TEXT_DIM, false);
        // filter tabs, as wide as the bin allows
        int textSum = 0;
        for (String f : FILTERS) textSum += g.textWidth(f);
        int avail = binW - 8 - (FILTERS.length - 1) * 2;
        int pad = clamp((avail - textSum) / FILTERS.length, 2, 12);
        int fx = 4;
        for (int i = 0; i < FILTERS.length; i++) {
            int w = g.textWidth(FILTERS[i]) + pad;
            tabX0[i] = fx;
            tabX1[i] = fx + w;
            boolean on = i == filter;
            boolean hover = mx >= fx && mx < fx + w && my >= topH + 16 && my < topH + 27;
            g.fill(fx, topH + 16, fx + w, topH + 27, on ? Theme.BUTTON_ON : hover ? Theme.BUTTON_HOVER : Theme.BUTTON);
            if (on) g.fill(fx, topH + 26, fx + w, topH + 27, Theme.ACCENT);
            g.text(FILTERS[i], fx + (w - g.textWidth(FILTERS[i])) / 2, topH + 18, on ? Theme.TEXT : Theme.TEXT_DIM, false);
            fx += w + 2;
        }
        if (timeline.picked != null) {
            // a moment was clicked on the "Partie" strip: show its shot
            ShotSuggestion p = timeline.picked;
            timeline.picked = null;
            if (!filtered().contains(p)) {
                filter = 0;
                list = filtered();
            }
            int idx = list.indexOf(p);
            int top = binTop(), bottom = binBottom();
            if (idx >= 0 && (idx * ROW < binScroll || (idx + 1) * ROW > binScroll + bottom - top)) binScroll = idx * ROW - (bottom - top - ROW) / 2;
        }
        int top = binTop(), bottom = binBottom();
        int maxScroll = Math.max(0, list.size() * ROW - (bottom - top));
        binScroll = Math.max(0, Math.min(binScroll, maxScroll));
        g.pushClip(0, top, binW, bottom);
        if (list.isEmpty()) {
            String hint = s.analysing ? "Analyse en cours…"
                    : filter == 2 ? "Écris une demande sous l'aperçu, puis Générer."
                    : filter == 3 ? "Caméra libre (Tab) : K pose une image clé, Entrée crée le plan."
                    : filter == 4 ? "Clique sur ☆ à droite d'un plan pour le garder ici."
                    : "Aucun plan : clique sur Réanalyser.";
            wrap(g, hint, 6, top + 6, binW - 12, Theme.TEXT_DIM);
        }
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * ROW - binScroll;
            if (ry + ROW < top || ry > bottom) continue;
            ShotSuggestion sh = list.get(i);
            boolean sel = sh == s.selectedShot;
            boolean hover = mx >= 0 && mx < binW && my >= ry && my < ry + ROW && my >= top && my < bottom;
            g.fill(2, ry + 1, binW - 2, ry + ROW - 1, sel ? Theme.BUTTON_ON : hover ? Theme.BUTTON_HOVER : Theme.PANEL_LIGHT);
            ShotType t = sh.type();
            g.fill(2, ry + 1, 5, ry + ROW - 1, Theme.shotColor(t));
            String title = t != null ? t.label : sh.clip.name;
            String dur = Director.formatTime(sh.clip.durationMs);
            int dw = g.textWidth(dur);
            g.text(Widget.fit(g, title, binW - 24 - dw), 8, ry + 3, Theme.TEXT, false);
            g.text(dur, binW - 16 - dw, ry + 3, Theme.TEXT_DIM, false);
            g.text(Widget.fit(g, sh.description, binW - 22), 8, ry + 13, Theme.momentColor(sh.momentKind), false);
            g.text(sh.favorite ? "★" : "☆", binW - 11, ry + 8, sh.favorite ? Theme.ACCENT : Theme.TEXT_DIM, false);
            if (s.previewShot == sh) g.fill(binW - 4, ry + 1, binW - 2, ry + ROW - 1, Theme.PLAYHEAD);
        }
        g.popClip();
        if (maxScroll > 0) {
            int barH = Math.max(10, (bottom - top) * (bottom - top) / (list.size() * ROW));
            int barY = top + (int) ((bottom - top - barH) * (binScroll / (double) maxScroll));
            g.fill(binW - 2, barY, binW - 1, barY + barH, Theme.TEXT_DIM);
        }
    }

    private void renderInspector(Gfx g, int mx, int my) {
        int x = W - inspW + 4, w = inspW - 8;
        g.text("Inspecteur", x, topH + 4, Theme.TEXT, false);
        Clip c = s.selected;
        if (c == null) {
            g.text(Widget.fit(g, s.sequence.name, w), x, topH + 18, Theme.ACCENT, false);
            int y = topH + 32;
            g.text(s.sequence.clips.size() + " plans · " + Director.formatTime(s.sequence.duration()), x, y, Theme.TEXT_DIM, false);
            ExportSettings e = s.sequence.export;
            g.text(e.width + "×" + e.height + " · " + e.fps + " i/s", x, y + 12, Theme.TEXT_DIM, false);
            g.text(Widget.fit(g, e.format.label, w), x, y + 24, Theme.TEXT_DIM, false);
            int hy = y + 80;
            g.text("Raccourcis", x, hy, Theme.TEXT, false);
            String[] keys = {"Espace  lecture", "S  couper", "Suppr  supprimer", "Ctrl+Z / Ctrl+Y", "Tab  caméra libre", "F  plein écran", "Ctrl+molette  zoom"};
            for (int i = 0; i < keys.length; i++) g.text(Widget.fit(g, keys[i], w), x, hy + 12 + i * 10, Theme.TEXT_DIM, false);
            return;
        }
        g.text(Widget.fit(g, EditorSession.clipLabel(c), w), x, topH + 18, Theme.ACCENT, false);
        int y = topH + 30 + 140;
        g.text("Source " + Director.formatTime(c.sourceStartMs) + " → " + Director.formatTime(c.sourceEndMs()), x, y, Theme.TEXT_DIM, false);
        g.text("Images clés : " + c.path.keys().size(), x, y + 11, Theme.TEXT_DIM, false);
    }

    private void renderViewportOverlay(Gfx g) {
        if (s.analysing) Widget.centered(g, "Le réalisateur analyse la partie…", vpX + vpW / 2, vpY + vpH / 2, Theme.TEXT);
        if (s.backend.isSeeking() || s.backend.isHoldingImage()) {
            String t = "Chargement" + spinner();
            int tw = g.textWidth("Chargement...");
            g.fill(vpX + vpW / 2 - tw / 2 - 5, vpY + 5, vpX + vpW / 2 + tw / 2 + 5, vpY + 18, 0xB0000000);
            g.text(t, vpX + vpW / 2 - tw / 2, vpY + 8, Theme.TEXT, false);
        }
        String msg = s.promptBusy ? s.promptMessage + spinner() : s.promptMessage;
        if (msg != null && !msg.isEmpty()) {
            int tw = Math.min(g.textWidth(msg), vpW - 12);
            g.fill(vpX + 4, vpY + vpH - 16, vpX + 10 + tw, vpY + vpH - 4, 0xB0000000);
            g.text(Widget.fit(g, msg, vpW - 12), vpX + 7, vpY + vpH - 14, s.promptBusy ? Theme.ACCENT : Theme.TEXT, false);
        }
    }

    private static String spinner() {
        int n = (int) (System.currentTimeMillis() / 300 % 4);
        return n == 0 ? "" : n == 1 ? "." : n == 2 ? ".." : "...";
    }

    private void renderToasts(Gfx g) {
        int y = vpY + 6;
        for (EditorSession.Toast t : s.toasts) {
            int tw = Math.min(g.textWidth(t.text), vpW - 20);
            int x = vpX + vpW - tw - 12;
            g.fill(x - 4, y - 3, x + tw + 4, y + 10, t.error ? 0xE0602020 : 0xE0141A2B);
            g.fill(x - 4, y - 3, x - 2, y + 10, t.error ? Theme.RED : Theme.ACCENT);
            g.text(Widget.fit(g, t.text, vpW - 20), x, y, Theme.TEXT, false);
            y += 15;
        }
    }

    /** Help bubble of what is under the mouse, after a short rest so it does not flicker. */
    private void renderTooltip(Gfx g, int mx, int my) {
        String t = hoverTip(mx, my);
        long now = System.currentTimeMillis();
        if (t == null || !t.equals(tipText)) {
            tipText = t;
            tipSince = now;
        }
        if (t == null || now - tipSince < 350) return;
        int tw = Math.min(g.textWidth(t), W - 10);
        int x = Math.max(4, Math.min(mx + 8, W - tw - 6)), y = my + 12;
        if (y + 12 > H) y = my - 14;
        g.fill(x - 3, y - 2, x + tw + 3, y + 10, 0xF0000000);
        Widget.frame(g, x - 3, y - 2, x + tw + 3, y + 10, Theme.BORDER);
        g.text(Widget.fit(g, t, W - 10), x, y, Theme.TEXT, false);
    }

    private String hoverTip(int mx, int my) {
        if (mx < 0 || my < 0) return null;
        for (Widget w : inspector) if (w.contains(mx, my)) return w.tooltipAt(mx, my);
        for (Widget w : widgets) if (w.contains(mx, my)) return w.tooltipAt(mx, my);
        if (mx < binW && my >= topH + 16 && my < topH + 27) {
            for (int i = 0; i < FILTERS.length; i++) if (mx >= tabX0[i] && mx < tabX1[i]) return FILTER_TIPS[i];
        }
        if (mx < binW && my >= binTop() && my < binBottom()) {
            List<ShotSuggestion> list = filtered();
            int i = (my - binTop() + binScroll) / ROW;
            if (i < 0 || i >= list.size()) return null;
            if (mx > binW - 14) return list.get(i).favorite ? "Retirer des favoris" : "Garder en favori";
            return "Clic : aperçu · double-clic : ajouter à la timeline";
        }
        return null;
    }

    /** Draws {@code text} on several lines of at most {@code maxW}; returns the y below it. */
    private static int wrap(Gfx g, String text, int x, int y, int maxW, int color) {
        String line = "";
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && g.textWidth(next) > maxW) {
                g.text(line, x, y, color, false);
                y += 10;
                line = word;
            } else {
                line = next;
            }
        }
        if (!line.isEmpty()) {
            g.text(Widget.fit(g, line, maxW), x, y, color, false);
            y += 10;
        }
        return y;
    }

    private void renderPanel(Gfx g, int mx, int my) {
        g.fill(0, 0, W, H, 0x90000000);
        int pw = Math.min(300, W - 40), ph = Math.min(220, H - 40);
        int px = (W - pw) / 2, py = (H - ph) / 2;
        g.fill(px, py, px + pw, py + ph, Theme.BG);
        Widget.frame(g, px, py, px + pw, py + ph, Theme.BORDER);
        g.fill(px, py, px + pw, py + 18, Theme.HEADER);
        String title = panel == Panel.EXPORT ? "Exporter la vidéo" : panel == Panel.SETTINGS ? "Réglages de Redo" : "Aide";
        g.text(title, px + 8, py + 5, Theme.ACCENT, false);
        int x = px + 10, w = pw - 20;
        if (panel == Panel.EXPORT) {
            File ff = s.ffmpeg();
            String st = ff != null ? "ffmpeg : " + ff.getPath() : "ffmpeg introuvable";
            if (downloader != null && !downloader.isDone()) st = "Téléchargement de ffmpeg " + (int) (downloader.progress() * 100) + " %";
            else if (downloader != null && downloader.error() != null) st = "Échec : " + downloader.error();
            g.text(Widget.fit(g, st, w), x, py + 26 + 108, ff != null ? Theme.GREEN : Theme.TEXT_DIM, false);
            g.text(Widget.fit(g, "Durée : " + Director.formatTime(s.sequence.duration()) + " · rendu image par image, sans saccade.", w), x, py + 26 + 122, Theme.TEXT_DIM, false);
            g.text(Widget.fit(g, "Astuce : plus la fenêtre est grande, plus c'est net.", w), x, py + 26 + 134, Theme.TEXT_DIM, false);
        } else if (panel == Panel.SETTINGS) {
            g.text("Clé API Claude (pour le prompt)", x, py + 26, Theme.TEXT_DIM, false);
            g.text("Modèle", x, py + 54, Theme.TEXT_DIM, false);
            g.text(Widget.fit(g, "Touches : " + Keys.name(Redo.config().keyHighlight) + " temps fort, " + Keys.name(Redo.config().keyMarker) + " marqueur", w),
                    x, py + 26 + 132, Theme.TEXT_DIM, false);
        } else {
            String[] paragraphs = {
                    "Redo a préparé des plans tout seul : ils sont dans le chutier. Clic : aperçu, double-clic : ajouter à la timeline.",
                    "« Créer la bande-annonce » monte une vidéo complète toute seule.",
                    "La bande « Partie » sous la timeline montre les moments repérés : cliquez-en un pour voir ses plans.",
                    "Le prompt crée des plans à partir d'une phrase.",
                    "Caméra libre (Tab) : volez avec vos touches de déplacement, K pose une image clé, Entrée crée le plan.",
                    "En jeu : " + Keys.name(Redo.config().keyHighlight) + " marque un temps fort, " + Keys.name(Redo.config().keyMarker)
                            + " pose un marqueur. Survolez un bouton pour voir son aide."};
            int ly = py + 26;
            for (String p : paragraphs) ly = wrap(g, p, x, ly, w, Theme.TEXT) + 3;
        }
        for (Widget wd : panelWidgets) wd.render(g, mx, my);
    }

    private void renderExport(Gfx g, int mx, int my) {
        ExportJob e = s.export;
        int pw = Math.min(260, W - 20), ph = 64;
        int px = (W - pw) / 2, py = H - ph - 10;
        g.fill(px, py, px + pw, py + ph, 0xE00E1220);
        Widget.frame(g, px, py, px + pw, py + ph, Theme.BORDER);
        String title;
        switch (e.state()) {
            case RENDERING: title = "Export en cours… " + (int) (e.progress() * 100) + " %"; break;
            case FINISHING: title = "Finalisation de la vidéo…"; break;
            case DONE: title = "Vidéo exportée !"; break;
            default: title = "Échec de l'export"; break;
        }
        g.text(title, px + 8, py + 6, e.state() == ExportJob.State.FAILED ? Theme.RED : Theme.ACCENT, false);
        g.fill(px + 8, py + 20, px + pw - 8, py + 24, Theme.BUTTON);
        g.fill(px + 8, py + 20, px + 8 + (int) ((pw - 16) * e.progress()), py + 24, Theme.ACCENT);
        String info = e.state() == ExportJob.State.FAILED ? e.error()
                : e.state() == ExportJob.State.DONE ? e.output.getName()
                : "Image " + e.frame() + " / " + e.frameCount() + (e.remainingMs() > 0 ? " · reste " + Director.formatTime(e.remainingMs()) : "");
        g.text(Widget.fit(g, info, pw - 16), px + 8, py + 30, Theme.TEXT_DIM, false);
        boolean active = e.state() == ExportJob.State.RENDERING || e.state() == ExportJob.State.FINISHING;
        String btn = active ? "Annuler" : e.state() == ExportJob.State.DONE ? "Ouvrir le dossier" : "Fermer";
        int bw = 90, bx = px + pw - bw - 8, by = py + ph - 18;
        boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 14;
        g.fill(bx, by, bx + bw, by + 14, hover ? Theme.BUTTON_HOVER : Theme.BUTTON);
        Widget.centered(g, btn, bx + bw / 2, by + 3, Theme.TEXT);
        if (!active) {
            int cbx = px + 8;
            boolean h2 = mx >= cbx && mx < cbx + 60 && my >= by && my < by + 14;
            g.fill(cbx, by, cbx + 60, by + 14, h2 ? Theme.BUTTON_HOVER : Theme.BUTTON);
            Widget.centered(g, "Retour", cbx + 30, by + 3, Theme.TEXT);
        }
    }

    private boolean exportClick(double mx, double my) {
        ExportJob e = s.export;
        int pw = Math.min(260, W - 20), ph = 64;
        int px = (W - pw) / 2, py = H - ph - 10;
        int bw = 90, bx = px + pw - bw - 8, by = py + ph - 18;
        boolean active = e.state() == ExportJob.State.RENDERING || e.state() == ExportJob.State.FINISHING;
        if (mx >= bx && mx < bx + bw && my >= by && my < by + 14) {
            if (active) {
                e.cancel();
                s.export = null;
            } else {
                if (e.state() == ExportJob.State.DONE) host.openFolder(e.output.getParentFile());
                s.export = null;
            }
            return true;
        }
        if (!active && mx >= px + 8 && mx < px + 68 && my >= by && my < by + 14) {
            s.export = null;
            return true;
        }
        return true;
    }

    // ------------------------------------------------------------------ input

    private void setFocus(Widget w) {
        if (focus == w) return;
        if (focus != null) focus.focusChanged(false);
        focus = w;
        if (focus != null) focus.focusChanged(true);
    }

    @Override
    public void setModifiers(int m) {
        mods = m;
        timeline.setMods(m);
    }

    @Override
    public boolean mouseDown(double mx, double my, int button) {
        if (s.export != null) return exportClick(mx, my);
        if (s.mode == EditorSession.Mode.FULLSCREEN) {
            s.togglePlay();
            return true;
        }
        if (s.mode == EditorSession.Mode.FREE_CAMERA) {
            for (Widget w : freeWidgets) {
                if (w.contains(mx, my)) {
                    pressed = w;
                    w.mouseDown(mx, my, button);
                    return true;
                }
            }
            lookDrag = true;
            return true;
        }
        if (panel != Panel.NONE) {
            for (Widget w : panelWidgets) {
                if (w.contains(mx, my)) {
                    setFocus(w.focusable() ? w : null);
                    pressed = w;
                    w.mouseDown(mx, my, button);
                    return true;
                }
            }
            setFocus(null);
            return true;
        }
        // bin filters
        if (my >= topH + 16 && my < topH + 27 && mx < binW) {
            for (int i = 0; i < FILTERS.length; i++) {
                if (mx >= tabX0[i] && mx < tabX1[i]) {
                    filter = i;
                    binScroll = 0;
                    return true;
                }
            }
        }
        // bin rows
        if (mx < binW && my >= binTop() && my < binBottom()) {
            List<ShotSuggestion> list = filtered();
            int i = (int) ((my - binTop() + binScroll) / ROW);
            if (i >= 0 && i < list.size()) {
                ShotSuggestion sh = list.get(i);
                if (mx > binW - 14) {
                    s.toggleFavorite(sh);
                    return true;
                }
                long now = System.currentTimeMillis();
                if (button == 1) {
                    s.selectedShot = sh;
                    s.previewShot(sh);
                } else if (lastClickShot == sh && now - lastClickAt < 350) {
                    s.insertShot(sh);
                    timeline.follow();
                } else {
                    s.selectedShot = sh;
                    s.previewShot(sh);
                }
                lastClickAt = now;
                lastClickShot = sh;
                setFocus(null);
                return true;
            }
        }
        for (Widget w : inspector) {
            if (w.contains(mx, my)) {
                setFocus(null);
                pressed = w;
                w.mouseDown(mx, my, button);
                return true;
            }
        }
        for (Widget w : widgets) {
            if (w.contains(mx, my)) {
                setFocus(w.focusable() ? w : null);
                pressed = w;
                w.mouseDown(mx, my, button);
                return true;
            }
        }
        // click in the viewport: play / pause
        setFocus(null);
        if (mx >= vpX && mx < vpX + vpW && my >= vpY && my < vpY + vpH) {
            s.togglePlay();
            return true;
        }
        return false;
    }

    @Override
    public void mouseDrag(double mx, double my, int button, double dx, double dy) {
        if (lookDrag) {
            s.freeCam.look(dx * 2, dy * 2);
            return;
        }
        if (pressed != null) pressed.mouseDrag(mx, my, button);
    }

    @Override
    public void mouseUp(double mx, double my, int button) {
        lookDrag = false;
        if (pressed != null) pressed.mouseUp(mx, my, button);
        pressed = null;
    }

    @Override
    public boolean scroll(double mx, double my, double amount) {
        if (s.mode == EditorSession.Mode.FREE_CAMERA) {
            if ((mods & Keys.MOD_CONTROL) != 0) s.freeCam.zoom(-amount * 5);
            else if ((mods & Keys.MOD_ALT) != 0) s.freeCam.roll(amount * 2);
            else Redo.config().flySpeed = Math.max(0.5, Math.min(200, Redo.config().flySpeed * Math.pow(1.25, amount)));
            return true;
        }
        if (s.mode == EditorSession.Mode.FULLSCREEN) return true;
        if (panel != Panel.NONE) {
            for (Widget w : panelWidgets) if (w.scroll(mx, my, amount)) return true;
            return true;
        }
        if (mx < binW && my >= binTop() && my < binBottom()) {
            binScroll -= (int) (amount * ROW);
            return true;
        }
        for (Widget w : inspector) if (w.scroll(mx, my, amount)) return true;
        return timeline.scroll(mx, my, amount);
    }

    /** Returns true if the key was used. Escape closes panels first, then asks to leave. */
    @Override
    public boolean key(int key, int mods) {
        setModifiers(mods);
        if (s.export == null && s.mode == EditorSession.Mode.FREE_CAMERA) return freeCameraKey(key, mods);
        if (s.export == null && s.mode == EditorSession.Mode.FULLSCREEN) {
            switch (key) {
                case Keys.ESCAPE:
                case Keys.F: s.setMode(EditorSession.Mode.MONTAGE); return true;
                case Keys.SPACE: s.togglePlay(); return true;
                case Keys.LEFT: s.step(-5000); return true;
                case Keys.RIGHT: s.step(5000); return true;
                default: return true;
            }
        }
        if (focus != null) {
            if (key == Keys.ESCAPE) {
                setFocus(null);
                return true;
            }
            if (Keys.ctrl(mods) && key == Keys.V && focus instanceof TextField) {
                ((TextField) focus).paste(host.clipboard());
                return true;
            }
            if (focus.key(key, mods)) return true;
        }
        if (panel != Panel.NONE) {
            if (key == Keys.ESCAPE) panel = Panel.NONE;
            return true;
        }
        boolean ctrl = Keys.ctrl(mods);
        switch (key) {
            case Keys.SPACE: s.togglePlay(); return true;
            case Keys.LEFT: s.step(Keys.shift(mods) ? -5000 : -1000 / 20.0 * (ctrl ? 1 : 20)); return true;
            case Keys.RIGHT: s.step(Keys.shift(mods) ? 5000 : 1000 / 20.0 * (ctrl ? 1 : 20)); return true;
            case Keys.HOME: s.setPlayhead(0); return true;
            case Keys.END: s.setPlayhead(s.sequence.duration()); return true;
            case Keys.S: if (!ctrl) { s.splitAtPlayhead(); return true; } s.save(); s.toast("Montage enregistré.", false); return true;
            case Keys.DELETE:
            case Keys.BACKSPACE: s.deleteSelected(!Keys.shift(mods)); return true;
            case Keys.Z: if (ctrl) { if (Keys.shift(mods)) s.redo(); else s.undo(); return true; } return false;
            case Keys.Y: if (ctrl) { s.redo(); return true; } return false;
            case Keys.TAB: s.setMode(EditorSession.Mode.FREE_CAMERA); return true;
            case Keys.F: s.setMode(EditorSession.Mode.FULLSCREEN); return true;
            case Keys.ENTER:
                if (s.selectedShot != null) s.insertShot(s.selectedShot);
                return true;
            case Keys.EQUAL:
            case Keys.KP_ADD: timeline.zoom(1.5, timeline.x + timeline.w / 2.0); return true;
            case Keys.MINUS:
            case Keys.KP_SUBTRACT: timeline.zoom(1 / 1.5, timeline.x + timeline.w / 2.0); return true;
            case Keys.ESCAPE:
                if (s.previewShot != null) {
                    s.stopPreview();
                    return true;
                }
                return false;
            default: return false;
        }
    }

    @Override
    public boolean chr(char c) {
        return focus != null && focus.chr(c);
    }

    /** True when a text field has the keyboard (the platform should not treat keys as shortcuts). */
    @Override
    public boolean typing() {
        return focus instanceof TextField;
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
