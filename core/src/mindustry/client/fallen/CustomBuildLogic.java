package mindustry.client.fallen;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.Timer;
import mindustry.content.Blocks;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ConstructBlock;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.Objects;

import static mindustry.Vars.control;
import static mindustry.Vars.player;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

public class CustomBuildLogic {

    private static final Seq<FDConfigRequest> configQueue = new Seq<>();
    private static float configTimer = 0f;
    private static final float CONFIG_DELAY = 10f;

    public static void update() {
        if (configQueue.isEmpty() || state.isPaused() || !state.isGame()) return;

        configTimer += Time.delta;

        if (configTimer >= CONFIG_DELAY) {
            configTimer = 0f;

            // Берем первый запрос, но НЕ удаляем его пока не обработаем
            FDConfigRequest req = configQueue.first();
            Building building = req.resolve();

            // Если здание не найдено (например, еще строится или снесено)
            if (building == null) {
                req.attempts++;
                // Если пытаемся уже долго, удаляем запрос
                if (req.attempts > 100) {
                    configQueue.remove(0);
                }
                return;
            }

            // Если здание найдено, удаляем запрос из очереди
            configQueue.remove(0);

            Object currentConfig = (building instanceof ConstructBlock.ConstructBuild co) ? co.lastConfig : building.config();

            if (!Objects.equals(currentConfig, req.config)) {
                Call.tileConfig(player, building, req.config);
            }
        }
    }

    private static boolean isUnbuildable(Building b) {
        if (b == null) return false;
        if (b.block instanceof CoreBlock) return true;

        if (b.block == Blocks.spawn || b.block == Blocks.powerVoid) return true;

        // блоки, которые не должны сноситься (источники/поглотители)
        if (b.block == Blocks.powerSource || b.block == Blocks.powerVoid ||
                b.block == Blocks.itemSource || b.block == Blocks.itemVoid ||
                b.block == Blocks.liquidSource || b.block == Blocks.liquidVoid) {
            return true;
        }

        return false;
    }

    private static boolean isUnplaceable(Block block) {
        if (block instanceof CoreBlock) return true;
        if (block == Blocks.spawn || block == Blocks.powerVoid) return true;
        return false;
    }

    private static class FDConfigRequest {
        final int x, y;
        final Object config;
        final Team team;
        int attempts = 0;

        FDConfigRequest(Building b, Object c) {
            this.x = b.tileX();
            this.y = b.tileY();
            this.config = c;
            this.team = b.team;
        }

        Building resolve() {
            Building b = world.build(x, y);
            if (b != null && b.team == team) return b;
            return null;
        }
    }

    private static void queueConfig(Building building, Object config) {
        //if (building == null || config == null) return;
        if (building == null) return;

        // Проверяем, нет ли уже такого запроса в очереди для этой точки
        for(var other : configQueue) {
            if(other.x == building.tileX() && other.y == building.tileY()) {
                return;
            }
        }
        configQueue.add(new FDConfigRequest(building, config));
    }

    private static boolean isSameStructure(Building b, BuildPlan s) {
        if (b == null || s == null || s.block == null) return false;

        // Обработка блока в процессе строительства
        if (b instanceof ConstructBlock.ConstructBuild co) {
            return co.current == s.block &&
                    co.tileX() == s.x &&
                    co.tileY() == s.y;
        }

        // 1. Координаты должны совпадать идеально
        if (b.tileX() != s.x || b.tileY() != s.y) return false;

        // 2. Тип блока должен совпадать
        if (b.block != s.block) return false;

        // 3.Проверяем ротацию только если она важна для блока
        if (!s.block.rotate) return true;

        return b.rotation == s.rotation;
    }

    public static void placeSchematicWithCleanup(Seq<BuildPlan> plans) {
        Unit unit = player.unit();
        if(unit == null || plans.isEmpty()) return;

        Seq<BuildPlan> filteredPlans = new Seq<>(plans.size);

        for (BuildPlan s : plans) {
            if (isUnplaceable(s.block)) {
                continue;
            }
            filteredPlans.add(s);
        }
        if (filteredPlans.isEmpty()) return;

        plans = filteredPlans;

        ObjectSet<Building> toRemove = new ObjectSet<>();
        Seq<BuildPlan> toBuild = new Seq<>();
        int reconfiguredCount = 0;

        for(BuildPlan s : plans){
            var it = unit.plans.iterator();
            while(it.hasNext()){
                BuildPlan p = it.next();
                if(p.x == s.x && p.y == s.y){
                    control.input.playerPlanTree.remove(p);
                    it.remove();
                }
            }

            final boolean[] fits = {true};
            final Building[] currentBuild = {null};

            s.block.iterateTaken(s.x, s.y, (tx, ty) -> {
                Tile tile = world.tile(tx, ty);
                if (tile == null) {
                    fits[0] = false;
                    return;
                }

                Building other = tile.build;
                if (other == null) return;

                if (other.team != player.team() || isUnbuildable(other)) {
                    fits[0] = false;
                    return;
                }

                if (!isSameStructure(other, s)) {
                    if (other.tileX() != s.x || other.tileY() != s.y || !s.block.canReplace(other.block)) {
                        toRemove.add(other);
                    }
                } else {
                    currentBuild[0] = other;
                }
            });

            if (fits[0] && currentBuild[0] != null) {
                Building existing = currentBuild[0];

                if (!(existing instanceof ConstructBlock.ConstructBuild)) {
                    if (!Objects.equals(existing.config(), s.config)) {
                        queueConfig(existing, s.config);
                    }
                } else {
                    toBuild.add(new BuildPlan(s.x, s.y, s.rotation, s.block, s.config));
                }
            }
            else {
                toBuild.add(new BuildPlan(s.x, s.y, s.rotation, s.block, s.config));
            }
        }

        if(!toRemove.isEmpty()){
            for(Building b : toRemove){
                if (isUnbuildable(b)) {
                    continue;
                }
                BuildPlan breakPlan = new BuildPlan(b.tileX(), b.tileY());
                breakPlan.breaking = true;

                boolean exists = false;
                for(BuildPlan p : unit.plans) if(p.breaking && p.x == breakPlan.x && p.y == breakPlan.y) { exists = true; break; }

                if(!exists){
                    unit.plans.addLast(breakPlan);
                    control.input.playerPlanTree.insert(breakPlan);
                }
            }
        }

        if (!toBuild.isEmpty() || !toRemove.isEmpty()) {
            final Unit startUnit = player.unit();

            Timer.schedule(new Timer.Task() {
                int attempts = 0;
                @Override
                public void run() {
                    if (!state.isGame() || player.unit() != startUnit || player.unit() == null) {
                        this.cancel();
                        return;
                    }

                    boolean allRemoved = true;
                    for (Building b : toRemove) {
                        if (b.isAdded() && world.build(b.tileX(), b.tileY()) == b) {
                            allRemoved = false;
                            break;
                        }
                    }

                    if (allRemoved || attempts > 15) {
                        for (BuildPlan s : toBuild) {
                            // Финальная проверка перед постановкой в очередь
                            Building existing = world.build(s.x, s.y);

                            if (existing != null && existing.team == player.team() && isSameStructure(existing, s)) {

                                // Пропускаем добавление в планы только если блок ПОЛНОСТЬЮ ДОСТРОЕН
                                if (!(existing instanceof ConstructBlock.ConstructBuild)) {
                                    Object currentConfig = existing.config();

                                    if (!Objects.equals(currentConfig, s.config)) {
                                        queueConfig(existing, s.config);
                                    }
                                    continue;
                                }
                            }

                            player.unit().plans.addLast(s);
                            control.input.playerPlanTree.insert(s);
                        }
                        //control.input.isBuilding = true;
                        this.cancel();
                    }
                    attempts++;
                }
            }, 0.4f, 0.4f);
        }

//        String msg = "";
//        if(reconfiguredCount > 0) msg += "[accent]Перенастроено: " + reconfiguredCount + "[] ";
//        if(!toRemove.isEmpty()) msg += "[scarlet]Снос: " + toRemove.size + "[] ";
//        if(!toBuild.isEmpty()) msg += "[green]Планы: " + toBuild.size + "[]";
//        if(!msg.isEmpty()) Vars.player.sendMessage(msg);
    }

}
