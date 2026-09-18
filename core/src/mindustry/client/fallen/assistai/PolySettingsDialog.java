package mindustry.client.fallen.assistai;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.*;
import arc.scene.ui.layout.Table;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.gen.Player;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

public class PolySettingsDialog extends BaseDialog {
    public static PolySettingsDialog instance = new PolySettingsDialog();

    public PolySettingsDialog() {
        super("Poly AI Settings");
        addCloseButton();
        shown(this::rebuild);
    }

    public void rebuild() {
        cont.clear();

        Table main = new Table();
        main.margin(10f);

        // --- 1. НАСТРОЙКИ ЛОГИКИ ---
        main.add("[accent]== Настройки логики ==[]").left().padBottom(6).row();

        main.check("Восстанавливать разрушенные блоки команды", SelfBuilderAI.rebuildBlocks, b -> {
            SelfBuilderAI.rebuildBlocks = b;
            Core.settings.put("poly-rebuild-blocks", b);
        }).left().padTop(3).row();

        main.check("Не строить под огнем вражеских турелей", SelfBuilderAI.checkEnemyTurrets, b -> {
            SelfBuilderAI.checkEnemyTurrets = b;
            Core.settings.put("poly-check-turrets", b);
        }).left().padTop(3).row();

        main.check("Не строить если нет ресурсов в ядре", SelfBuilderAI.checkResources, b -> {
            SelfBuilderAI.checkResources = b;
            Core.settings.put("poly-check-res", b);
        }).left().padTop(3).row();

        main.check("Приоритет обороне и энергии", SelfBuilderAI.prioritizeDefenses, b -> {
            SelfBuilderAI.prioritizeDefenses = b;
            Core.settings.put("poly-prio-defense", b);
        }).left().padTop(3).row();

        main.check("Авто-лечение поврежденных блоков", SelfBuilderAI.healDamaged, b -> {
            SelfBuilderAI.healDamaged = b;
            Core.settings.put("poly-heal", b);
        }).left().padTop(3).row();

        main.check("Искать ближайший блок (не по очереди)", SelfBuilderAI.findClosestPlan, b -> {
            SelfBuilderAI.findClosestPlan = b;
            Core.settings.put("poly-closest", b);
        }).left().padTop(3).row();

        main.image().color(Color.gray).fillX().height(2f).padTop(10f).padBottom(10f).row();

        // --- 2. ФИЛЬТРАЦИЯ ИГРОКОВ ---
        main.add("[accent]== Фильтр помощи игрокам ==[]").left().padBottom(6).row();
        main.check("Помогать ТОЛЬКО избранным (Whitelist)", PolyFilter.onlyWhitelist, b -> {
            Core.settings.put("poly-only-whitelist", b);
            PolyFilter.onlyWhitelist = b;
        }).left().padTop(3).row();

        main.check("Доверять игрокам с кастомным уровнем", PolyFilter.allowCustomLvl, b -> {
            Core.settings.put("poly-allow-custom-lvl", b);
            PolyFilter.allowCustomLvl = b;
        }).left().padTop(3).row();

        // Слайдер уровня
        Table lvlTable = new Table();
        int curMinLvl = PolyFilter.minLevel;
        Label lvlLabel = new Label("Мин. уровень для помощи: [accent]" + curMinLvl + "[]");
        Slider slider = new Slider(0, 50, 1, false);
        slider.setValue(curMinLvl);
        slider.moved(val -> {
            int v = (int) val;
            Core.settings.put("poly-min-lvl", v);
            PolyFilter.minLevel = v;
            lvlLabel.setText("Мин. уровень для помощи: [accent]" + v + "[]");
        });
        lvlTable.add(lvlLabel).left().padRight(10);
        lvlTable.add(slider).growX();
        main.add(lvlTable).growX().padTop(6).row();

        main.image().color(Color.gray).fillX().height(2f).padTop(10f).padBottom(10f).row();

        // --- 3. СПИСОК ИГРОКОВ ---
        main.add("[accent]== Игроки на сервере ==[]").left().padBottom(6).row();

        Table playerTable = new Table();

        if (Groups.player.size() <= 1) {
            playerTable.add("[lightgray]Других игроков нет на сервере").pad(10f);
        } else {
            for (Player p : Groups.player) {
                if (p.isLocal()) continue;

                String key = PolyFilter.getKey(p);
                PolyFilter.PlayerInfo info = PolyFilter.parsePlayer(p);

                Table row = new Table(Styles.black6);
                row.margin(6f);

                // 1. Кнопка Whitelist [★]
                ImageButton btnWhite = row.button(Icon.star, Styles.clearNoneTogglei, () -> {
                    if (PolyFilter.whitelist.contains(key)) {
                        PolyFilter.whitelist.remove(key);
                    } else {
                        PolyFilter.whitelist.add(key);
                        PolyFilter.blacklist.remove(key);
                    }
                }).size(32f).padRight(4f).get();

                btnWhite.update(() -> {
                    boolean inWhite = PolyFilter.whitelist.contains(key);
                    btnWhite.setChecked(inWhite);
                    btnWhite.getStyle().imageUpColor = inWhite ? Color.gold : Color.white;
                });

                // 2. Кнопка Blacklist [✕]
                ImageButton btnBlack = row.button(Icon.cancel, Styles.clearNoneTogglei, () -> {
                    if (PolyFilter.blacklist.contains(key)) {
                        PolyFilter.blacklist.remove(key);
                    } else {
                        PolyFilter.blacklist.add(key);
                        PolyFilter.whitelist.remove(key);
                    }
                }).size(32f).padRight(8f).get();

                btnBlack.update(() -> {
                    boolean inBlack = PolyFilter.blacklist.contains(key);
                    btnBlack.setChecked(inBlack);
                    btnBlack.getStyle().imageUpColor = inBlack ? Color.scarlet : Color.white;
                });

                // 3. Никнейм и уровень
                String lvlText = info.isCustomLevel ? "[gold]<" + info.customTag + ">[]" : "[green]<Lvl " + info.level + ">[]";
                if (info.isAfk) lvlText = "[gray]<AFK>[] " + lvlText;

                row.add(p.name + " " + lvlText).growX().left();

                playerTable.add(row).growX().padBottom(4f).row();
            }
        }

        ScrollPane pane = new ScrollPane(playerTable);
        pane.setScrollingDisabled(true, false);
        pane.setFadeScrollBars(false);

        main.add(pane).growX().height(380f).row();

        cont.add(new ScrollPane(main)).width(900f);
    }
}