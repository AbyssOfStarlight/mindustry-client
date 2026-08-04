package mindustry.client.fallen;

import arc.Core;
import arc.func.Cons;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import mindustry.client.ui.PanelFragment;
import mindustry.ui.dialogs.BaseDialog;

public class MinersSettingsDialog extends BaseDialog {

    public MinersSettingsDialog() {
        super(Core.bundle.get("client.fdtrash.mining.settings", "Mining AI Settings"));

        addCloseButton();
        setup();
    }

    private void setup() {
        cont.clear();
        cont.pane(all -> {
            all.add("@client.fdtrash.mining").left().padTop(10).row();

            all.table(tt -> {
                tt.defaults().left().pad(4);

                addSlider(tt, "@client.fdtrash.minUnitsPerResource", 0, 15, 1,
                        (float) PanelFragment.minUnitsPerResource,
                        v -> PanelFragment.minMinUnitsSet(v.intValue()), " x");

                addSlider(tt, "@client.fdtrash.crisisThreshold", 1f, 50f, 1f,
                        PanelFragment.crisisThreshold * 100,
                        v -> PanelFragment.crisisThreshold = v / 100f, " %");

                tt.check("@client.fdtrash.mineMonos", PanelFragment.mineMonos, b -> PanelFragment.mineMonos = b).row();
                tt.check("@client.fdtrash.minePolys", PanelFragment.minePolys, b -> PanelFragment.minePolys = b).row();
                tt.check("@client.fdtrash.minePulss", PanelFragment.minePulss, b -> PanelFragment.minePulss = b).row();
                tt.check("@client.fdtrash.mineQuazs", PanelFragment.mineQuazs, b -> PanelFragment.mineQuazs = b).row();
                tt.check("@client.fdtrash.mineMegas", PanelFragment.mineMegas, b -> PanelFragment.mineMegas = b).row();
                tt.check("@client.fdtrash.megaAutoHeal", PanelFragment.autoHealMegas, b -> PanelFragment.autoHealMegas = b).row();
                tt.check("@client.fdtrash.resetDisabledUnits", Core.settings.getBool("resetDisabledUnits", false), b -> Core.settings.put("resetDisabledUnits", b)).row();

                addSlider(tt, "@client.fdtrash.megadistheal", 10, 500, 10,
                        PanelFragment.autoHealDist,
                        v -> PanelFragment.autoHealDist = v, " x");

                addSlider(tt, "@client.fdtrash.updatetime", 1, 20, 1,
                        (float) MinersFDAI.AIMiningUpdateTime,
                        v -> {
                            MinersFDAI.AIMiningUpdateTime = v.intValue();
                            Core.settings.put("AIUpTime", v.intValue());
                        }, " x");

                addSlider(tt, "@client.fdtrash.helprad", 1, 50, 1,
                        (float) MinersFDAI.AIHelpRad,
                        v -> {
                            MinersFDAI.AIHelpRad = v;
                            Core.settings.put("AIHelpRad", v);
                        }, " tile");

            }).left().row();
        }).fillX().fillY();
    }

    // Вспомогательный метод для создания слайдеров
    private void addSlider(Table table, String text, float min, float max, float step, float def, Cons<Float> changed, String suffix) {
        table.table(t -> {
            Label val = new Label(String.valueOf((int) def) + suffix);
            t.add(text).left().width(180f); // Фиксированная ширина для выравнивания
            Slider slider = new Slider(min, max, step, false);
            slider.setValue(def);
            slider.changed(() -> {
                changed.get(slider.getValue());
                val.setText(String.valueOf((int) slider.getValue()) + suffix);
            });
            t.row();
            t.add(slider).width(150f).padLeft(10);
            t.add(val).padLeft(10).width(40f);
        }).row();
    }
}