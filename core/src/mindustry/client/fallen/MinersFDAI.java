package mindustry.client.fallen;

import arc.Core;
import arc.Events;
import arc.struct.IntSeq;
import arc.struct.IntSet;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Interval;
import arc.util.Log;
import arc.util.Strings;
import mindustry.Vars;
import mindustry.ai.ItemUnitStance;
import mindustry.ai.UnitCommand;
import mindustry.ai.UnitStance;
import mindustry.ai.types.CommandAI;
import mindustry.client.ui.PanelFragment;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.Units;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.UnitType;

import static mindustry.Vars.player;

// === Суть работы ИИ ===
// Управляет выбранными типами юнитов (моно, поли, пульсары, меги, квазары)
// Распределяет их по ресурсам на основе:
//   - весовых коэффициентов (зависит от заполненности хранилища)
//   - квот (минимальное количество юнитов на ресурс)
//   - приоритетов (кризисные ресурсы получают приоритет)
// Поддерживает два уровня кризиса:
//   - обычный: переключение на любые ресурсы ниже порога
//   - критический: переключение на базовые ресурсы (медь/свинец/титан) при сильном дефиците
// Дополнительные функции:
//   - авто-починка построек у ядра мегами
//   - помощь в строительстве (в радиусе от игрока)
//   - приоритет ручных команд (не перехватывает управление)
//   - автоматический возврат юнитов на свободу при отключении их типа
// Всё управляется через таймеры с настраиваемыми интервалами

public class MinersFDAI {
    public static boolean autoMiningActive = false;
    public static boolean autoAssistBuild = false;
    public static boolean respectManualCommands = false;
    private static boolean wasAutoMiningActive = false;

    private static Interval miningTimer = new Interval();
    private static Interval assistTimer = new Interval();
    public static int AIMiningUpdateTime = Core.settings.getInt("AIUpTime", 10);
    public static float AIHelpRad = Core.settings.getFloat("AIHelpRad", 10);
    public static boolean resetDisabledUnits = Core.settings.getBool("resetDisabledUnits", false);

    private static final ObjectMap<Integer, UnitCommand> lastAiCommand = new ObjectMap<>();
    private static final IntSet manualUnits = new IntSet();
    private static final IntSet assistingUnits = new IntSet();

    public static void init() {
        Events.on(EventType.WorldLoadEvent.class, e -> {
            autoMiningActive = false;
            wasAutoMiningActive = false;
            manualUnits.clear();
            assistingUnits.clear();
            lastAiCommand.clear();
        });

        Events.run(EventType.Trigger.update, () -> {
            if (Vars.state.isMenu()) return;


            // === ЗАПУСК КОПКИ ПРИ ВКЛЮЧЕНИИ ИИ ===
            if (autoMiningActive && !wasAutoMiningActive) {
                IntSeq toTakeOver = new IntSeq();
                for (Unit u : Groups.unit) {
                    if (u.team != player.team() || !u.isCommandable()) continue;

                    if (isManagedMinerType(u.type)) {
                        toTakeOver.add(u.id);
                        manualUnits.remove(u.id);    // Убираем из игнора
                        assistingUnits.remove(u.id); // Убираем из ассиста
                        lastAiCommand.remove(u.id);  // Стираем историю, чтобы ИИ не считал их "ручными"
                    }
                }

                if (toTakeOver.size > 0) {
                    // Просто даем всем команду копать. Без сложного распределения.
                    Call.setUnitCommand(player, toTakeOver.toArray(), UnitCommand.mineCommand);
                }

                wasAutoMiningActive = true;
                miningTimer.clear();
                assistTimer.clear();
            } else if (!autoMiningActive) {
                wasAutoMiningActive = false;
            }

            if (autoMiningActive && autoAssistBuild && assistTimer.get(60f)) {
                handleAssistNearPlayer();
            }

            if (autoMiningActive && miningTimer.get(AIMiningUpdateTime * 60f)) {
                autoAssignMiningUnitsEqually();
            }
        });
    }

    private static boolean isManagedMinerType(UnitType type) {
        if (type == UnitTypes.mono) return PanelFragment.mineMonos;
        if (type == UnitTypes.poly) return PanelFragment.minePolys;
        if (type == UnitTypes.mega) return PanelFragment.mineMegas;
        if (type == UnitTypes.pulsar) return PanelFragment.minePulss;
        if (type == UnitTypes.quasar) return PanelFragment.mineQuazs;
        return false; // По умолчанию не трогаем неопознанные типы
    }

    // ================== ФИЧА: АССИСТ БЛИЖАЙШИХ ЮНИТОВ ПРИ СТРОЙКЕ ==================
    private static void handleAssistNearPlayer() {
        if (player.unit() == null) return;

        boolean building = isPlayerBuilding();
        float px = player.x, py = player.y;
        float radiusPx = AIHelpRad * 8f;

        IntSeq toAssist = new IntSeq();
        IntSeq toReturn = new IntSeq();

        for (Unit u : Groups.unit) {
            if (u.team != player.team() || !u.isCommandable()) continue;
            if (u.type.buildSpeed <= 0f) continue;
            if (!isManagedMinerType(u.type)) continue; // Если тип выключен — не берем в ассист
            if (manualUnits.contains(u.id)) continue; // Не трогаем ручных юнитов

            boolean inRange = u.dst(px, py) <= radiusPx;
            boolean isCurrentlyAssist = u.controller() instanceof CommandAI cai && cai.command == UnitCommand.assistCommand;

            if (building && inRange) {
                if (!isCurrentlyAssist) toAssist.add(u.id);
                assistingUnits.add(u.id);
            } else if (assistingUnits.contains(u.id) || isCurrentlyAssist) {
                toReturn.add(u.id);
                assistingUnits.remove(u.id);
            }
        }

        if (toAssist.size > 0) {
            int[] ids = toAssist.toArray();
            Call.setUnitCommand(player, ids, UnitCommand.assistCommand);
            for (int id : ids) lastAiCommand.put(id, UnitCommand.assistCommand);
        }

        if (toReturn.size > 0) {
            int[] ids = toReturn.toArray();
            Call.setUnitCommand(player, ids, UnitCommand.mineCommand);
            for (int id : ids) lastAiCommand.put(id, UnitCommand.mineCommand);
        }
    }

    private static void autoAssignMiningUnitsEqually() {
        if (player.unit() == null) return;
        Building core = player.team().core();
        if (core == null) return;

        // --- Проверка режима починки ---
        boolean needsRepairNearCore = false;
        if (PanelFragment.autoHealMegas) {
            for (Building ncore : player.team().cores()) {
                if (ncore == null) continue;
                Building nearest = Units.findDamagedTile(player.team(), ncore.x, ncore.y);
                if (nearest != null && nearest.dst(ncore) / 8f < PanelFragment.autoHealDist) {
                    needsRepairNearCore = true;
                    break;
                }
            }
        }

        int megaCounter = 0; // Счетчик для разделения Мег пополам
        int capacity = core.core().storageCapacity;

        // 1. Считаем веса ресурсов (спрос ядра)
        Item[] items = {Items.copper, Items.lead, Items.titanium, Items.sand, Items.coal, Items.scrap};
        boolean[] flags = {
                PanelFragment.minecopper, PanelFragment.minelead, PanelFragment.minetitan,
                PanelFragment.minesand, PanelFragment.minecoal, PanelFragment.minescrap
        };
        ObjectMap<Item, Float> itemWeights = new ObjectMap<>();
        Seq<Item> allEnabled = new Seq<>();
        float totalWeight = 0;

        for (int i = 0; i < items.length; i++) {
            if (!flags[i]) continue;
            Item it = items[i];
            allEnabled.add(it);
            float progress = (float) core.items.get(it) / capacity;
            float weight = Math.max(0.05f, 1.0f - progress);
            if (progress < 0.1f) weight *= 5f;
            itemWeights.put(it, weight);
            totalWeight += weight;
        }
        if (allEnabled.isEmpty() || totalWeight <= 0) return;

        ObjectMap<Item, IntSeq> toBatchSend = new ObjectMap<>();

        // ============================================================
        // 2. ГЛОБАЛЬНОЕ ОПРЕДЕЛЕНИЕ КРИЗИСА
        // ============================================================
        final float CRITICAL_CORE_THRESHOLD = PanelFragment.crisisThreshold / 2f;
        final float NORMAL_CRISIS_THRESHOLD = PanelFragment.crisisThreshold;

        Seq<Item> globalCrisisItems = new Seq<>();
        boolean isCriticalCoreCrisis = false;

        Item[] coreResources = {Items.copper, Items.lead, Items.titanium};
        for (Item it : coreResources) {
            if (!allEnabled.contains(it)) continue;
            float progress = (float) core.items.get(it) / capacity;
            if (progress < CRITICAL_CORE_THRESHOLD) {
                globalCrisisItems.add(it);
                isCriticalCoreCrisis = true;
            }
        }

        if (!isCriticalCoreCrisis) {
            for (Item it : allEnabled) {
                float progress = (float) core.items.get(it) / capacity;
                if (progress < NORMAL_CRISIS_THRESHOLD) {
                    globalCrisisItems.add(it);
                }
            }
        }
        boolean isGlobalCrisis = !globalCrisisItems.isEmpty();

        // ================== ФИЧА: игнор ручных юнитов + возврат чужих ==================
        //IntSeq toStop = new IntSeq();
        IntSeq toReleaseAsAssist = new IntSeq(); // Для полей (poly)
        IntSeq toReleaseAsMine = new IntSeq();   // Для мег, квазаров, пульсаров и моно
        IntSeq toForceRestore = new IntSeq();
        IntSeq toRepair = new IntSeq();

        // 3. Группируем юнитов по типам
        ObjectMap<UnitType, Seq<Unit>> unitGroups = new ObjectMap<>();
        for (Unit u : Groups.unit) {
            if (u.team != player.team() || !u.isCommandable()) continue;

            // Тип выключен в панели — снимаем с учета, если раньше управляли им
            if (!isManagedMinerType(u.type)) {
                if (lastAiCommand.containsKey(u.id)) {
                    // Распределяем по типам для правильной команды при сбросе
                    if (u.type == UnitTypes.poly) {
                        toReleaseAsAssist.add(u.id);
                    } else if (u.type == UnitTypes.mega || u.type == UnitTypes.quasar ||
                            u.type == UnitTypes.pulsar || u.type == UnitTypes.mono) {
                        toReleaseAsMine.add(u.id);
                    }
                    lastAiCommand.remove(u.id);
                }
                continue;
            }

            // Ручной режим: если игрок сам дал юниту другую команду — не трогаем его.
            // Если команду дал кто-то другой — забираем обратно под управление ИИ.
            if (respectManualCommands && u.controller() instanceof CommandAI cai) {
                UnitCommand current = cai.command;
                UnitCommand expected = lastAiCommand.get(u.id);
                if (current != UnitCommand.mineCommand && expected != null && current != expected) {
                    if (current != UnitCommand.repairCommand && current != UnitCommand.assistCommand) {
                        String cmdr = u.lastCommanded != null ? Strings.stripColors(u.lastCommanded) : "";
                        Log.info("current=@ expected=@ lastCommanded='@' myName='@'",
                                 current, expected, cmdr, Strings.stripColors(player.name));
                        if (cmdr.equals(Strings.stripColors(player.name))) {
                            manualUnits.add(u.id);
                            continue;
                        } else {
                            toForceRestore.add(u.id);
                            manualUnits.remove(u.id);
                        }
                    }
                }
            }

            if (manualUnits.contains(u.id) || assistingUnits.contains(u.id)) continue;

            if (u.type == UnitTypes.mega) {
                if (!PanelFragment.mineMegas) continue;
                megaCounter++;

                if (PanelFragment.autoHealMegas && needsRepairNearCore && (!isGlobalCrisis || megaCounter % 2 == 0)) {
                    if (!(u.controller() instanceof CommandAI cai && cai.command == UnitCommand.repairCommand)) {
                        toRepair.add(u.id);
                    }
                    continue; // Отправляем хилить, в майнинг не пускаем
                }
            }

            if (u.type.mineTier > 0) {
                if (!unitGroups.containsKey(u.type)) unitGroups.put(u.type, new Seq<>());
                unitGroups.get(u.type).add(u);
            }
        }

        // Разовая отправка сервисных команд
        //if (toStop.size > 0) Call.setUnitCommand(player, toStop.toArray(), UnitCommand.assistCommand);
        if (resetDisabledUnits) {
            if (toReleaseAsAssist.size > 0) {
                Call.setUnitCommand(player, toReleaseAsAssist.toArray(), UnitCommand.assistCommand);
            }
            if (toReleaseAsMine.size > 0) {
                Call.setUnitCommand(player, toReleaseAsMine.toArray(), UnitCommand.mineCommand);
            }
        }
        if (toForceRestore.size > 0) Call.setUnitCommand(player, toForceRestore.toArray(), UnitCommand.mineCommand);
        if (toRepair.size > 0) {
            int[] ids = toRepair.toArray();
            Call.setUnitCommand(player, ids, UnitCommand.repairCommand);
            for (int id : ids) lastAiCommand.put(id, UnitCommand.repairCommand);
        }

        if (unitGroups.isEmpty()) return;

        // ============================================================
        // 4. ОБРАБОТКА КАЖДОЙ ГРУППЫ — ЛОГИКА КВОТ
        // ============================================================
        for (var entry : unitGroups.entries()) {
            UnitType type = entry.key;
            Seq<Unit> units = entry.value;

            Seq<Item> possible = allEnabled.select(it -> type.mineTier >= it.hardness);
            if (possible.isEmpty()) continue;

            Seq<Item> targets = possible;
            boolean isCrisisMode = false;

            if (isGlobalCrisis) {
                Seq<Item> myCrisisTargets = new Seq<>();
                for (Item it : globalCrisisItems) {
                    if (possible.contains(it)) myCrisisTargets.add(it);
                }
                if (!myCrisisTargets.isEmpty()) {
                    targets = myCrisisTargets;
                    isCrisisMode = true;
                }
            }

            ObjectMap<Item, Integer> quotas = new ObjectMap<>();
            int assignedCount = 0;

            float currentTotalWeight = 0;
            for (Item it : targets) currentTotalWeight += itemWeights.get(it, 0f);
            if (currentTotalWeight <= 0) continue;

            for (Item it : possible) {
                int target;

                if (isCrisisMode) {
                    if (!targets.contains(it)) {
                        target = 0;
                    } else {
                        int baseShare = units.size / targets.size;
                        int remainder = units.size % targets.size;
                        int index = targets.indexOf(it);
                        target = baseShare + (index < remainder ? 1 : 0);

                        float progress = (float) core.items.get(it) / capacity;
                        if (progress < 0.1f) target += 1;
                    }
                } else {
                    int baseTarget = Math.round((itemWeights.get(it, 0f) / currentTotalWeight) * units.size);
                    target = Math.max(PanelFragment.minUnitsPerResource, baseTarget);
                }

                quotas.put(it, target);
                assignedCount += target;
            }

            // 5. КОРРЕКТИРОВКА (БАЛАНСИРОВКА)
            while (assignedCount > units.size) {
                Item toReduce = possible.max(it -> {
                    int q = quotas.get(it, 0);
                    if (q <= 0) return -1f;
                    return (float) q / itemWeights.get(it, 0.05f);
                });
                if (toReduce != null) {
                    quotas.put(toReduce, quotas.get(toReduce, 0) - 1);
                    assignedCount--;
                } else break;
            }

            while (assignedCount < units.size) {
                Item toBoost = targets.max(it -> itemWeights.get(it, 0f));
                if (toBoost != null) {
                    quotas.put(toBoost, quotas.get(toBoost, 0) + 1);
                    assignedCount++;
                } else break;
            }

            // --- ЛОГИКА "ЛИПКОСТИ" ---
            Seq<Unit> unassignedUnits = new Seq<>();

            for (Unit u : units) {
                Item currentItem = null;
                if (u.controller() instanceof CommandAI cai && cai.command == UnitCommand.mineCommand) {
                    for (Item it : possible) {
                        if (cai.hasStance(ItemUnitStance.getByItem(it))) {
                            currentItem = it;
                            break;
                        }
                    }
                }

                if (currentItem != null && quotas.get(currentItem, 0) > 0) {
                    quotas.put(currentItem, quotas.get(currentItem, 0) - 1);
                } else {
                    unassignedUnits.add(u);
                }
            }

            for (Unit u : unassignedUnits) {
                Item bestTarget = possible.max(it -> quotas.get(it, 0));

                if (bestTarget != null && quotas.get(bestTarget, 0) > 0) {
                    quotas.put(bestTarget, quotas.get(bestTarget, 0) - 1);

                    // ФИКС: реальное сохранение id юнита в мапу пакетной отправки
                    if (!toBatchSend.containsKey(bestTarget)) toBatchSend.put(bestTarget, new IntSeq());
                    toBatchSend.get(bestTarget).add(u.id);
                } else {
                    Item fallback = possible.max(it -> itemWeights.get(it, 0f));

                    // Не слать команду повторно, если юнит уже и так копает fallback-ресурс
                    if (!(u.controller() instanceof CommandAI cai && cai.command == UnitCommand.mineCommand && cai.hasStance(ItemUnitStance.getByItem(fallback)))) {
                        if (!toBatchSend.containsKey(fallback)) toBatchSend.put(fallback, new IntSeq());
                        toBatchSend.get(fallback).add(u.id);
                    }
                }
            }
        }

        // 6. Отправка пакетов
        for (var entry : toBatchSend.entries()) {
            int[] ids = entry.value.toArray();
            Call.setUnitCommand(player, ids, UnitCommand.mineCommand);
            Call.setUnitStance(player, ids, UnitStance.mineAuto, false);
            Call.setUnitStance(player, ids, ItemUnitStance.getByItem(entry.key), true);
            for (int id : ids) lastAiCommand.put(id, UnitCommand.mineCommand);
        }
    }

    private static boolean isPlayerBuilding() {
        Unit u = player.unit();
        return u != null && (u.plans.size > 0 || u.activelyBuilding());
    }
}