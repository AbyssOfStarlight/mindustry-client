package mindustry.client.fallen;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Timer;
import mindustry.Vars;
import mindustry.client.ClientVars;
import mindustry.client.fallen.ActionsHistory.*;
import mindustry.content.Blocks;
import mindustry.core.NetClient;
import mindustry.game.EventType.*;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.ui.fragments.ChatFragment;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static mindustry.Vars.*;

public class ActivityLogger {

    public static void init() {

        //setupNickAnimation();

        Timer.schedule(() -> {
            if (Vars.ui.chatfrag == null || !Vars.state.isGame()) return;

            StringBuilder stats = new StringBuilder();
            stats.append("[accent]History Status: [white]")
                    .append("Blocks: ").append(ActionsHistory.blocksplayersplans.size).append(" | ")
                    .append("Configs: ").append(ActionsHistory.blockconfplayersplans.size).append(" | ")
                    .append("Items: ").append(ActionsHistory.playeritemsplans.size).append(" | ")
                    .append("Deaths: ").append(ActionsHistory.deathunitsplan.size);

            String finalMsg = stats.toString();

            //Vars.ui.chatfrag.addMessage(finalMsg, null, null, "", finalMsg);

        }, 100, 100);

        HistoryRenderer.init();

        Events.on(BlockDestroyEvent.class, event -> {
            if(!Core.settings.getBool("coredeathalarm")) return;
            if(state.rules.coreCapture && Core.settings.getBool("coredeathalarmrecap")) return;

            if(event.tile.build instanceof CoreBlock.CoreBuild core){
                int cx = Mathf.ceil(core.x / 8f);
                int cy = Mathf.ceil(core.y / 8f);
                String msg;

                if(core.team == player.team()){
                    msg = "[#fa]Our core at " + cx + ", " + cy + " death...";

                    if(Core.settings.getBool("unitatchat") && !state.rules.coreCapture){
                        if(state.rules.pvp) {
                            Call.sendChatMessage("/t " + msg);
                        } else {
                            Call.sendChatMessage(msg);
                        }
                    } else if (!state.rules.coreCapture){
                        addLocalMessage(msg);
                    }
                } else {
                    msg = "[#" + core.team.color + "]" + core.team.name + " core at []" + cx + ", " + cy + " death.";
                    addLocalMessage(msg);
                }
            }
        });

        Events.on(BlockBuildBeginEventBefore.class, it -> {
            if(!Core.settings.getBool("blocksplayersplan")) return;
            if (it.unit == null) return;

            String name = it.unit.getControllerName();
            String rawName = Strings.stripColors(name == null ? "Unknown" : name);

            if (it.newBlock == null || it.newBlock == Blocks.air) {
                if (it.tile.build == null || !it.tile.block().rebuildable) return;

                ActionsHistory.blocksplayersplans.addFirst(new BlockPlayerPlan(
                        it.tile.x, it.tile.y, (short) it.tile.build.rotation,
                        it.tile.build.block.id, it.tile.build.config(),
                        Strings.stripColors(rawName), it.breaking
                ));
            } else {
                if (it.tile == null) return;

                ActionsHistory.blocksplayersplans.addFirst(new BlockPlayerPlan(
                        it.tile.x, it.tile.y, (short) 0,
                        it.newBlock.id, null,
                        Strings.stripColors(rawName), it.breaking
                ));
            }
        });

        Events.on(BuildRotateEvent.class, it -> {
            if(!Core.settings.getBool("blocksplayersplan")) return;
            if (it.build == null || it.unit.controller() == null) return;
            String rawPlayerName = Strings.stripColors(it.unit.getControllerName() == null ? "Unknown" : it.unit.getControllerName());

            ActionsHistory.blockconfplayersplans.addFirst(new BlockConfigPlayerPlan(
                    (int)it.build.x/8, (int)it.build.y/8, it.build.block.id, rawPlayerName)
            );
        });

        Events.on(ConfigEvent.class, it -> {
            if(!Core.settings.getBool("blocksplayersplan")) return;
            if (it.tile.block == null || it.player == null) return;
            String rawPlayerName = Strings.stripColors(it.player == null ? "Unknown" : it.player.name);


            ActionsHistory.blockconfplayersplans.addFirst(new BlockConfigPlayerPlan(
                    (int)it.tile.x/8, (int)it.tile.y/8, it.tile.block.id, rawPlayerName
            ));
        });

        Events.on(DepositEvent.class, it -> {
            if(!Core.settings.getBool("itemslog")) return;
            if (it.tile.block == null || it.player == null) return;

            ActionsHistory.playeritemsplans.addFirst(new ItemPlayerPlan(
                    it.player, it.tile.tile, it.item, false
            ));
        });

        Events.on(WithdrawEvent.class, it -> {
            if(!Core.settings.getBool("itemslog")) return;
            if (it.tile.block == null || it.player == null) return;

            ActionsHistory.playeritemsplans.addFirst(new ItemPlayerPlan(
                    it.player, it.tile.tile, it.item, true
            ));
        });

        Events.on(UnitRealDeathEvent.class, it -> {
            if (it.unit == null || it.unit.type == null) return;

            Player p = it.unit.getPlayer();
            String commander = it.unit.lastCommanded;
            if (p == null && commander == null) return;

            boolean canAlarm = Core.settings.getBool("playerunitdeathalarm");
            int hpLimit = Core.settings.getInt("playerunitdeathalarmhp", 500);
            boolean isHeavyUnit = it.unit.maxHealth >= hpLimit && hpLimit > 0;

            if (p != null) {
                ActionsHistory.deathunitsplan.addFirst(new ActionsHistory.UnitsKilledByPlayers(
                        p, it.unit.type, it.unit.x, it.unit.y
                ));

                if (canAlarm && isHeavyUnit) {
                    addLocalMessage("!![#fa]⚠[]:  [#" + it.unit.team.color + "]" + it.unit.type.localizedName + " убито с позором. [" + Mathf.ceil(it.unit.lastX / 8) + "," + Mathf.ceil(it.unit.lastY / 8) +"] Пилот: " + p.name );
                }
            } else {
                ActionsHistory.deathunitscontrolplan.addFirst(new ActionsHistory.UnitsKilledByControllPlayers(
                        commander, it.unit.type, it.unit.x, it.unit.y
                ));

                if (canAlarm && isHeavyUnit) {
                    addLocalMessage("!![#fa]⚠[]: [#" + it.unit.team.color + "]" + it.unit.type.localizedName + " бездарно потерян. ["+ Mathf.ceil(it.unit.lastX / 8) + "," + Mathf.ceil(it.unit.lastY / 8) +"] Командир: " + commander);
                }
            }
        });
    }

    private static void addLocalMessage(String msg) {
        if (ui.chatfrag == null) return;
        ChatFragment.ChatMessage m = ui.chatfrag.addMessage(msg, null, null, "", msg);
        NetClient.findCoords(m);
    }
}