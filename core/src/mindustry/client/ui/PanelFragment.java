package mindustry.client.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;

import arc.input.KeyCode;
import arc.math.Mathf;

import arc.math.geom.Vec2;
import arc.scene.*;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.ai.ItemUnitStance;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.client.ClientVars;
import mindustry.client.fallen.*;
import mindustry.client.fallen.miners.MinersFDAI;
import mindustry.client.fallen.miners.MinersSettingsDialog;
import mindustry.client.navigation.BuildPath;
import mindustry.client.navigation.MinePath;
import mindustry.client.navigation.Navigation;
import mindustry.client.navigation.RepairPath;
import mindustry.client.utils.AutoTransfer;
import mindustry.content.*;
import mindustry.core.NetClient;
import mindustry.entities.Units;
import mindustry.game.EventType.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.fragments.ChatFragment;
import mindustry.world.*;
import mindustry.world.blocks.ConstructBlock.*;
import mindustry.world.blocks.sandbox.ItemSource;
import mindustry.world.blocks.sandbox.LiquidSource;
import mindustry.world.blocks.sandbox.PowerSource;
import mindustry.world.blocks.sandbox.PowerVoid;
import mindustry.world.blocks.storage.CoreBlock;
import mindustry.world.blocks.units.Reconstructor;
import mindustry.world.blocks.units.UnitFactory;
import arc.struct.Seq;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static arc.Core.*;
import static mindustry.Vars.*;
import static mindustry.client.fallen.miners.MinersFDAI.minePolys;

public class PanelFragment extends Table{
    public Table fdpanel; //Создание интерфейса дял кнопок
    public static Seq<Item> itemtomine = new Seq<>(); //Создание выборки для копания
    public static boolean minecopper = false,minelead = false, minetitan = false, minesand = false, minecoal = false, minescrap = false;
    public static boolean mineBerylliumwall, mineGraphiticwall;
    private float brokenFade = 0f;
    public static int max_length = 146;
    private final IntMap<EffState> effStorage = new IntMap<>();


    private boolean eneblemining = false;
    private boolean viewunitshealth = false;
    private boolean viewunitseffects = false;
    private boolean viewprogressunit = false;
    private boolean viewprogresbuild  = false;
    private boolean viewEfficiency = false;


    public static int currentfollowmode = 0; // 1 - mine, 2 - build, 3 - heal
    public static int prevfollowmode = 0;
    public static boolean forcesavelogs = false;
    public static boolean rtvWaveKey = false;
    public static boolean rtvKey = false;

    private static final GlyphLayout layout = new GlyphLayout();
    private static final StringBuilder sb = new StringBuilder();
    private static final Color tmpCol = new Color();
    private static final Bits tempBits = new Bits();

    //шизааааааааааааааа
    public static boolean triEnabled = false;
    public static int triUnitCount = 11;
    public static float triSize = 90f;
    public static float triRotSpeed = 0.1f;
    public static int triUnitTypeIndex = 0; // Для слайдера типов

    // Отсортированный список типов юнитов
    public static Seq<UnitType> sortedUnitTypes = new Seq<>();
    public static final Seq<Unit> followers = new Seq<>();
    private static int syncTimer = 0;

    String temp_name = Core.settings.getString("mynickshifter", "nani");


    public PanelFragment(){ //Основной класс

        Timer.schedule(()->{
            if (net.client() && Core.settings.getBool("shift_nick", false)){
                temp_name = shiftColorsRight(temp_name);
                Call.sendChatMessage("/name " + temp_name);
            }
        }, 60f, 300f);

        Events.run(Trigger.update, () -> {
            if(!Vars.state.isMenu()) {
//                FDAutoFill.update();
                CustomBuildLogic.update();
            }
        });

        Events.run(Trigger.draw, () -> { //Постоянный вызов прорисовки
            if(ui.hudfrag.shown) {
                drawBuildings();
                drawUnits();
                FDAutoShoot.drawTarget();
                FDAutoShoot.drawUnitAim();
                FDAutoShoot.update();
            }
        });

        Events.on(WaveEvent.class, e -> {
            if (net.client() && rtvWaveKey) {
                Timer.schedule(() -> Call.sendChatMessage("/rtv wave"), 10f);
            }
        });

        Events.on(WorldLoadEvent.class, e -> { //Ивент, срабатывающий при загрузке карты
//            FDEnemyWarning.init();
            initTypes();
            Timer.schedule(() -> {
                if(net.client() && player.unit() != null && player.unit().id != -1){
                    //Call.sendChatMessage("/vanish 1");
                }
                if (net.client() && rtvKey) {
                    Call.sendChatMessage("/rtv");
                }
            }, 5f);
            rebuild();
//            autoMiningActive = false;
            minecopper = true; minelead = true; minetitan = true;
            mineBerylliumwall = true; mineGraphiticwall = true;
            //minesand = false; minecoal = false;
            minescrap = false;
            itemtomine.clear();
            updatemineitems();
            effStorage.clear();
        });
        Events.on(UnitControlEvent.class, e -> { //Проверка ресурсов при смене юнита
            updatemineitems();
        });
        Events.on(UnitChangeEvent.class, e -> { //Проверка ресурсов при смене юнита
            updatemineitems();
        });

        Events.run(Trigger.update, () -> { //currentfollowmode // 1 - mine, 2 - build, 3 - heal //переключения в режиме афк
            if (player == null || player.unit() == null) return;
            updateTriControl();
//            FDEnemyWarning.update();
            if(Core.settings.getBool("afkmode")){
                if(Navigation.currentlyFollowing == null){currentfollowmode = 1; startmining();}
                else if(Navigation.currentlyFollowing instanceof BuildPath){
                    if(player.unit().plans.size == 0 && control.input.isBuilding ){ currentfollowmode = 1; if(prevfollowmode == 1){startmining();}else{Navigation.follow(new RepairPath(), true); } }
                } else if(Navigation.currentlyFollowing instanceof MinePath){
                    if(player.unit().plans.size != 0 && control.input.isBuilding ) {prevfollowmode = 1; currentfollowmode = 1; Navigation.follow(new BuildPath("self")); } else return;
                }else if(Navigation.currentlyFollowing instanceof RepairPath){
                    if(player.unit().plans.size != 0 && control.input.isBuilding ) {prevfollowmode = 3; currentfollowmode = 3; Navigation.follow(new BuildPath("self")); } else return;
                }
            }
        });
    }

    public static String shiftColorsRight(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        List<String> colors = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();

        int i = 0;
        int len = input.length();
        while (i < len) {
            // Ищем начало тега цвета: "[#"
            if (input.charAt(i) == '[' && i + 2 < len && input.charAt(i + 1) == '#') {
                int endBracket = input.indexOf(']', i + 2);
                if (endBracket != -1) {
                    // Нашли валидный тег [#...]
                    colors.add(input.substring(i, endBracket + 1));
                    texts.add(currentText.toString());
                    currentText.setLength(0); // Очищаем буфер для следующего сегмента текста
                    i = endBracket + 1;
                    continue;
                }
            }
            // Обычный символ
            currentText.append(input.charAt(i));
            i++;
        }
        // Добавляем остаток текста после последнего цвета
        texts.add(currentText.toString());

        // Если цветов 0 или 1, сдвигать нечего
        if (colors.size() <= 1) {
            return input;
        }

        // Циклический сдвиг коллекции вправо на 1 позицию
        Collections.rotate(colors, 1);

        // Собираем строку обратно, чередуя сдвинутые цвета и исходные тексты
        StringBuilder result = new StringBuilder(input.length());
        result.append(texts.get(0)); // Текст до первого цвета (обычно пустой)
        for (int k = 0; k < colors.size(); k++) {
            result.append(colors.get(k));
            result.append(texts.get(k + 1));
        }

        return result.toString();
    }

    public static void startInit() {
        mindustry.client.fallen.ActivityLogger.init();
        MinersFDAI.init();
        AntiAttemPatcher.load();
        Log.info("Start init");
    }

    void rebuild(){         //category does not change on rebuild anymore, only on new world load
        Group group = fdpanel.parent;
        int index = fdpanel.getZIndex();
        fdpanel.remove();
        build(group);
        fdpanel.setZIndex(index);
    }

    public void build(Group parent){
        parent.fill(full -> {
            fdpanel = full;
            full.center().left().visible(() -> ui.hudfrag.shown);
            fdpanel.table(t -> {
                ImageButton.ImageButtonStyle sstyle = Styles.clearNonei;
                ImageButton.ImageButtonStyle sstylet = Styles.clearNoneTogglei;
                t.defaults().size(settings.getInt("buttonsizefdpamel", 30) * 1f);
                t.label(() -> {
                    if (player == null || player.unit() == null) return "HP: -/-";
                    return "HP:" + Mathf.floor(player.unit().health * 10) / 10f + "/" + Mathf.floor(player.unit().maxHealth * 10) / 10f;
                }).height(17f);
                t.row();
                t.label(() -> {
                    if (player == null || player.unit() == null) return "Shield: -";
                    return "Shield:" + Mathf.floor(player.unit().shield * 10) / 10f;
                }).height(17f);

                t.row();

                t.table(tb->{
                    tb.button(Icon.wrenchSmall, sstylet, ()->{minePolys = !minePolys;}).tooltip("Переклчюить режим копки полей").update(i -> i.setChecked(minePolys)).width(settings.getInt("buttonsizefdpamel", 30)).height(settings.getInt("buttonsizefdpamel", 30) / 2f);
                    tb.row();

                    tb.button(Icon.modeSurvivalSmall, sstylet, () -> {
                                MinersFDAI.autoMiningActive = !MinersFDAI.autoMiningActive;
                            }).update(b -> {
                                b.setChecked(MinersFDAI.autoMiningActive);
                                b.getImage().setColor(MinersFDAI.autoMiningActive ? Color.cyan : Color.white);
                            }).tooltip("Автоматически копать руду")
                            .width(settings.getInt("buttonsizefdpamel", 30))
                            .height(settings.getInt("buttonsizefdpamel", 30) / 2f);
                });

                t.table(tb->{
                    tb.defaults().size(settings.getInt("buttonsizefdpamel", 30) / 2f);
                    tb.button(Icon.mapSmall, sstylet, () -> {
                        minesand = !minesand;
                        updatemineitems();
                    }).update(i -> i.setChecked(minesand)).name("minesand").tooltip("Mine sand");

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        minecoal = !minecoal;
                        updatemineitems();
                    }).update(i -> i.setChecked(minecoal)).name("minecoal").tooltip("Mine coal");
                    tb.row();
                    tb.button(Icon.mapSmall, sstylet, () -> {
                        minelead = !minelead;
                        updatemineitems();
                    }).update(i -> i.setChecked(minelead)).name("minelead").tooltip("Mine lead");

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        minecopper = !minecopper;
                        updatemineitems();
                    }).update(i -> i.setChecked(minecopper)).name("minecopper").tooltip("Mine copper");
                });

                t.table(tb->{
                    tb.defaults().size(settings.getInt("buttonsizefdpamel", 30) / 2f);

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        minescrap = !minescrap;
                        updatemineitems();
                    }).update(i -> i.setChecked(minescrap)).name("minescrap").tooltip("Mine scrap");

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        mineBerylliumwall = !mineBerylliumwall;
                        updatemineitems();
                    }).update(i -> i.setChecked(mineBerylliumwall)).name("Beryllium").tooltip("Mine Beryllium wall");

                    tb.row();

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        minetitan = !minetitan;
                        updatemineitems();
                    }).update(i -> i.setChecked(minetitan)).name("minetitan").tooltip("Mine titan");

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        mineGraphiticwall = !mineGraphiticwall;
                        updatemineitems();
                    }).update(i -> i.setChecked(mineGraphiticwall)).name("mineGraphiticwall").tooltip("Mine Graphitic wall");

                });


                t.table(tb->{
                    tb.button(Icon.mapSmall, sstylet, () -> {
                        MinersFDAI.autoAssistBuild = !MinersFDAI.autoAssistBuild;
                    }).update(i -> i.setChecked(MinersFDAI.autoAssistBuild)).name("autoAssistBuild")
                            .tooltip("autoAssistBuild")
                            .width(settings.getInt("buttonsizefdpamel", 30)).height(settings.getInt("buttonsizefdpamel", 30) / 2f);

                    tb.row();

                    tb.button(Icon.mapSmall, sstylet, () -> {
                        MinersFDAI.respectManualCommands = !MinersFDAI.respectManualCommands;
                    }).update(i -> i.setChecked(MinersFDAI.respectManualCommands))
                            .name("respectManualCommands").tooltip("respectManualCommands")
                            .width(settings.getInt("buttonsizefdpamel", 30)).height(settings.getInt("buttonsizefdpamel", 30) / 2f);
                });


                t.button(Icon.settingsSmall, sstyle, () -> {
                    MinersSettingsDialog.get().show();
                }).name("MinersSettingsDialog").tooltip("MinersSettingsDialog");


//                t.button(Icon.distributionSmall, sstyle, () -> {
//                    currentfollowmode = 3;
//                    Navigation.follow(new RepairPath(), true);
//                }).name("healer").tooltip("Heal");
//
//                t.button(Icon.distributionSmall, sstyle, () -> {
//                    currentfollowmode = 2;
//                    Navigation.follow(new BuildPath("self"));
//                }).name("builder").tooltip("Self builder");

                t.button(Icon.terminalSmall, sstyle, () -> {
                    eneblemining = !eneblemining;
                    startmining();
                }).name("miner").tooltip("Mine!");

                t.row();

                t.button(Icon.eyeOffSmall, sstyle, () -> {
                    enableLight = !enableLight;
                }).name("light").tooltip("light");


                t.button(Icon.planetSmall, sstylet, () -> {
                    viewunitshealth = !viewunitshealth;
                }).update(i -> i.setChecked(viewunitshealth)).name("viewunitshealth").tooltip("Units health bar");

                t.button(Icon.unitsSmall, sstylet, () -> {
                    viewprogressunit = !viewprogressunit;
                }).update(i -> i.setChecked(viewprogressunit)).name("ubprogress").tooltip("Units build progress");

                t.button(Icon.craftingSmall, sstylet, () -> {
                    viewprogresbuild = !viewprogresbuild;
                }).update(i -> i.setChecked(viewprogresbuild)).name("bbprogress").tooltip("Buildings build progress");

                t.button(Icon.chartBar, sstylet, () -> {
                    viewEfficiency = !viewEfficiency;
                }).update(i -> i.setChecked(viewEfficiency)).name("vefficiency").tooltip("Building Efficiency (Real-time)");

                t.button(Icon.unitsSmall, sstylet, () -> {
                    viewunitseffects = !viewunitseffects;
                }).update(i -> i.setChecked(viewunitseffects)).name("viewunitseffects").tooltip("Unit Status Effects");

                t.row();

                t.button(Icon.eyeSmall, sstyle, this::checkunits).tooltip("Eye of Sauron: Units");

                t.button(Icon.eyeSmall, sstyle, this::checkcores).tooltip("Eye of Sauron: Cores");

                t.button(Icon.eyeSmall, sstyle, this::checkspawns).tooltip("Eye of Sauron: Spawns");

                t.button(Icon.eyeSmall, sstyle, this::checkvoids).tooltip("Eye of Sauron: Voids");

                t.button(Icon.eyeSmall, sstyle, this::checksources).tooltip("Eye of Sauron: Sources");

                t.button(Icon.eyeSmall, sstyle, this::checkworldprocc).tooltip("Eye of Sauron: World Processor");

                t.row();

                t.button(Icon.refreshSmall, sstyle, () -> {
                    Call.sendChatMessage("/sync");
                }).name("sync").tooltip("/sync");

                t.button(Icon.hammerSmall, sstyle, () -> {
                    Call.sendChatMessage("/vote y");
                }).name("vote").tooltip("/vote y");

//                t.button(Icon.itchioSmall, sstyle, () -> {
//                    Call.sendChatMessage("/rtv");
//                }).name("rtv").tooltip("/rtv");

                ImageButton rtv = t.button(Icon.itchioSmall, Styles.clearNoneTogglei, () -> {}).update(i -> i.setChecked(rtvKey)).tooltip("/rtv").get();
                rtv.addListener(new InputListener() {
                    @Override public boolean touchDown(InputEvent e, float x, float y, int p, KeyCode b) {
                        if (b == KeyCode.mouseLeft) {
                            Call.sendChatMessage("/rtv");
                            return true;
                        } else
                        if (b == KeyCode.mouseRight) {
                            rtvKey = !rtvKey;
                            return true;
                        }
                        return false;
                    }
                });

//                t.button(Icon.wavesSmall, sstyle, () -> {
//                    Call.sendChatMessage("/rtv wave");
//                }).name("rtv wave").tooltip("/rtv wave");

                ImageButton rtvWave = t.button(Icon.wavesSmall, Styles.clearNoneTogglei, () -> {}).update(i -> i.setChecked(rtvWaveKey)).tooltip("/rtv wave").get();
                rtvWave.addListener(new InputListener() {
                    @Override public boolean touchDown(InputEvent e, float x, float y, int p, KeyCode b) {
                        if (b == KeyCode.mouseLeft) {
                            Call.sendChatMessage("/rtv wave");
                            return true;
                        } else
                        if (b == KeyCode.mouseRight) {
                            rtvWaveKey = !rtvWaveKey;
                            return true;
                        }
                        return false;
                    }
                });


                t.button(Icon.menuSmall, sstyle, () -> {
                    Call.sendChatMessage("/history");
                }).name("history").tooltip("/history");

                if(Core.settings.getBool("OneLoliToRuleThemAll", false)) {

                    t.button(Icon.warningSmall, sstylet, () -> {
                        temp_name = shiftColorsRight(Core.settings.getString("mynickshifter", "nani"));
                        Core.settings.put("shift_nick", !Core.settings.getBool("shift_nick"));
                    }).update(i -> i.setChecked(Core.settings.getBool("shift_nick", false))).name("shift_nick").tooltip("shift_nick");
                }

                t.row();

                t.button(Icon.starSmall, sstylet, () -> {
                    settings.put("smarttargeting", !settings.getBool("smarttargeting"));
                }).update(i -> i.setChecked(settings.getBool("smarttargeting"))).name("smarttargeting").tooltip("smarttargeting(пробел+лкм)");

                t.button(Icon.cancelSmall, sstylet, () -> {
                    settings.put("ignoreunit", !settings.getBool("ignoreunit"));
                }).update(i -> i.setChecked(settings.getBool("ignoreunit"))).name("ignoreunit").tooltip("ignoreunit");

                t.button(Icon.cancelSmall, sstylet, () -> {
                    settings.put("ignoreheal", !settings.getBool("ignoreheal"));
                }).update(i -> i.setChecked(settings.getBool("ignoreheal"))).name("ignoreheal").tooltip("ignoreheal");

                t.button(Icon.lineSmall, sstylet, () -> {
                    FDAutoShoot.viewUnitAim = !FDAutoShoot.viewUnitAim;
                }).update(i -> i.setChecked(FDAutoShoot.viewUnitAim)).name("vaim").tooltip("Player Unit Range & Real Aim");

//                t.button(Icon.warningSmall, sstylet, () -> {
//                    enemyComing = !enemyComing;
//                }).update(i -> i.setChecked(enemyComing)).name("enemyComing").tooltip("Warn about impudent enemies");

                t.row();

                t.button(Icon.powerSmall, sstylet, () -> {
                    String message = "!fixpower c";
                    CommandHandler.CommandResponse response = ClientVars.clientCommandHandler.handleMessage(message, player);
                }).update(i -> i.setChecked(settings.getBool("fixpower"))).name("fixpower").tooltip("fixpower");


                t.button(Icon.unitsSmall, sstyle, () -> {
                    String message = "!uc " + UnitTypes.mega.localizedName;
                    ClientVars.clientCommandHandler.handleMessage(message, player);
                }).name("mega").tooltip("mega");

                t.button(Icon.fileTextSmall, sstyle, () -> {
                    String message = "!fixcode r";
                    CommandHandler.CommandResponse response = ClientVars.clientCommandHandler.handleMessage(message, player);
                }).name("fixcode").tooltip("fixcode");

                t.button(Icon.gridSmall, sstylet, () -> {
                    Core.settings.put("prod-anal", !Core.settings.getBool("prod-anal"));
                }).update(i -> i.setChecked(settings.getBool("prod-anal"))).name("prod-anal").tooltip("prod-anal");





//                t.button(Icon.mapSmall, sstylet, () -> {
//                    triEnabled = !triEnabled;
//                    if (!triEnabled) {
//                        // При выключении отпускаем юнитов (очищаем планы)
//                        for(Unit u : followers) {
//                            if(u.isValid()) u.plans.clear();
//                        }
//                        followers.clear();
//                    }
//                }).update(i -> i.setChecked(triEnabled)).name("triEnabled").tooltip("triEnabled");

//                t.button(Icon.trelloSmall, sstylet, () -> {
//                    Core.settings.put("steal_map", !Core.settings.getBool("steal_map"));
//                }).update(i -> i.setChecked(Core.settings.getBool("steal_map"))).name("steal_map").tooltip("steal_map");
//
//
//                t.button(Icon.trelloSmall, sstylet, () -> {
//                    scanAttackMaps();
//                }).name("scanAttackMaps").tooltip("scanAttackMaps");
//
//                t.button(Icon.trelloSmall, sstylet, () -> {
//                    listMaps();
//                }).name("listMaps").tooltip("listMaps");
//
//                t.button(Icon.trelloSmall, sstylet, () -> {
//                    deleteAttackMaps();
//                }).name("deleteAttackMaps").tooltip("deleteAttackMaps");

//                if(Core.settings.getBool("OneLoliToRuleThemAll", false)){
//                    t.row();
//
//                    t.button(Icon.warningSmall, sstylet, () -> {
//                        Core.settings.put("fd-ai", !Core.settings.getBool("fd-ai"));
//                    }).update(i -> i.setChecked(Core.settings.getBool("fd-ai", false))).name("fd-ai").tooltip("fd-ai");
//
//                    t.button(Icon.warningSmall, sstylet, () -> {
//                        Core.settings.put("fd-ai-foos", !Core.settings.getBool("fd-ai-foos"));
//                    }).update(i -> i.setChecked(Core.settings.getBool("fd-ai-foos", false))).name("fd-ai-foos").tooltip("fd-ai-foos");
//
//                    t.button(Icon.trelloSmall, sstyle, () -> {
//                        Vars.ui.ai.getSettings().show();
//                    }).name("ai-settings").tooltip("ai-settings");
//                }

                t.row();

                t.button(Icon.craftingSmall, sstylet, () -> {
                    AutoTransfer.enabled = !AutoTransfer.enabled;
                    new Toast(1).add(bundle.get("client.autotransfer") + ": " + bundle.get(AutoTransfer.enabled ? "mod.enabled" : "mod.disabled"));
                    Core.settings.put("autotransfer", !settings.getBool("autotransfer"));
                }).update(i -> i.setChecked(settings.getBool("autotransfer"))).name("autotransfer").tooltip("autotransfer");

                t.button(Icon.turretSmall, sstylet, () -> {
                            // ГЛАВНОЕ: добавляем "!" перед получением значения
                            boolean val = !Core.settings.getBool("autotransfer-t-turrets", false);
                            Core.settings.put("autotransfer-t-turrets", val);
                            AutoTransfer.Settings.setTargetTurrets(val);
                        }).update(b -> b.setChecked(Core.settings.getBool("autotransfer-t-turrets", false)))
                        .tooltip("autotransfer-t-turrets");

                t.button(Icon.productionSmall, sstylet, () -> {
                            boolean val = !Core.settings.getBool("autotransfer-t-prod", false);
                            Core.settings.put("autotransfer-t-prod", val);
                            AutoTransfer.Settings.setTargetProduction(val);
                        }).update(b -> b.setChecked(Core.settings.getBool("autotransfer-t-prod", false)))
                        .tooltip("autotransfer-t-prod");

                t.button(Icon.unitsSmall, sstylet, () -> {
                            boolean val = !Core.settings.getBool("autotransfer-t-units", false);
                            Core.settings.put("autotransfer-t-units", val);
                            AutoTransfer.Settings.setTargetUnitFactories(val);
                        }).update(b -> b.setChecked(Core.settings.getBool("autotransfer-t-units", false)))
                        .tooltip("autotransfer-t-units");

                t.button(Icon.uploadSmall, sstylet, () -> {
                            boolean val = !Core.settings.getBool("autotransfer-t-recons", false);
                            Core.settings.put("autotransfer-t-recons", val);
                            AutoTransfer.Settings.setTargetReconstructors(val);
                        }).update(b -> b.setChecked(Core.settings.getBool("autotransfer-t-recons", false)))
                        .tooltip("autotransfer-t-recons");


                t.row();

                t.button(Icon.diagonalSmall, sstylet, () -> {
                    if(!settings.getBool("afkmode")){
                        eneblemining = true;
                        startmining();
                    } else {Navigation.stopFollowing();}
                    settings.put("afkmode", !settings.getBool("afkmode"));
                    new Toast(1).add(bundle.get("setting.afkmode.name") + ": " + bundle.get((settings.getBool("afkmode") ? "mod.enabled" : "mod.disabled")));
                }).update(i -> i.setChecked(settings.getBool("afkmode"))).name("AFK").tooltip("AFK");

                t.button(Icon.cancelSmall, sstylet, () -> {
                    settings.put("placeSchematicWithCleanup", !settings.getBool("placeSchematicWithCleanup"));
                }).update(i -> i.setChecked(settings.getBool("placeSchematicWithCleanup"))).name("placeSchematicWithCleanup").tooltip("placeSchematicWithCleanup");

//                t.button(Icon.trelloSmall, sstylet, () -> {
//                    settings.put("mobilegayming", !settings.getBool("mobilegayming"));
//                }).update(i -> i.setChecked(settings.getBool("mobilegayming"))).name("mobilegayming").tooltip("mobilegayming");

                t.button(Icon.chatSmall, sstylet, () -> {
                    settings.put("unitatchat", !settings.getBool("unitatchat"));
                }).update(i -> i.setChecked(settings.getBool("unitatchat"))).name("unitatchat").tooltip("unitatchat");


            }).padTop(Core.settings.getInt("yoffssetfdpamel",  -200) * 1f);
        });
    }
/*
    private void autoAssignMiningUnitsEqually() {
        if (player.unit() == null) return;
        Building core = player.team().core();
        if (core == null || player.unit() == null) return;

        // --- Проверка режима починки ---
        boolean needsRepairNearCore = false;

        if(autoHealMegas){
            for(Building ncore : player.team().cores()){
                if(ncore == null) return;
                Building nearest = Units.findDamagedTile(player.team(), ncore.x, ncore.y);
                if(nearest != null && nearest.dst(ncore)/8f < autoHealDist){
                    //Log.info("Core at ( @, @) at dist: ", ncore.x()/8, ncore.y()/8);
                    //Log.info("Nearest : @ at ( @, @) at dist: @ when dist: @ ", nearest, nearest.x()/8, nearest.y()/8, nearest.dst(ncore), autoHealDist);
                    needsRepairNearCore = true;
                    break;
                }
            }
        }
        int megaCounter = 0; // Счетчик для разделения Мег пополам


        int capacity = core.core().storageCapacity;


        // 1. Считаем веса ресурсов (спрос ядра)
        Item[] items = {Items.copper, Items.lead, Items.titanium, Items.sand, Items.coal, Items.scrap};
        boolean[] flags = {minecopper, minelead, minetitan, minesand, minecoal, minescrap};
        ObjectMap<Item, Float> itemWeights = new ObjectMap<>();
        Seq<Item> allEnabled = new Seq<>();
        float totalWeight = 0;

        for(int i = 0; i < items.length; i++){
            if(!flags[i]) continue;
            Item it = items[i];
            allEnabled.add(it);
            float progress = (float)core.items.get(it) / capacity;
            float weight = Math.max(0.05f, 1.0f - progress);
            if(progress < 0.1f) weight *= 5f;
            itemWeights.put(it, weight);
            totalWeight += weight;
        }
        if(allEnabled.isEmpty() || totalWeight <= 0) return;

        ObjectMap<Item, IntSeq> toBatchSend = new ObjectMap<>();
        ObjectMap<UnitType, ObjectMap<Item, Integer>> detailedStats = new ObjectMap<>();

        // ============================================================
        // 2.5. ГЛОБАЛЬНОЕ ОПРЕДЕЛЕНИЕ КРИЗИСА (ВЫНЕСЕНО ЗА ЦИКЛ)
        // ============================================================

        // Пороги
        final float CRITICAL_CORE_THRESHOLD = crisisThreshold / 2f; // для меди/свинца/титана
        final float NORMAL_CRISIS_THRESHOLD = crisisThreshold; // для остальных

        Seq<Item> globalCrisisItems = new Seq<>();
        boolean isCriticalCoreCrisis = false;

        // --- УРОВЕНЬ 1: Проверка критических ресурсов (медь, свинец, титан) ---
        Item[] coreResources = {Items.copper, Items.lead, Items.titanium};
        for(Item it : coreResources) {
            if(!allEnabled.contains(it)) continue; // Пропускаем, если ресурс выключен

            float progress = (float)core.items.get(it) / capacity;
            if(progress < CRITICAL_CORE_THRESHOLD) {
                globalCrisisItems.add(it);
                isCriticalCoreCrisis = true;
            }
        }

        // --- УРОВЕНЬ 2: Если критического кризиса нет, проверяем все ресурсы ---
        if(!isCriticalCoreCrisis) {
            for(Item it : allEnabled) {
                float progress = (float)core.items.get(it) / capacity;
                if(progress < NORMAL_CRISIS_THRESHOLD) {
                    globalCrisisItems.add(it);
                }
            }
        }

        boolean isGlobalCrisis = !globalCrisisItems.isEmpty();

        // 2. Группируем юнитов по типам
        ObjectMap<UnitType, Seq<Unit>> unitGroups = new ObjectMap<>();
        for(Unit u : Groups.unit){
            if(u.team != player.team() || !u.isCommandable()) continue;
            if(u.type == UnitTypes.mono && !mineMonos) continue;
            if(u.type == UnitTypes.poly && !minePolys) continue;
            //if(u.type == UnitTypes.mega && !mineMegas) continue;
            if(u.type == UnitTypes.pulsar && !minePulss) continue;
            if(u.type == UnitTypes.quasar && !mineQuazs) continue;

            if(u.type == UnitTypes.mega){
                if(!mineMegas) continue;

                megaCounter++;

                if(autoHealMegas && needsRepairNearCore && (!isGlobalCrisis || megaCounter % 2 == 0)){

                    if(u.controller() instanceof CommandAI cai && cai.command != UnitCommand.repairCommand){
                        Call.setUnitCommand(player, new int[]{u.id}, UnitCommand.repairCommand);
                    }

                    continue; // Отправляем хилить, в майнинг не пускаем
                }
            }

            if(u.type.mineTier > 0){
                if(!unitGroups.containsKey(u.type)) unitGroups.put(u.type, new Seq<>());
                unitGroups.get(u.type).add(u);
            }
        }
        if(unitGroups.isEmpty()) return;

        // 3. ОБРАБОТКА КАЖДОЙ ГРУППЫ (Моно, Мега и т.д.)
        for(var entry : unitGroups.entries()){
            UnitType type = entry.key;
            Seq<Unit> units = entry.value;
            if(!detailedStats.containsKey(type)) detailedStats.put(type, new ObjectMap<>());

            // Список того, что этот тип может копать
            Seq<Item> possible = allEnabled.select(it -> type.mineTier >= it.hardness);
            if(possible.isEmpty()) continue;

            // --- ЛОГИКА КРИЗИСА ДЛЯ ГРУППЫ ---
            Seq<Item> targets = possible; // По умолчанию целимся во всё доступное
            boolean isCrisisMode = false;

            if(isGlobalCrisis) {
                // Если в мире кризис, проверяем, можем ли мы помочь
                Seq<Item> myCrisisTargets = new Seq<>();
                for(Item it : globalCrisisItems) {
                    if(possible.contains(it)) {
                        myCrisisTargets.add(it);
                    }
                }

                if(!myCrisisTargets.isEmpty()) {
                    // МЫ МОЖЕМ ПОМОЧЬ! Включаем кризис-режим
                    targets = myCrisisTargets;
                    isCrisisMode = true;
                }
                // Если myCrisisTargets пуст — работаем в обычном режиме
            }

            // Рассчитываем целевое количество юнитов для каждого ресурса (КВОТЫ)
            ObjectMap<Item, Integer> quotas = new ObjectMap<>();
            int assignedCount = 0;

            // СЧИТАЕМ ВЕСА ТОЛЬКО ДЛЯ ВЫБРАННЫХ РЕСУРСОВ (targets)
            float currentTotalWeight = 0;
            for(Item it : targets) {
                currentTotalWeight += itemWeights.get(it, 0f);
            }

            if(currentTotalWeight <= 0) continue; // Защита от деления на 0

            for(Item it : possible) {
                int target;

                if(isCrisisMode) {
                    // В КРИЗИСЕ (только для тех, кто может помочь):
                    if(!targets.contains(it)) {
                        target = 0;
                    } else {
                        // Равномерное распределение + приоритет самым пустым
                        int baseShare = units.size / targets.size;
                        int remainder = units.size % targets.size;
                        int index = targets.indexOf(it);

                        target = baseShare + (index < remainder ? 1 : 0);

                        // Бонус для критически пустых (<10%)
                        float progress = (float)core.items.get(it) / capacity;
                        if(progress < 0.1f) target += 1;
                    }
                } else {
                    // В ОБЫЧНОМ РЕЖИМЕ: пропорции + minUnitsPerResource
                    int baseTarget = Math.round((itemWeights.get(it, 0f) / currentTotalWeight) * units.size);
                    target = Math.max(minUnitsPerResource, baseTarget);
                }

                quotas.put(it, target);
                assignedCount += target;
            }

            // 5. КОРРЕКТИРОВКА (БАЛАНСИРОВКА)
            while(assignedCount > units.size) {
                Item toReduce = possible.max(it -> {
                    int q = quotas.get(it, 0);
                    if(q <= 0) return -1f;
                    return (float)q / itemWeights.get(it, 0.05f);
                });

                if(toReduce != null) {
                    quotas.put(toReduce, quotas.get(toReduce, 0) - 1);
                    assignedCount--;
                } else break;
            }

            while(assignedCount < units.size) {
                Item toBoost = targets.max(it -> itemWeights.get(it, 0f));
                if(toBoost != null) {
                    quotas.put(toBoost, quotas.get(toBoost, 0) + 1);
                    assignedCount++;
                } else break;
            }
            // --- КОНЕЦ РАСЧЁТА КВОТ ---

            // --- ЛОГИКА "ЛИПКОСТИ" ---
            Seq<Unit> unassignedUnits = new Seq<>();

            // ШАГ 1: Оставляем тех, кто уже на правильном месте
            for(Unit u : units) {
                Item currentItem = null;
                if(u.controller() instanceof CommandAI cai && cai.command == UnitCommand.mineCommand) {
                    for(Item it : possible) {
                        if(cai.hasStance(ItemUnitStance.getByItem(it))) {
                            currentItem = it;
                            break;
                        }
                    }
                }

                if(currentItem != null && quotas.get(currentItem, 0) > 0) {
                    quotas.put(currentItem, quotas.get(currentItem, 0) - 1);
                    detailedStats.get(type).put(currentItem, detailedStats.get(type).get(currentItem, 0) + 1);
                } else {
                    unassignedUnits.add(u);
                }
            }

            // ШАГ 2: Распределяем оставшихся по свободным квотам
            for(Unit u : unassignedUnits) {
                Item bestTarget = possible.max(it -> quotas.get(it, 0));

                if(bestTarget != null && quotas.get(bestTarget, 0) > 0) {
                    quotas.put(bestTarget, quotas.get(bestTarget, 0) - 1);
                    detailedStats.get(type).put(bestTarget, detailedStats.get(type).get(bestTarget, 0) + 1);

                    if(!toBatchSend.containsKey(bestTarget)) toBatchSend.put(bestTarget, new IntSeq());
                    toBatchSend.get(bestTarget).add(u.id);
                } else {
                    Item fallback = possible.max(it -> itemWeights.get(it, 0f));
                    detailedStats.get(type).put(fallback, detailedStats.get(type).get(fallback, 0) + 1);

                    if(!(u.controller() instanceof CommandAI cai && cai.command == UnitCommand.mineCommand && cai.hasStance(ItemUnitStance.getByItem(fallback)))) {
                        if(!toBatchSend.containsKey(fallback)) toBatchSend.put(fallback, new IntSeq());
                        toBatchSend.get(fallback).add(u.id);
                    }
                }
            }
        }

        // 4. Отправка пакетов
        for(var entry : toBatchSend.entries()){
            int[] ids = entry.value.toArray();
            Call.setUnitCommand(player, ids, UnitCommand.mineCommand);
            Call.setUnitStance(player, ids, UnitStance.mineAuto, false);
            Call.setUnitStance(player, ids, ItemUnitStance.getByItem(entry.key), true);
        }

        // 5. Лог с деталями кризиса
        if(toBatchSend.size > 0) {
            StringBuilder crisisLog = new StringBuilder();
            if(isGlobalCrisis) {
                crisisLog.append("[");
                for(Item it : globalCrisisItems) {
                    int amount = core.items.get(it);
                    float pct = (float)amount / capacity * 100f;
                    crisisLog.append(it.localizedName)
                            .append(":")
                            .append(amount)
                            .append("(")
                            .append(Mathf.floor(pct))
                            .append("%),");
                }
                if(crisisLog.length() > 1) crisisLog.setLength(crisisLog.length() - 1); // убрать последнюю запятую
                crisisLog.append("]");
            } else {
                crisisLog.append("NO");
            }

            //Log.info("--- SAM Mining [Min:@, Crisis:@] Shifting @ units ---", minUnitsPerResource, crisisLog.toString(), toBatchSend.size);

            for(var entry : detailedStats.entries()){
                StringBuilder sb = new StringBuilder(" > " + entry.key.localizedName + ": [");
                for(var itemsMap : entry.value.entries()) sb.append(itemsMap.key.localizedName).append(": ").append(itemsMap.value).append(" | ");
                if(sb.length() > 5) sb.setLength(sb.length() - 3);
                //Log.info(sb.append("]").toString());
            }
        }
    }
*/
    public void updateTriControl() {
        if (!triEnabled || !Vars.state.isGame() || Vars.player.unit() == null) return;
        if(triUnitTypeIndex < 0 || triUnitTypeIndex >= sortedUnitTypes.size) return;

        UnitType currentType = sortedUnitTypes.get(triUnitTypeIndex);

        // 1. Очистка и поиск юнитов
        followers.removeAll(u -> !u.isValid() || u.team != Vars.player.team() || u.type != currentType);

        if (followers.size < triUnitCount) {
            Groups.unit.each(u -> {
                if (followers.size < triUnitCount && u.type == currentType && u.team == Vars.player.team() && !followers.contains(u)) {
                    followers.add(u);
                }
            });
        }
        if (followers.size > triUnitCount) followers.truncate(triUnitCount);

        // 2. Команды (раз в 5 тиков для оптимизации сетевого трафика)
        syncTimer++;
        if (syncTimer % 30 != 0) return;

        float baseRotation = Time.time * triRotSpeed;

        for (int i = 0; i < followers.size; i++) {
            Unit unit = followers.get(i);

            // --- ЛОГИКА АТАКИ ---
            // Ищем ближайшего врага в радиусе обзора юнита (или фиксированном, напр. 250 пикселей)
            float range = unit.range();
            Unit target = Units.closestEnemy(unit.team, unit.x, unit.y, range, u ->
                    // Передаем два аргумента: targetAir и targetGround
                    u.checkTarget(unit.type.targetAir, unit.type.targetGround)
            );

            if (target != null) {
                // Атака цели
                Call.commandUnits(Vars.player, new int[]{unit.id}, null, target, new Vec2(target.x, target.y), false, true);
                continue;
            }

            // --- ЛОГИКА ТРЕУГОЛЬНИКА (если врагов нет) ---
            float progress = (float) i / Math.max(1, followers.size);
            float sideProgress = (progress * 3f) % 1f;
            int side = Mathf.floor(progress * 3f);

            float a1 = baseRotation + (side * 120f);
            float a2 = baseRotation + ((side + 1) * 120f);

            float x1 = Vars.player.x + Mathf.cosDeg(a1) * triSize;
            float y1 = Vars.player.y + Mathf.sinDeg(a1) * triSize;
            float x2 = Vars.player.x + Mathf.cosDeg(a2) * triSize;
            float y2 = Vars.player.y + Mathf.sinDeg(a2) * triSize;

            float tx = Mathf.lerp(x1, x2, sideProgress);
            float ty = Mathf.lerp(y1, y2, sideProgress);

            Call.commandUnits(Vars.player, new int[]{unit.id}, null, null, new Vec2(tx, ty), false, true);
        }
    }


    //Копание всех всем
    private void autoAssignMiningUnits() {
        if (player.unit() == null) return;

        // Списки ID юнитов по типам
        IntSeq t1Ids = new IntSeq();
        IntSeq t2Ids = new IntSeq();
        IntSeq t3Ids = new IntSeq();

        // 1. Собираем всех юнитов в группы по типам
        for (Unit u : Groups.unit) {
            if (u.team != player.team() || !u.isCommandable()) continue;

            if (u.type == UnitTypes.mono) t1Ids.add(u.id);
            else if (u.type == UnitTypes.poly || u.type == UnitTypes.pulsar) t2Ids.add(u.id);
            else if (u.type == UnitTypes.mega || u.type == UnitTypes.quasar) t3Ids.add(u.id);
        }

        // 2. Определяем наборы ресурсов для активации
        Item[] t1Targets = {Items.copper, Items.lead, Items.sand};
        Item[] t2Targets = {Items.copper, Items.lead, Items.sand, Items.coal};
        Item[] t3Targets = {Items.copper, Items.lead, Items.sand, Items.coal, Items.titanium};

        // 3. Отдаем приказы
        assignGroup(t1Ids, t1Targets);
        assignGroup(t2Ids, t2Targets);
        assignGroup(t3Ids, t3Targets);
    }

    private void assignGroup(IntSeq ids, Item[] items) {
        if (ids.isEmpty()) return;
        int[] rawIds = ids.toArray();

        // А. Переводим в режим добычи
        Call.setUnitCommand(player, rawIds, UnitCommand.mineCommand);

        // Б. Выключаем режим "Авто" , так как она мешает выбору конкретных ресурсов
        Call.setUnitStance(player, rawIds, UnitStance.mineAuto, false);

        // В. Включаем КАЖДЫЙ нужный ресурс по очереди
        for (Item item : items) {
            UnitStance stance = ItemUnitStance.getByItem(item);
            if (stance != null) {
                Call.setUnitStance(player, rawIds, stance, true);
            }
        }
    }

    private void checkspawns() {
        if(!state.hasSpawns()) return;

        StringBuilder sb = new StringBuilder();
        sb.append(": ");
        int num = 0;

        for(Tile tile: spawner.getSpawns()){
            sb.append("(").append(Mathf.ceil(tile.x)).append(",").append(Mathf.ceil(tile.y)).append(");");
            num++;
        }

        if(sb.length() > 2){
            String uspawns = "Spawns(" + num + ")" + sb.toString();

            if ((max_length != 0) && (uspawns.length() >= max_length)) {
                uspawns = uspawns.substring(0, max_length);
            }

            if(settings.getBool("unitatchat")){
                Call.sendChatMessage(uspawns);
            } else {
                ChatFragment.ChatMessage msg = ui.chatfrag.addMessage(uspawns, null, null, "", uspawns);
                NetClient.findCoords(msg);
            }
        }
    }

    public static void startmining() {
        if(player.team().data().core() == null) return;
        currentfollowmode = 1;
        Navigation.follow( new MinePath(itemtomine, player.team().data().core().storageCapacity), true);
    }

    private void checkvoids() {
        Threads.daemon(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append(":");

            for(Tile tile : world.tiles) {
                if (tile.block() instanceof PowerVoid) { sb.append("(").append(tile.x).append(",").append(tile.y).append(");"); }
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;

                if(sb.length() > 1) {
                    String ucont = sb.toString();
                    String prefix = "[#fa]Power Voids:[white] ";
                    String fullMessage = prefix + ucont;

                    if ((max_length != 0) && (fullMessage.length() >= max_length)) {
                        fullMessage = fullMessage.substring(0, max_length);
                    }

                    if(Core.settings.getBool("unitatchat")){
                        if(state.rules.pvp) {
                            Call.sendChatMessage("/t " + fullMessage);
                        } else {
                            Call.sendChatMessage(fullMessage);
                        }
                    } else {
                        if (fullMessage.length() > 1000) fullMessage = fullMessage.substring(0, 1000) + "... [gray](truncated)";
                        ChatFragment.ChatMessage msg = ui.chatfrag.addMessage(fullMessage, null, null, "", fullMessage);
                        NetClient.findCoords(msg);
                    }
                }
            });
        });
    }

    private void checksources() {
        Threads.daemon(() -> {
            if (world.tiles == null) return;
            ObjectMap<Team, StringBuilder> teamBuilders = new ObjectMap<>();
            for (Team t : Team.all) teamBuilders.put(t, new StringBuilder().append(":"));

            for (Tile tile : world.tiles) {
                if (tile.build != null && tile.build.tile == tile) {
                    Block block = tile.build.block;
                    if (block instanceof ItemSource || block instanceof PowerSource || block instanceof LiquidSource) {
                        Team team = tile.build.team;
                        StringBuilder sb = teamBuilders.get(team);
                        if (sb != null) sb.append(Fonts.getUnicodeStr(block.name)).append("(").append(tile.x).append(",").append(tile.y).append(");");
                    }
                }
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;
                for (ObjectMap.Entry<Team, StringBuilder> entry : teamBuilders.entries()) {
                    Team cteam = entry.key;
                    StringBuilder sb = entry.value;

                    if (sb.length() > 1) {
                        String content = sb.toString();
                        boolean atChat = Core.settings.getBool("unitatchat");
                        String fullMessage = "[#" + cteam.color + "]" + cteam.name + (atChat ? "[white]" : "[]") + content;

                        if (atChat) {
                            if (max_length != 0 && fullMessage.length() >= max_length) fullMessage = fullMessage.substring(0, max_length);
                            if (state.rules.pvp) Call.sendChatMessage("/t " + fullMessage); else Call.sendChatMessage(fullMessage);
                        } else {
                            if (fullMessage.length() > 1000) fullMessage = fullMessage.substring(0, 1000) + "... [gray](truncated)";
                            ChatFragment.ChatMessage msg = ui.chatfrag.addMessage(fullMessage, null, null, "", fullMessage);
                            NetClient.findCoords(msg);
                        }
                    }
                }
            });
        });
    }

    private void checkworldprocc() {
        Threads.daemon(() -> {
            ObjectMap<Team, StringBuilder> teamBuilders = new ObjectMap<>();
            for (Team t : Team.all) teamBuilders.put(t, new StringBuilder().append(":"));

            for(Tile tile : world.tiles) {
                if(tile.build != null && tile.build.tile == tile && tile.build.block == Blocks.worldProcessor) {
                    Team team = tile.build.team;
                    StringBuilder sb = teamBuilders.get(team);
                    if (sb != null) sb.append(Fonts.getUnicodeStr(Blocks.worldProcessor.name)).append("(").append(tile.x).append(", ").append(tile.y).append("); ");
                }
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;
                for(ObjectMap.Entry<Team, StringBuilder> entry : teamBuilders.entries()) {
                    StringBuilder sb = entry.value;
                    if(sb.length() > 1) {
                        Team cteam = entry.key;
                        boolean atChat = Core.settings.getBool("unitatchat");
                        String fullMessage = "[#" + cteam.color + "]" + cteam.name + (atChat ? "[white]" : "[]") + sb.toString();

                        if (atChat) {
                            if (max_length != 0 && fullMessage.length() >= max_length) fullMessage = fullMessage.substring(0, max_length);
                            if (state.rules.pvp) Call.sendChatMessage("/t " + fullMessage); else Call.sendChatMessage(fullMessage);
                        } else {
                            if (fullMessage.length() > 1000) fullMessage = fullMessage.substring(0, 1000) + "... [gray](truncated)";
                            ChatFragment.ChatMessage msg = ui.chatfrag.addMessage(fullMessage, null, null, "", fullMessage);
                            NetClient.findCoords(msg);
                        }
                    }
                }
            });
        });
    }

    private void checkcores() {
        Threads.daemon(() -> {
            ObjectMap<Team, StringBuilder> teamBuilders = new ObjectMap<>();
            ObjectMap<Team, Integer> teamCounters = new ObjectMap<>();

            for (Team t : Team.all) {
                teamBuilders.put(t, new StringBuilder().append(":"));
                teamCounters.put(t, 0);
            }

            for(Tile tile : world.tiles) {
                if(tile.build instanceof CoreBlock.CoreBuild && tile.build.tile == tile) {
                    Team team = tile.build.team;
                    StringBuilder sb = teamBuilders.get(team);

                    if (sb != null) {
                        sb.append(Fonts.getUnicodeStr(tile.build.block.name))
                                .append("(")
                                .append(tile.x)
                                .append(", ")
                                .append(tile.y)
                                .append("); ");

                        teamCounters.put(team, teamCounters.get(team) + 1);
                    }
                }
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;

                for(ObjectMap.Entry<Team, StringBuilder> entry : teamBuilders.entries()) {
                    StringBuilder sb = entry.value;
                    if(sb.length() > 1) {
                        Team cteam = entry.key;
                        int count = teamCounters.get(cteam);
                        String content = sb.toString();
                        String fullMessage;

                        if(Core.settings.getBool("unitatchat")){
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "(" + count + ")[white]" + content;

                            if ((max_length != 0) && (fullMessage.length() >= max_length)) {
                                fullMessage = fullMessage.substring(0, max_length);
                            }

                            if(state.rules.pvp) Call.sendChatMessage("/t " + fullMessage);
                            else Call.sendChatMessage(fullMessage);
                        } else {
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "(" + count + ")[]" + content;
                            if (fullMessage.length() > 1000) fullMessage = fullMessage.substring(0, 1000) + "... [gray](truncated)";
                            ChatFragment.ChatMessage msg = ui.chatfrag.addMessage(fullMessage, null, null, "", fullMessage);
                            NetClient.findCoords(msg);
                        }
                    }
                }
            });
        });
    }

    private void checkunits() {
        Threads.daemon(() -> {
            ObjectMap<Team, ObjectIntMap<UnitType>> teamUnitMap = new ObjectMap<>();

            for (Unit unit : Groups.unit) {
                if (unit == null || unit.type == null) continue;

                Team team = unit.team;
                if (!teamUnitMap.containsKey(team)) {
                    teamUnitMap.put(team, new ObjectIntMap<>());
                }

                ObjectIntMap<UnitType> counts = teamUnitMap.get(team);
                counts.put(unit.type, counts.get(unit.type, 0) + 1);
            }

            Seq<String> messagesToSend = new Seq<>();
            StringBuilder currentBatch = new StringBuilder();

            for (Team team : Team.all) {
                if (!teamUnitMap.containsKey(team)) continue;

                ObjectIntMap<UnitType> counts = teamUnitMap.get(team);
                if (counts.size == 0) continue;

                StringBuilder teamSb = new StringBuilder();
                String color = Core.settings.getBool("unitatchat") ? "[#" + team.color + "]" : "[#" + team.color + "]";
                String reset = Core.settings.getBool("unitatchat") ? "[white]" : "[]";

                teamSb.append(color).append(team.name).append(reset).append(":");

                for (ObjectIntMap.Entry<UnitType> entry : counts.entries()) {
                    teamSb.append(Fonts.getUnicodeStr(entry.key.name))
                            .append(entry.value)
                            .append(";");
                }

                String teamString = teamSb.toString();

                if (currentBatch.length() + teamString.length() + 3 < max_length) {
                    if (currentBatch.length() > 0) currentBatch.append(" | ");
                    currentBatch.append(teamString);
                } else {
                    if (currentBatch.length() > 0) {
                        messagesToSend.add(currentBatch.toString());
                        currentBatch.setLength(0);
                    }

                    if (teamString.length() >= max_length) {
                        messagesToSend.add(teamString.substring(0, Math.min(teamString.length(), max_length)));
                    } else {
                        currentBatch.append(teamString);
                    }
                }
            }

            if (currentBatch.length() > 0) {
                messagesToSend.add(currentBatch.toString());
            }

            Core.app.post(() -> {
                if (player == null || messagesToSend.isEmpty()) return;

                for (int i = 0; i < messagesToSend.size; i++) {
                    String msg = messagesToSend.get(i);

                    Timer.schedule(() -> {
                        if (player == null) return;

                        if (Core.settings.getBool("unitatchat")) {
                            if (state.rules.pvp) Call.sendChatMessage("/t " + msg);
                            else Call.sendChatMessage(msg);
                        } else {
                            ChatFragment.ChatMessage m = ui.chatfrag.addMessage(msg, null, null, "", msg);
                            NetClient.findCoords(m);
                        }
                    }, i * 1.1f);
                }
            });
        });
    }
    private void oldcheckunits() {
        Threads.daemon(() -> {
            Seq<String> messagesToSend = new Seq<>();
            StringBuilder currentBatch = new StringBuilder();

            for(Team team : Team.all) {
                if (team.data().unitCount == 0) continue;

                StringBuilder teamSb = new StringBuilder();
                boolean hasUnits = false;

                if(Core.settings.getBool("unitatchat")){
                    teamSb.append("[#").append(team.color).append("]").append(team.name).append("[white]:");
                } else {
                    teamSb.append("[#").append(team.color).append("]").append(team.name).append("[]:");
                }

                for(UnitType type : content.units()) {
                    int count = team.data().countType(type);
                    if(count > 0) {
                        teamSb.append(Fonts.getUnicodeStr(type.name)).append(count).append(";");
                        hasUnits = true;
                    }
                }

                if(!hasUnits) continue;

                String teamString = teamSb.toString();

                if (currentBatch.length() + teamString.length() < max_length) {
                    if (currentBatch.length() > 0) currentBatch.append(" | ");
                    currentBatch.append(teamString);
                }
                else {
                    if (currentBatch.length() > 0) {
                        messagesToSend.add(currentBatch.toString());
                        currentBatch.setLength(0);
                    }

                    if (teamString.length() >= max_length && max_length != 0) {
                        messagesToSend.add(teamString.substring(0, max_length));
                    } else {
                        currentBatch.append(teamString);
                    }
                }
            }

            if (currentBatch.length() > 0) {
                messagesToSend.add(currentBatch.toString());
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;
                if (messagesToSend.isEmpty()) return;

                for (int i = 0; i < messagesToSend.size; i++) {
                    String msg = messagesToSend.get(i);

                    Timer.schedule(() -> {
                        if (player == null) return;

                        if(Core.settings.getBool("unitatchat")){
                            if(state.rules.pvp) {
                                Call.sendChatMessage("/t " + msg);
                            } else {
                                Call.sendChatMessage(msg);
                            }
                        } else {
                            ChatFragment.ChatMessage m = ui.chatfrag.addMessage(msg, null, null, "", msg);
                            NetClient.findCoords(m);
                        }
                    }, i * 1.1f);
                }
            });
        });
    }

    private static class EffState {
        float prevValue = 0;
        float currentValue = 0;
        float startTime = 0;
        float lastUpdate = 0;
    }

    private float getSmoothEfficiency(Building build) {
        float timerSeconds = 10f; // Уменьшим окно до 10 сек для отзывчивости
        float windowTicks = timerSeconds * 60f;
        float now = Time.time;

        EffState state = effStorage.get(build.id);

        if (state == null) {
            state = new EffState();
            state.startTime = now;
            state.lastUpdate = now;
            effStorage.put(build.id, state);
            return 0;
        }

        float passedTime = now - state.lastUpdate;
        float elapsedSinceStart = now - state.startTime;

        if (elapsedSinceStart > windowTicks) {
            state.prevValue = state.currentValue / windowTicks;
            state.currentValue = 0;
            state.startTime = now;
        }

        // ГЛАВНОЕ ИЗМЕНЕНИЕ: эффективность умножаем на скорость времени (Overdrive)
        // Для ускорителей берем просто их рабочее состояние
        float points = build.efficiency * build.timeScale();

        // Если это сам ускоритель, его timeScale всегда 1, но нам важно, работает ли он
        if(build instanceof mindustry.world.blocks.defense.OverdriveProjector.OverdriveBuild) {
            points = build.efficiency;
        }

        state.currentValue += passedTime * points;
        state.lastUpdate = now;

        float measurement = Math.min(1f, elapsedSinceStart / windowTicks);
        return (state.currentValue / windowTicks) + (state.prevValue * (1f - measurement));
    }
    public static void initTypes() {
        // Используем select вместо filter
        sortedUnitTypes = Vars.content.units().copy()
                .select(u -> !u.internal)
                .select(u -> !u.hidden)
                .sort(u -> u.health);
    }


    private void updatemineitems() { //Выбор руд
        if (player == null || player.unit() == null) return;

        if (minecopper){if(!itemtomine.contains(Items.copper)) itemtomine.add(Items.copper);} else {if(itemtomine.contains(Items.copper)){itemtomine.remove(Items.copper);}}
        if (minelead){if(!itemtomine.contains(Items.lead)) itemtomine.add(Items.lead);} else {if(itemtomine.contains(Items.lead)){itemtomine.remove(Items.lead);}}
        if (minesand){if(!itemtomine.contains(Items.sand)) itemtomine.add(Items.sand);} else {if(itemtomine.contains(Items.sand)){itemtomine.remove(Items.sand);}}
        if (minecoal){if(!itemtomine.contains(Items.coal)) itemtomine.add(Items.coal);} else {if(itemtomine.contains(Items.coal)){itemtomine.remove(Items.coal);}}
        if (minescrap){if(!itemtomine.contains(Items.scrap)) itemtomine.add(Items.scrap);} else {if(itemtomine.contains(Items.scrap)){itemtomine.remove(Items.scrap);}}
        if (minetitan){if(!itemtomine.contains(Items.titanium)) itemtomine.add(Items.titanium);} else {if(itemtomine.contains(Items.titanium)){itemtomine.remove(Items.titanium);}}
        if (mineBerylliumwall){if(!itemtomine.contains(Items.beryllium)) itemtomine.add(Items.beryllium);} else {if(itemtomine.contains(Items.beryllium)){itemtomine.remove(Items.beryllium);}}
        if (mineGraphiticwall){if(!itemtomine.contains(Items.graphite)) itemtomine.add(Items.graphite);} else {if(itemtomine.contains(Items.graphite)){itemtomine.remove(Items.graphite);}}

        if((player.unit().type == UnitTypes.evoke)||(player.unit().type == UnitTypes.incite)||(player.unit().type == UnitTypes.emanate)){
            if(itemtomine.contains(Items.copper)){itemtomine.remove(Items.copper);}
            if(itemtomine.contains(Items.lead)){itemtomine.remove(Items.lead);}
            if(itemtomine.contains(Items.sand)){itemtomine.remove(Items.sand);}
            if(itemtomine.contains(Items.coal)){itemtomine.remove(Items.coal);}
            if(itemtomine.contains(Items.scrap)){itemtomine.remove(Items.scrap);}
            if(itemtomine.contains(Items.titanium)){itemtomine.remove(Items.titanium);}
        }

    }
    private void drawBuildings() {
        if (!viewprogressunit && !viewprogresbuild && !viewEfficiency) return;

        float fontScale = 0.25f / Scl.scl(1.0f);
        Font font = Fonts.outline;

        font.setUseIntegerPositions(false);
        font.getData().setScale(fontScale);
        font.setColor(Color.white);

        for(Building bui : Groups.build) {
            if(!bui.within(Core.camera.position, Core.graphics.getWidth() / 1.5f)) continue;

            if (viewprogressunit) {             // --- ЛОГИКА ФАБРИК ---
                float prog = 0;
                if (bui instanceof UnitFactory.UnitFactoryBuild build) {
                    prog = build.fraction();
                } else if (bui instanceof Reconstructor.ReconstructorBuild buildr) {
                    prog = buildr.fraction();
                }

                if (prog > 0.0001f) {
                    drawBarAndText(bui.x, bui.y, bui.block.size * 4, bui.team.color, prog, true, font);
                }
            }

            if (viewprogresbuild && bui instanceof ConstructBuild entity) {        // --- ЛОГИКА СТРОИТЕЛЬСТВА  ---
                float prog = entity.progress;
                if (prog > 0.0001f && prog < 1f) {
                    drawTextOnly(bui.x, bui.y, prog, font);
                }
            }

            if (viewEfficiency) {
                boolean isProducer = bui.block.category == Category.production ||
                        bui.block.category == Category.crafting ||
                        bui.block.category == Category.units ||
                        bui instanceof mindustry.world.blocks.distribution.MassDriver.MassDriverBuild  ||
                        bui instanceof mindustry.world.blocks.production.Pump.PumpBuild  ||
                        bui instanceof mindustry.world.blocks.defense.OverdriveProjector.OverdriveBuild  ||
                        bui instanceof mindustry.world.blocks.power.PowerGenerator.GeneratorBuild;

                if (isProducer) {
                    float smoothEff = getSmoothEfficiency(bui);

                    if (smoothEff >= 0f) {
                        // Расчет цвета:
                        if (smoothEff < 0.5f) tmpCol.set(Color.red).lerp(Color.orange, smoothEff * 2f);
                        else if (smoothEff <= 1.02f) tmpCol.set(Color.orange).lerp(Color.green, (smoothEff - 0.5f) * 2f);
                        else tmpCol.set(Color.green).lerp(Color.cyan, Math.min(1f, (smoothEff - 1f) / 1.5f)); // Голубой для > 100%

                        Draw.z(Layer.darkness + 1);
                        sb.setLength(0);

                        // Если эффективность > 100%, подсвечиваем это
                        int val = Math.round(smoothEff * 100);
                        sb.append(val).append("%");

                        layout.setText(font, sb);
                        font.setColor(tmpCol);

                        // Отрисовка в центре блока
                        font.draw(sb, bui.x - layout.width / 2, bui.y + layout.height / 2);
                    }
                }
            }
        }

        font.getData().setScale(1f);
        Draw.reset();
    }

    private void drawUnits() {
        if (!viewunitshealth && !viewunitseffects) return;

        brokenFade = Mathf.lerpDelta(brokenFade, 1f, 0.1f);

        float fontScale = 0.25f / Scl.scl(1.0f);
        Font font = Fonts.outline;

        font.setUseIntegerPositions(false);
        font.getData().setScale(fontScale);

        for(Unit unit : Groups.unit) {
            if(!unit.isAdded() || !unit.within(Core.camera.position, Core.graphics.getWidth() / 1.5f)) continue;

            if (viewunitshealth && unit.health < unit.maxHealth) {
                float prog = unit.health / unit.maxHealth;
                if (prog > 0.0001f) {
                    tmpCol.set(Color.white).lerp(Color.black, 1f - prog);

                    drawBarAndText(unit.x, unit.y - (unit.hitSize * 3) + (unit.hitSize + 2), unit.hitSize, unit.team.color, prog, false, font);
                }
            }
            if (viewunitseffects) {
                Draw.alpha(0.90f * brokenFade);
                tempBits.clear();
                Bits applied = unit.statusBits();

                if(applied != null && !applied.isEmpty()){
                    int i = 0;
                    for(StatusEffect effect : content.statusEffects()){
                        if(applied.get(effect.id) && !effect.isHidden()){
                            Draw.rect(effect.uiIcon, unit.x + i * effect.uiIcon.width / 4f, unit.y);
                            i++;
                        }
                    }
                }
            }
        }

        font.getData().setScale(1f);
        Draw.reset();
    }

    private void drawBarAndText(float x, float y, float width, Color teamColor, float prog, boolean isFactory, Font font) {
        float yOffset = isFactory ? width + 2 : width + 2; // В оригинале было (hw + 2)

        Draw.z(Layer.darkness + 1);

        if(isFactory){
            Draw.color(Pal.darkerGray);
        } else {
            Draw.color(tmpCol);
        }

        Lines.stroke(4);
        Lines.line(x - width, y + yOffset, x - width + width * 2, y + yOffset);

        Draw.color(teamColor);
        Lines.stroke(2);
        Lines.line(x - width, y + yOffset, x - width + width * 2 * prog, y + yOffset);

        sb.setLength(0);
        sb.append((int)(prog * 100)).append("%");

        layout.setText(font, sb);
        font.setColor(Color.white);
        font.draw(sb, x - layout.width / 2, y + yOffset + layout.height / 2 + 6);
        Draw.reset();
    }

    private void drawTextOnly(float x, float y, float prog, Font font) {
        Draw.z(Layer.darkness + 1);

        sb.setLength(0);
        sb.append((int)(prog * 100)).append("%");

        layout.setText(font, sb);
        font.setColor(Color.white);
        font.draw(sb, x - layout.width / 2, y + layout.height / 2);

        Draw.reset();
    }
}