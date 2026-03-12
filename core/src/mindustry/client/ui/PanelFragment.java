package mindustry.client.ui;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
import arc.graphics.g2d.Lines;

import arc.math.Mathf;

import arc.scene.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.CommandHandler;
import arc.util.pooling.Pools;
import mindustry.*;
import mindustry.client.ClientVars;
import mindustry.client.fallen.FDAutoShoot;
import mindustry.client.navigation.BuildPath;
import mindustry.client.navigation.MinePath;
import mindustry.client.navigation.Navigation;
import mindustry.client.navigation.RepairPath;
import mindustry.client.utils.AutoTransfer;
import mindustry.content.*;
import mindustry.core.NetClient;
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
import arc.util.Threads;
import arc.util.Timer;
import arc.struct.Seq;


import static arc.Core.*;
import static mindustry.Vars.*;

public class PanelFragment extends Table{
    public Table fdpanel; //Создание интерфейса дял кнопок
    public static Seq<Item> itemtomine = new Seq<>(); //Создание выборки для копания
    private static boolean minecopper, minelead, minetitan, minesand, minecoal, minescrap;
    private static boolean mineBerylliumwall, mineGraphiticwall;
    private float brokenFade = 0f;
    public static int max_length = 146;

    private boolean eneblemining = false;
    private boolean playerbuild = false;
    private boolean viewunitshealth = false;
    private boolean viewunitseffects = false;
    private boolean viewprogressunit = false;
    private boolean viewprogresbuild  = false;

    public static int currentfollowmode = 0; // 1 - mine, 2 - build, 3 - heal
    public static int prevfollowmode = 0;
    public static boolean forcesavelogs = false;

    private static final GlyphLayout layout = new GlyphLayout();
    private static final StringBuilder sb = new StringBuilder();
    private static final Color tmpCol = new Color();
    private static final Bits tempBits = new Bits();

    public PanelFragment(){ //Основной класс

        Events.run(Trigger.draw, () -> { //Постоянный вызов прорисовки
            if(ui.hudfrag.shown) {
                drawBuildings();
                drawUnits();
            }
        });

        Events.on(WorldLoadEvent.class, e -> { //Ивент, срабатывающий при загрузке карты
            rebuild();
            minecopper = true; minelead = true; minetitan = true;
            mineBerylliumwall = true; mineGraphiticwall = true;
            //minesand = false; minecoal = false;
            minescrap = false;
            itemtomine.clear();
            updatemineitems();
        });
        Events.on(UnitControlEvent.class, e -> { //Проверка ресурсов при смене юнита
            updatemineitems();
        });
        Events.on(UnitChangeEvent.class, e -> { //Проверка ресурсов при смене юнита
            updatemineitems();
        });

        Events.run(Trigger.update, () -> { //currentfollowmode // 1 - mine, 2 - build, 3 - heal //переключения в режиме афк
            if (player == null || player.unit() == null) return;

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
            FDAutoShoot.update();
        });
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
        if (!viewprogressunit && !viewprogresbuild) return;

        float fontScale = 0.25f / Scl.scl(1.0f);
        Font font = Fonts.outline;

        font.setUseIntegerPositions(false);
        font.getData().setScale(fontScale);
        font.setColor(Color.white);

        for(Building bui : Groups.build) {
            if(!bui.within(Core.camera.position, Core.graphics.getWidth() / 1.5f)) continue;

            if (viewprogressunit) {
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

            if (viewprogresbuild && bui instanceof ConstructBuild entity) {
                float prog = entity.progress;
                if (prog > 0.0001f && prog < 1f) {
                    drawTextOnly(bui.x, bui.y, prog, font);
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
                t.defaults().size(Core.settings.getInt("buttonsizefdpamel") * 1f);
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
                    tb.defaults().size(Core.settings.getInt("buttonsizefdpamel") / 2f);
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
                    tb.defaults().size(Core.settings.getInt("buttonsizefdpamel") / 2f);

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

                t.button(Icon.distributionSmall, sstyle, () -> {
                    currentfollowmode = 3;
                    Navigation.follow(new RepairPath(), true);
                }).name("healer").tooltip("Heal");

                t.button(Icon.distributionSmall, sstyle, () -> {
                    currentfollowmode = 2;
                    Navigation.follow(new BuildPath("self"));
                }).name("builder").tooltip("Self builder");

                t.button(Icon.terminalSmall, sstyle, () -> {
                    eneblemining = !eneblemining;
                    startmining();
                }).name("miner").tooltip("Mine!");

                t.row();

                t.button(Icon.eyeOffSmall, sstyle, () -> {
                    Vars.enableLight = !Vars.enableLight;

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


                t.button(Icon.chatSmall, sstylet, () -> {
                    Core.settings.put("unitatchat", !Core.settings.getBool("unitatchat"));
                }).update(i -> i.setChecked(Core.settings.getBool("unitatchat"))).name("unitatchat").tooltip("unitatchat");

                t.row();

                t.button(Icon.eyeSmall, sstyle, this::checkunits).tooltip("Eye of Sauron: Units");

                t.button(Icon.eyeSmall, sstyle, this::checkcores).tooltip("Eye of Sauron: Cores");

                t.button(Icon.eyeSmall, sstyle, this::checkspawns).tooltip("Eye of Sauron: Spawns");

                t.button(Icon.eyeSmall, sstyle, this::checkvoids).tooltip("Eye of Sauron: Voids");

                t.button(Icon.eyeSmall, sstyle, this::checksources).tooltip("Eye of Sauron: Sources");

                t.row();

                t.button(Icon.refreshSmall, sstyle, () -> {
                    Call.sendChatMessage("/sync");
                }).name("ores").tooltip("/sync");

                t.button(Icon.hammerSmall, sstyle, () -> {
                    Call.sendChatMessage("/vote y");
                }).name("ores").tooltip("/vote y");

                t.button(Icon.itchioSmall, sstyle, () -> {
                    Call.sendChatMessage("/rtv");
                }).name("ores").tooltip("/rtv");

                t.button(Icon.eyeSmall, sstyle, this::checkworldprocc).tooltip("Eye of Sauron: World Processor");

                t.button(Icon.eyeSmall, sstylet, () -> {
                    viewunitseffects = !viewunitseffects;
                }).update(i -> i.setChecked(viewunitseffects)).name("viewunitseffects").tooltip("Unit Status Effects");

                t.row();

                t.button(Icon.diagonalSmall, sstylet, () -> {
                    if(!Core.settings.getBool("afkmode")){
                        eneblemining = true;
                        startmining();
                    } else {Navigation.stopFollowing();}
                    Core.settings.put("afkmode", !Core.settings.getBool("afkmode"));
                    new Toast(1).add(bundle.get("setting.afkmode.name") + ": " + bundle.get((settings.getBool("afkmode") ? "mod.enabled" : "mod.disabled")));
                }).update(i -> i.setChecked(Core.settings.getBool("afkmode"))).name("AFK").tooltip("AFK");

                t.button(Icon.starSmall, sstylet, () -> {
                    settings.put("smarttargeting", !settings.getBool("smarttargeting"));
                }).update(i -> i.setChecked(settings.getBool("smarttargeting"))).name("smarttargeting").tooltip("smarttargeting");

                t.button(Icon.cancelSmall, sstylet, () -> {
                    Core.settings.put("ignoreunit", !Core.settings.getBool("ignoreunit"));

                }).update(i -> i.setChecked(Core.settings.getBool("ignoreunit"))).name("ignoreunit").tooltip("ignoreunit");

                t.button(Icon.cancelSmall, sstylet, () -> {
                    Core.settings.put("ignoreheal", !Core.settings.getBool("ignoreheal"));
                }).update(i -> i.setChecked(Core.settings.getBool("ignoreheal"))).name("ignoreheal").tooltip("ignoreheal");
                t.button(Icon.craftingSmall, sstylet, () -> {
                    AutoTransfer.enabled ^= true;
                    new Toast(1).add(bundle.get("client.autotransfer") + ": " + bundle.get(AutoTransfer.enabled ? "mod.enabled" : "mod.disabled"));
                    Core.settings.put("autotransfer", !Core.settings.getBool("autotransfer"));
                }).update(i -> i.setChecked(Core.settings.getBool("autotransfer"))).name("autotransfer").tooltip("autotransfer");
                t.button(Icon.lineSmall, sstylet, () -> {
                    FDAutoShoot.viewUnitAim = !FDAutoShoot.viewUnitAim;
                }).update(i -> i.setChecked(FDAutoShoot.viewUnitAim)).name("vaim").tooltip("Player Unit Range & Real Aim");

                t.row();
                t.button(Icon.trelloSmall, sstylet, () -> {
                    Core.settings.put("mobilegayming", !Core.settings.getBool("mobilegayming"));
                }).update(i -> i.setChecked(Core.settings.getBool("mobilegayming"))).name("mobilegayming").tooltip("mobilegayming");

                t.button(Icon.powerSmall, sstylet, () -> {
                    String message = "!fixpower c";
                    CommandHandler.CommandResponse response = ClientVars.clientCommandHandler.handleMessage(message, player);
                }).update(i -> i.setChecked(Core.settings.getBool("fixpower"))).name("fixpower").tooltip("fixpower");


                t.button(Icon.unitsSmall, sstyle, () -> {
                    String message = "!uc " + UnitTypes.mega.localizedName;
                    ClientVars.clientCommandHandler.handleMessage(message, player);
                }).name("mega").tooltip("mega");

                t.button(Icon.fileTextSmall, sstyle, () -> {
                    String message = "!fixcode r";
                    CommandHandler.CommandResponse response = ClientVars.clientCommandHandler.handleMessage(message, player);
                }).name("fixcode").tooltip("fixcode");

                t.button(Icon.saveSmall, sstylet, () -> {
                    forcesavelogs = !forcesavelogs;
                }).update(i -> i.setChecked(forcesavelogs)).name("forcesavelogs").tooltip("Save logs then exit or sync");

            }).padTop(Core.settings.getInt("yoffssetfdpamel") * 1f);
        });
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

            for (Team t : Team.all) {
                teamBuilders.put(t, new StringBuilder().append(":"));
            }

            for (Tile tile : world.tiles) {
                if (tile.build == null) continue;

                Block block = tile.build.block;

                if (block instanceof ItemSource || block instanceof PowerSource || block instanceof LiquidSource) {

                    Team team = tile.build.team;
                    StringBuilder sb = teamBuilders.get(team);

                    if (sb == null) { sb = new StringBuilder().append(":"); teamBuilders.put(team, sb); }
                    sb.append(Fonts.getUnicodeStr(block.name)).append("(").append(tile.x).append(",").append(tile.y).append(");");
                }
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;

                for (ObjectMap.Entry<Team, StringBuilder> entry : teamBuilders.entries()) {
                    Team cteam = entry.key;
                    StringBuilder sb = entry.value;

                    if (sb.length() > 1) {
                        String content = sb.toString();
                        String fullMessage;

                        if (Core.settings.getBool("unitatchat")) {
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "[white]" + content;
                        } else {
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "[]" + content;
                        }

                        if ((max_length != 0) && (fullMessage.length() >= max_length)) {
                            fullMessage = fullMessage.substring(0, max_length);
                        }

                        if (Core.settings.getBool("unitatchat")) {
                            if (state.rules.pvp) {
                                Call.sendChatMessage("/t " + fullMessage);
                            } else {
                                Call.sendChatMessage(fullMessage);
                            }
                        } else {
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

            for (Team t : Team.all) {
                teamBuilders.put(t, new StringBuilder().append(":"));
            }

            for(Tile tile : world.tiles) {
                if(tile.build == null) continue;

                if(tile.build.block == Blocks.worldProcessor) {
                    Team team = tile.build.team;
                    StringBuilder sb = teamBuilders.get(team);

                    if (sb != null) {
                        sb.append(Fonts.getUnicodeStr(Blocks.worldProcessor.name)).append("(").append(tile.x).append(", ").append(tile.y).append("); "); }
                }
            }

            Core.app.post(() -> {
                if (player == null || player.unit() == null) return;

                for(ObjectMap.Entry<Team, StringBuilder> entry : teamBuilders.entries()) {
                    StringBuilder sb = entry.value;
                    if(sb.length() > 1) {
                        Team cteam = entry.key;
                        String content = sb.toString();
                        String fullMessage;

                        if(Core.settings.getBool("unitatchat")){
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "[white]" + content;
                        } else {
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "[]" + content;
                        }

                        if ((max_length != 0) && (fullMessage.length() >= max_length)) {
                            fullMessage = fullMessage.substring(0, max_length);
                        }

                        if(Core.settings.getBool("unitatchat")){
                            if(state.rules.pvp) { Call.sendChatMessage("/t " + fullMessage); }
                            else { Call.sendChatMessage(fullMessage); }
                        } else {
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
                if(tile.build == null) continue;

                if(tile.build instanceof CoreBlock.CoreBuild) {
                    Team team = tile.build.team;
                    StringBuilder sb = teamBuilders.get(team);

                    if (sb != null) {
                        sb.append(Fonts.getUnicodeStr(tile.build.block.name)).append("(").append(tile.x).append(", ").append(tile.y).append("); ");
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
                        } else {
                            fullMessage = "[#" + cteam.color + "]" + cteam.name + "(" + count + ")[]" + content;
                        }

                        if ((max_length != 0) && (fullMessage.length() >= max_length)) {
                            fullMessage = fullMessage.substring(0, max_length);
                        }

                        if(Core.settings.getBool("unitatchat")){
                            if(state.rules.pvp) { Call.sendChatMessage("/t " + fullMessage); }
                            else { Call.sendChatMessage(fullMessage); }
                        } else {
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
}