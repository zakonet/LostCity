package common.cn.kafei.simukraft.industrial;

import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenHomeRestService;
import common.cn.kafei.simukraft.citizen.CitizenJobVisualService;
import common.cn.kafei.simukraft.citizen.CitizenLevelService;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenSkillSnapshot;
import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.job.CityJobType;
import common.cn.kafei.simukraft.path.CitizenNavigationService;
import common.cn.kafei.simukraft.path.MovementIntent;
import common.cn.kafei.simukraft.util.SaveScopedCacheKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("null")
public final class IndustrialWorkService {
    private static final ConcurrentMap<String, LevelRuntime> RUNTIMES = new ConcurrentHashMap<>();
    private static final long IDLE_RETRY_TICKS = 40L;
    private static final long MOVE_RETRY_TICKS = 8L;

    private IndustrialWorkService() {
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        IndustrialBoxManager manager = IndustrialBoxManager.get(level);
        LevelRuntime runtime = runtime(level);
        long gameTime = level.getGameTime();
        for (IndustrialBoxData data : manager.all()) {
            BoxRuntime boxRuntime = runtime.boxes.computeIfAbsent(data.boxPos(), ignored -> new BoxRuntime());
            if (!data.running()) {
                boxRuntime.reset();
                continue;
            }
            if (gameTime < boxRuntime.nextTick) {
                continue;
            }
            tickBox(level, manager, data, boxRuntime, gameTime);
        }
    }

    public static void flush(ServerLevel level) {
        if (level != null) {
            IndustrialBoxManager.get(level).saveToSqlite(level);
        }
    }

    public static void clearServerCaches(MinecraftServer server) {
        String serverKey = SaveScopedCacheKey.serverKey(server).toLowerCase(Locale.ROOT);
        RUNTIMES.keySet().removeIf(key -> key.startsWith(serverKey + "|"));
    }

    private static void tickBox(ServerLevel level, IndustrialBoxManager manager, IndustrialBoxData data, BoxRuntime boxRuntime, long gameTime) {
        if (!IndustrialControlBoxService.isIndustrialControlBox(level, data.boxPos())) {
            manager.remove(data.boxPos());
            boxRuntime.reset();
            return;
        }
        PlacedBuildingRecord building = IndustrialControlBoxService.resolveBuilding(level, data.boxPos());
        IndustrialDefinitionLoader.LoadResult loadResult = IndustrialDefinitionLoader.loadForBuilding(building);
        IndustrialDefinition definition = loadResult.definition();
        CitizenData worker = IndustrialControlBoxService.findAssignedWorker(level, data.boxPos());
        if (building == null) {
            pause(manager, data, boxRuntime, "gui.simukraft.industrial.status.no_building", "");
            return;
        }
        if (!loadResult.valid() || definition == null) {
            pause(manager, data, boxRuntime, "gui.simukraft.industrial.status.invalid_definition", String.join(",", loadResult.errors()));
            return;
        }
        if (worker == null) {
            pause(manager, data, boxRuntime, "gui.simukraft.industrial.status.no_worker", "");
            return;
        }
        IndustrialEntitySpawnService.ensureSpawned(level, manager, data, building, definition);
        if (CitizenHomeRestService.isRestTime(level)) {
            setStatus(manager, data, "gui.simukraft.industrial.status.resting", "");
            CitizenJobVisualService.clearMainHandOverride(worker.uuid());
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        CitizenEntity entity = CitizenTeleportService.findCitizenEntity(level, worker.uuid());
        if (entity == null) {
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
            return;
        }
        IndustrialDefinition.RecipeDefinition recipe = definition.recipeById(data.selectedRecipeId());
        if (recipe == null || recipe.steps().isEmpty()) {
            pause(manager, data, boxRuntime, "gui.simukraft.industrial.status.no_recipe", "");
            return;
        }
        if (data.currentStep() >= recipe.steps().size()) {
            data.setCurrentStep(0);
            manager.persist(data);
        }
        IndustrialDefinition.StepDefinition step = recipe.steps().get(data.currentStep());
        StepResult result = executeStep(level, manager, data, boxRuntime, building, definition, recipe, worker, entity, step, gameTime);
        if (result == StepResult.PROGRESSED) {
            advanceStep(manager, data, recipe, boxRuntime);
            boxRuntime.nextTick = gameTime + 1L;
        } else if (result == StepResult.WAITING_MOVE) {
            boxRuntime.nextTick = gameTime + MOVE_RETRY_TICKS;
        } else if (result == StepResult.WAITING_RETRY) {
            boxRuntime.nextTick = gameTime + IDLE_RETRY_TICKS;
        }
    }

    private static StepResult executeStep(ServerLevel level,
                                          IndustrialBoxManager manager,
                                          IndustrialBoxData data,
                                          BoxRuntime boxRuntime,
                                          PlacedBuildingRecord building,
                                          IndustrialDefinition definition,
                                          IndustrialDefinition.RecipeDefinition recipe,
                                          CitizenData worker,
                                          CitizenEntity entity,
                                          IndustrialDefinition.StepDefinition step,
                                          long gameTime) {
        String type = step.type().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "set_held_item" -> {
                String itemId = !step.item().isBlank() ? step.item() : recipe.effectiveHeldItem(definition.heldItem());
                CitizenJobVisualService.setMainHandOverride(worker.uuid(), IndustrialInventoryService.stackForItem(itemId, 1));
                yield StepResult.PROGRESSED;
            }
            case "move_to" -> moveTo(level, data, boxRuntime, building, definition, worker, entity, step);
            case "look_at" -> lookAt(building, definition, entity, step);
            case "require_inputs" -> requireInputs(level, manager, data, building, definition, recipe, step);
            case "require_output_space" -> requireOutputSpace(level, manager, data, building, definition, recipe, step);
            case "use_item" -> useItem(entity, boxRuntime, step, gameTime);
            case "craft_recipe" -> craftRecipe(level, manager, data, building, definition, recipe, worker, step);
            case "set_status" -> {
                setStatus(manager, data,
                        !step.statusKey().isBlank() ? step.statusKey() : "gui.simukraft.industrial.status.running",
                        step.statusText());
                yield StepResult.PROGRESSED;
            }
            default -> {
                setStatus(manager, data, "gui.simukraft.industrial.status.invalid_step", type);
                yield StepResult.WAITING_RETRY;
            }
        };
    }

    private static StepResult moveTo(ServerLevel level,
                                     IndustrialBoxData data,
                                     BoxRuntime boxRuntime,
                                     PlacedBuildingRecord building,
                                     IndustrialDefinition definition,
                                     CitizenData worker,
                                     CitizenEntity entity,
                                     IndustrialDefinition.StepDefinition step) {
        BlockPos target = IndustrialControlBoxService.resolvePoint(building, definition, step.point(), entity.position());
        if (target == null) {
            setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.missing_point", step.point());
            return StepResult.WAITING_RETRY;
        }
        double range = Math.max(0.2D, step.range());
        Vec3 targetCenter = Vec3.atBottomCenterOf(target);
        if (entity.position().distanceToSqr(targetCenter) <= range * range) {
            CitizenNavigationService.stop(level, worker.uuid());
            return StepResult.PROGRESSED;
        }
        setStatus(IndustrialBoxManager.get(level), data, "gui.simukraft.industrial.status.moving", step.point());
        CitizenNavigationService.requestMove(level, worker.uuid(), targetCenter, MovementIntent.WORK);
        boxRuntime.resetStep(data.currentStep());
        return StepResult.WAITING_MOVE;
    }

    private static StepResult lookAt(PlacedBuildingRecord building, IndustrialDefinition definition, CitizenEntity entity, IndustrialDefinition.StepDefinition step) {
        BlockPos target = IndustrialControlBoxService.resolvePoint(building, definition, step.point(), entity.position());
        if (target == null) {
            return StepResult.WAITING_RETRY;
        }
        Vec3 center = Vec3.atCenterOf(target);
        entity.getLookControl().setLookAt(center.x, center.y, center.z);
        return StepResult.PROGRESSED;
    }

    private static StepResult requireInputs(ServerLevel level,
                                            IndustrialBoxManager manager,
                                            IndustrialBoxData data,
                                            PlacedBuildingRecord building,
                                            IndustrialDefinition definition,
                                            IndustrialDefinition.RecipeDefinition recipe,
                                            IndustrialDefinition.StepDefinition step) {
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.container(), step.input(), "input"));
        if (containers.isEmpty() || !IndustrialInventoryService.hasInputs(level, containers, recipe.inputs())) {
            setStatus(manager, data, "gui.simukraft.industrial.status.missing_inputs", "");
            return StepResult.WAITING_RETRY;
        }
        return StepResult.PROGRESSED;
    }

    private static StepResult requireOutputSpace(ServerLevel level,
                                                 IndustrialBoxManager manager,
                                                 IndustrialBoxData data,
                                                 PlacedBuildingRecord building,
                                                 IndustrialDefinition definition,
                                                 IndustrialDefinition.RecipeDefinition recipe,
                                                 IndustrialDefinition.StepDefinition step) {
        List<BlockPos> containers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.container(), step.output(), "output"));
        List<ItemStack> worstCaseOutputs = recipe.outputs().stream()
                .map(output -> IndustrialInventoryService.stackForItem(output.itemId(), output.potionId(), output.baseAmount() + Math.max(0, output.randomRange())))
                .filter(stack -> !stack.isEmpty())
                .toList();
        if (containers.isEmpty() || !IndustrialInventoryService.hasOutputSpace(level, containers, worstCaseOutputs)) {
            setStatus(manager, data, "gui.simukraft.industrial.status.output_full", "");
            return StepResult.WAITING_RETRY;
        }
        return StepResult.PROGRESSED;
    }

    private static StepResult useItem(CitizenEntity entity, BoxRuntime boxRuntime, IndustrialDefinition.StepDefinition step, long gameTime) {
        if (boxRuntime.activeStep != boxRuntimeStepKey(entity, step)) {
            boxRuntime.activeStep = boxRuntimeStepKey(entity, step);
            boxRuntime.stepStartedAt = gameTime;
            boxRuntime.swingDone = false;
        }
        if (step.swing() && !boxRuntime.swingDone) {
            entity.triggerWorkSwing(InteractionHand.MAIN_HAND);
            boxRuntime.swingDone = true;
        }
        return gameTime - boxRuntime.stepStartedAt >= Math.max(1, step.ticks()) ? StepResult.PROGRESSED : StepResult.WAITING;
    }

    private static StepResult craftRecipe(ServerLevel level,
                                          IndustrialBoxManager manager,
                                          IndustrialBoxData data,
                                          PlacedBuildingRecord building,
                                          IndustrialDefinition definition,
                                          IndustrialDefinition.RecipeDefinition recipe,
                                          CitizenData worker,
                                          IndustrialDefinition.StepDefinition step) {
        List<BlockPos> inputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.input(), step.container(), "input"));
        List<BlockPos> outputContainers = IndustrialControlBoxService.resolveContainerPositions(building, definition, containerName(step.output(), step.container(), "output"));
        CitizenSkillSnapshot skill = CitizenLevelService.snapshot(worker, CityJobType.INDUSTRIAL_WORKER);
        double multiplier = 1.0D + Math.max(0, skill.level() - 1) * 0.05D;
        if (!IndustrialInventoryService.craftRecipe(level, inputContainers, outputContainers, recipe, multiplier, level.random)) {
            setStatus(manager, data, "gui.simukraft.industrial.status.craft_blocked", "");
            return StepResult.WAITING_RETRY;
        }
        CitizenLevelService.addExperience(level, worker.uuid(), CityJobType.INDUSTRIAL_WORKER, 2);
        setCitizenStatus(level, worker, "gui.simukraft.industrial.status.running", "");
        setStatus(manager, data, "gui.simukraft.industrial.status.running", "");
        return StepResult.PROGRESSED;
    }

    private static void advanceStep(IndustrialBoxManager manager, IndustrialBoxData data, IndustrialDefinition.RecipeDefinition recipe, BoxRuntime boxRuntime) {
        int next = data.currentStep() + 1;
        data.setCurrentStep(next >= recipe.steps().size() ? 0 : next);
        boxRuntime.reset();
        manager.persist(data);
    }

    private static void pause(IndustrialBoxManager manager, IndustrialBoxData data, BoxRuntime boxRuntime, String statusKey, String statusText) {
        data.setRunning(false);
        data.setStatusKey(statusKey);
        data.setStatusText(statusText);
        manager.persist(data);
        boxRuntime.reset();
    }

    private static void setStatus(IndustrialBoxManager manager, IndustrialBoxData data, String statusKey, String statusText) {
        String safeText = statusText != null ? statusText : "";
        if (Objects.equals(data.statusKey(), statusKey) && Objects.equals(data.statusText(), safeText)) {
            return;
        }
        data.setStatusKey(statusKey);
        data.setStatusText(safeText);
        manager.persist(data);
    }

    private static void setCitizenStatus(ServerLevel level, CitizenData worker, String statusKey, String needDetail) {
        if (worker == null) {
            return;
        }
        worker.setWorkStatus(CitizenWorkStatus.WORKING);
        worker.setStatusLabel(statusKey);
        worker.setWorkNeedDetail(needDetail != null ? needDetail : "");
        CitizenService.save(level, worker.uuid());
    }

    private static String containerName(String primary, String secondary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (secondary != null && !secondary.isBlank()) {
            return secondary;
        }
        return fallback;
    }

    private static int boxRuntimeStepKey(CitizenEntity entity, IndustrialDefinition.StepDefinition step) {
        return Objects.hash(entity.getUUID(), step.type(), step.ticks(), step.swing());
    }

    private static LevelRuntime runtime(ServerLevel level) {
        return RUNTIMES.computeIfAbsent(SaveScopedCacheKey.levelKey(level).toLowerCase(Locale.ROOT), ignored -> new LevelRuntime());
    }

    private enum StepResult {
        PROGRESSED,
        WAITING,
        WAITING_MOVE,
        WAITING_RETRY
    }

    private static final class LevelRuntime {
        private final ConcurrentMap<BlockPos, BoxRuntime> boxes = new ConcurrentHashMap<>();
    }

    private static final class BoxRuntime {
        private long nextTick;
        private int activeStep = Integer.MIN_VALUE;
        private long stepStartedAt;
        private boolean swingDone;

        private void reset() {
            activeStep = Integer.MIN_VALUE;
            stepStartedAt = 0L;
            swingDone = false;
        }

        private void resetStep(int currentStep) {
            if (activeStep != currentStep) {
                reset();
            }
        }
    }
}
