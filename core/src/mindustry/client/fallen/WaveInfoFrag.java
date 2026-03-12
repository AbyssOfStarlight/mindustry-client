package mindustry.client.fallen;

import arc.*;
import arc.graphics.*;
import arc.input.*;
import arc.math.Mathf;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.Pal;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.client.Spectate;
import mindustry.content.StatusEffects;

import static arc.Core.scene;
import static mindustry.Vars.*;

public class WaveInfoFrag extends Table {
    private Table container = new Table();
    private Table contenttab = new Table();
    private int waveOffset = 0;
    private boolean visible = false;

    private float lastX = 0, lastY = 0;
    private boolean centered = false;

    public void build(Group parent) {
        parent.addChild(this);
        //setSize(300f, 400f);

        float w = Core.settings.getFloat("wavefragwidth", 300f);
        float h = Core.settings.getFloat("wavefragheight", 400f);
        if(w < 50f) w = 300f;
        if(h < 50f) h = 400f;
        setSize(w, h);

        Events.on(EventType.WaveEvent.class, e -> rebuild());


        visible(() -> visible && state.isGame() && !state.rules.pvp && Core.settings.getBool("wavefragment", false));

        table(Styles.black6, main -> {
            main.table(ctrl -> {
                ImageButton drag = ctrl.button(Icon.move, Styles.cleari, () -> {}).size(35f).get();
                drag.addListener(new InputListener() {
                    @Override
                    public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
                        lastX = x; lastY = y; return true;
                    }
                    @Override
                    public void touchDragged(InputEvent event, float x, float y, int pointer) {
                        moveBy(x - lastX, y - lastY);
                    }

                    @Override
                    public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button) {
                        Core.settings.put("wavefrag-x", WaveInfoFrag.this.x);
                        Core.settings.put("wavefrag-y", WaveInfoFrag.this.y);
                    }
                });

                ctrl.add().growX().top();
                ctrl.button(Icon.left, Styles.cleari, () -> { waveOffset -= Core.input.shift() ? 10 : 1; rebuild(); }).size(40f).disabled(b -> state.wave + waveOffset <= 1);
                ctrl.button(Icon.refresh, Styles.cleari, () -> { waveOffset = 0; rebuild(); }).size(40f);
                ctrl.button(Icon.right, Styles.cleari, () -> { waveOffset += Core.input.shift() ? 10 : 1; rebuild(); }).size(40f);
                ctrl.add().growX();
                ctrl.button(Icon.cancel, Styles.cleari, this::toggle).size(35f);
            }).growX().top().pad(2f);

            main.row().top();
            main.image().growX().height(2f).color(Pal.accent);
            main.row();

            main.pane(p -> {
                p.top();
                p.add(contenttab).growX().top();
                container = contenttab;
                rebuild();
            }).growX().top().update(pane -> {
                if(scene.getScrollFocus() == pane && !Core.input.shift()){
                    scene.setScrollFocus(null);
                }
            });
        }).growX().top();

        update(() -> {
            if(!centered && Core.graphics.getWidth() > 0){
                if(Core.settings.has("wavefrag-x") && Core.settings.has("wavefrag-y")){
                    float sx = Core.settings.getFloat("wavefrag-x");
                    float sy = Core.settings.getFloat("wavefrag-y");

                    sx = Mathf.clamp(sx, 0, Core.graphics.getWidth() - width);
                    sy = Mathf.clamp(sy, 0, Core.graphics.getHeight() - height);
                    setPosition(sx, sy);
                } else {
                    setPosition(Core.graphics.getWidth() / 2f, 150f, Align.bottom);
                }
                centered = true;
            }
        });

    }
    public void updateSize(){
        rebuild();
        float w = Core.settings.getFloat("wavefragwidth", 300f);
        float h = Core.settings.getFloat("wavefragheight", 400f);
        if(w < 50f) w = 300f;
        if(h < 50f) h = 400f;
        setSize(w, h);
    }
    public void resetPos(){
        setPosition(Core.graphics.getWidth() / 2f, 150f, Align.bottom);
        rebuild();
    }

    public void rebuild() {
        if (container == null) return;
        container.clear();
        container.defaults().growX().margin(0f).pad(0f);

        int startWave = state.wave + waveOffset;
        int spawnCount = Math.max(spawner.getSpawns().size, 1);
        float font_offset = Core.settings.getFloat("wave_font_offset", 1);


        for (int i = 0; i < 4; i++) {
            int displayWave = startWave + i;
            if (displayWave <= 0) continue;
            int internalWave = displayWave - 1;

            container.table(Styles.black3, card -> {
                card.margin(2f).top();
                card.touchable = Touchable.enabled;
                card.addListener(new HandCursorListener());
                card.clicked(() -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Wave ").append(displayWave).append(":\n");

                    int spawnCountCopy = Math.max(spawner.getSpawns().size, 1);

                    for (SpawnGroup group : state.rules.spawns) {
                        int amt = group.getSpawned(internalWave);
                        if (amt <= 0) continue;

                        if (group.spawn == -1 || spawnCountCopy <= 1) {
                            sb.append(" ").append(Fonts.getUnicodeStr(group.type.name))
                                    .append("x").append(amt * (spawnCountCopy <= 1 ? 1 : spawnCountCopy));
                        } else {
                            sb.append("- (").append(Point2.x(group.spawn)).append(",").append(Point2.y(group.spawn))
                                    .append(") ").append(Fonts.getUnicodeStr(group.type.name))
                                    .append("x").append(amt);
                        }

                        if (group.effect != null && group.effect != StatusEffects.none) {
                            sb.append(Fonts.getUnicodeStr(group.effect.name));
                        }
                        sb.append("\n");
                    }

                    Core.app.setClipboardText(sb.toString());
                });


                card.table(ht -> {
                    ht.add((displayWave == state.wave ? "[accent]" : "[white]") + "WAVE " + displayWave).fontScale(0.8f*font_offset);
                }).growX().center().row();

                card.image().height(1f).growX().color(Color.darkGray).row();

                card.table(units -> {
                    units.left().defaults().left();

                    ObjectMap<SpawnKey, Integer> allSpawnsMap = new ObjectMap<>();
                    ObjectMap<Integer, ObjectMap<SpawnKey, Integer>> groupedSpecific = new ObjectMap<>();

                    for (SpawnGroup group : state.rules.spawns) {
                        int amt = group.getSpawned(internalWave);
                        if (amt <= 0) continue;

                        StatusEffect eff = (group.effect == StatusEffects.none) ? null : group.effect;
                        SpawnKey key = new SpawnKey(group.type, eff);

                        // Логика All: если на карте 1 спавн, все падает в All
                        if (group.spawn == -1 || spawnCount <= 1) {
                            allSpawnsMap.put(key, allSpawnsMap.get(key, 0) + amt);
                        } else {
                            if (!groupedSpecific.containsKey(group.spawn)) groupedSpecific.put(group.spawn, new ObjectMap<>());
                            groupedSpecific.get(group.spawn).put(key, groupedSpecific.get(group.spawn).get(key, 0) + amt);
                        }
                    }

                    if (allSpawnsMap.isEmpty() && groupedSpecific.isEmpty()) {
                        units.add("No bastards").color(Color.gray).fontScale(0.7f*font_offset).center().growX();
                    } else {
                        // 1. All Spawns
                        if (!allSpawnsMap.isEmpty()) {
                            units.table(allTable -> {
                                allTable.left();
                                String label = spawnCount <= 1 ? "(All) " : "(All*" + spawnCount + ") ";
                                allTable.add("[lightgray]" + label).fontScale(0.65f*font_offset);

                                int col = 0;
                                for (var entry : allSpawnsMap.entries()) {
                                    if(col > 0 && col % 5 == 0) allTable.row().add().padRight(4f);

                                    allTable.table(uRow -> {
                                        uRow.image(entry.key.type.uiIcon).size(14f);
                                        uRow.add("[white]" + (entry.value * (spawnCount <= 1 ? 1 : spawnCount))).fontScale(0.75f*font_offset).padLeft(1f);
                                        if(entry.key.effect != null) uRow.image(entry.key.effect.uiIcon).size(10f).padLeft(1f);
                                    }).padRight(4f);
                                    col++;
                                }
                            }).growX().row();
                        }

                        // 2. Specific Spawns
                        for (var entry : groupedSpecific.entries()) {
                            units.table(row -> {
                                row.left();
                                String loc = "(" + Point2.x(entry.key) + "," + Point2.y(entry.key) + ")";
                                Label l = row.add("[gray]" + loc + " ").fontScale(0.65f*font_offset).get();
                                l.touchable = Touchable.enabled;
                                l.addListener(new ClickListener() {
                                    @Override
                                    public void clicked(InputEvent event, float x, float y) {
                                        Spectate.INSTANCE.spectate(new Vec2(Point2.x(entry.key) * tilesize, Point2.y(entry.key) * tilesize));
                                    }
                                });

                                for (var unitEntry : entry.value.entries()) {
                                    row.image(unitEntry.key.type.uiIcon).size(14f).padLeft(2f);
                                    row.add("[white]" + unitEntry.value).fontScale(0.75f*font_offset).padLeft(1f);
                                    if (unitEntry.key.effect != null) row.image(unitEntry.key.effect.uiIcon).size(10f).padLeft(1f);
                                }
                            }).growX().row();
                        }
                    }
                }).growX().padTop(1f);
            }).growX().pad(0f).row();
        }
    }

    private static class SpawnKey {
        UnitType type; StatusEffect effect;
        SpawnKey(UnitType type, StatusEffect effect) { this.type = type; this.effect = effect; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SpawnKey)) return false;
            SpawnKey key = (SpawnKey) o;
            return type == key.type && effect == key.effect;
        }
        @Override public int hashCode() { return type.hashCode() * 31 + (effect != null ? effect.hashCode() : 0); }
    }

    public void toggle() { visible = !visible; rebuild(); if(visible) toFront(); }
}