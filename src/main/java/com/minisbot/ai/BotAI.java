package com.minisbot.ai;

import com.minisbot.MinisBotMod;
import com.minisbot.entity.BotPlayer;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.Dimensions;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * BotAI — 机器人的 AI 行为控制系统
 * 管理任务队列、自动寻路、自动交互
 * 支持下界残骸挖掘，支持跨维度传送
 */
public class BotAI {
    private final BotPlayer bot;
    private final LinkedList<BotTask> taskQueue = new LinkedList<>();
    private BotTask currentTask = null;
    private String currentDescription = "空闲";
    private int tickCounter = 0;
    private BlockPos targetPos = null;
    private int stuckTimer = 0;
    private BlockPos lastPos = null;
    private int idleTimer = 0;

    public BotAI(BotPlayer bot) {
        this.bot = bot;
    }

    public void tick() {
        if (bot.level().isClientSide) return;
        tickCounter++;

        if (tickCounter % 5 != 0) return;
        checkStuck();

        if (currentTask == null || currentTask.isCompleted()) {
            currentTask = taskQueue.poll();
            if (currentTask == null) {
                if (!currentDescription.equals("空闲")) {
                    currentDescription = "空闲";
                }
                targetPos = null;
                idleTimer++;
                if (idleTimer % 200 == 0 && idleTimer > 100) {
                    // 长时间空闲时小范围走动
                    wander();
                }
                return;
            }
            idleTimer = 0;
            targetPos = null;
            onTaskStart(currentTask);
        }

        if (currentTask != null && !currentTask.isCompleted()) {
            executeCurrentTask();
        }
    }

    private void checkStuck() {
        if (targetPos == null) return;
        BlockPos currentPos = bot.blockPosition();
        if (currentPos.equals(lastPos)) {
            stuckTimer++;
            if (stuckTimer > 40) { // ~10秒卡住
                bot.setJumping(true);
                if (stuckTimer > 60) {
                    bot.setJumping(false);
                    targetPos = null;
                    currentDescription = "寻路卡住";
                    stuckTimer = 0;
                }
            }
        } else {
            stuckTimer = 0;
            lastPos = currentPos;
        }
    }

    private void onTaskStart(BotTask task) {
        currentDescription = switch (task.getType()) {
            case CHOP_TREE -> "正在寻找树木...";
            case MINE -> "正在挖矿: " + task.getTarget();
            case FARM -> "正在植树...";
            case FIGHT -> "进入战斗模式";
            case FOLLOW_PLAYER -> "正在跟随 " + task.getTarget();
            case COLLECT -> "正在采集: " + task.getTarget();
            case GO_HOME -> "正在回家...";
            default -> "执行任务";
        };
    }

    private void executeCurrentTask() {
        switch (currentTask.getType()) {
            case MINE -> executeMine();
            case CHOP_TREE -> executeChopTree();
            case FARM -> executeFarm();
            case FIGHT -> executeFight();
            case FOLLOW_PLAYER -> executeFollow();
            case COLLECT -> executeCollect();
            case GO_HOME -> executeGoHome();
            default -> currentTask.complete("完成");
        }
    }

    // ==================== 挖矿（核心：支持下界残骸） ====================

    private void executeMine() {
        ServerLevel level = (ServerLevel) bot.level();
        String target = currentTask.getTarget().toLowerCase();

        // 检测是否需要去下界
        boolean needsNether = target.contains("ancient") || target.contains("debris")
                || target.contains("残骸") || target.contains("netherite");

        if (needsNether && !isInNether(level)) {
            // 去下界
            goToNether();
            return;
        }

        // 在正确维度中搜索矿石
        BlockPos orePos = findOre(level, bot.blockPosition(), 24, target);

        if (orePos == null) {
            // 没找到，往下挖
            if (needsNether) {
                // 下界残骸在 Y=8~22 之间，直接往这个范围挖
                int y = bot.blockPosition().getY();
                if (y > 22) {
                    digDownStaircase(level, Direction.NORTH, 3);
                    currentDescription = "下到 Y=8~22 层找残骸 (当前Y=" + y + ")";
                } else if (y < 8) {
                    // 在 Y=8~22 范围内横向挖掘
                    tunnelHorizontal(level, 3);
                    currentDescription = "在 Y=" + y + " 横向挖掘找残骸";
                } else {
                    tunnelHorizontal(level, 3);
                    currentDescription = "在 Y=" + y + " 层挖矿找残骸";
                }
            } else {
                digDown(level);
                currentDescription = "向下挖矿 (Y=" + bot.blockPosition().getY() + ")";
            }

            currentTask.setProgress(Math.min(100, currentTask.getProgress() + 1));
            if (currentTask.getProgress() >= 100) {
                currentTask.fail("没有找到 " + target);
            }
            return;
        }

        targetPos = orePos;
        if (moveToward(orePos, 2.0)) {
            // 挖掘周围 3x3 区域（远古残骸常成片出现）
            int radius = needsNether ? 2 : 1;
            int mined = 0;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos p = orePos.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(p);
                        if (isTargetOre(state, target)) {
                            breakBlock(p);
                            mined++;
                        }
                    }
                }
            }

            if (mined > 0) {
                currentTask.setProgress(Math.min(100, currentTask.getProgress() + mined * 10));
            } else {
                currentTask.setProgress(currentTask.getProgress() + 5);
            }

            // 继续找下一个
            if (currentTask.getProgress() >= 100) {
                currentTask.complete("已挖掘 " + target + " x" + mined);
            }
        }
    }

    // ==================== 砍树 ====================

    private void executeChopTree() {
        ServerLevel level = (ServerLevel) bot.level();
        BlockPos logPos = findNearest(level, bot.blockPosition(), 20,
                state -> state.is(BlockTags.LOGS));

        if (logPos == null) {
            wander();
            currentTask.setProgress(Math.min(100, currentTask.getProgress() + 2));
            if (currentTask.getProgress() >= 100) currentTask.fail("附近没有树");
            return;
        }

        targetPos = logPos;
        if (moveToward(logPos, 2.0)) {
            // 向上砍完整棵树
            BlockPos top = logPos;
            while (level.getBlockState(top).is(BlockTags.LOGS)) {
                breakBlock(top);
                top = top.above();
            }
            currentTask.setProgress(currentTask.getProgress() + 20);
            if (currentTask.getProgress() >= 100) currentTask.complete("砍树完成");
        }
    }

    // ==================== 植树 ====================

    private void executeFarm() {
        ServerLevel level = (ServerLevel) bot.level();
        BlockPos spot = findNearest(level, bot.blockPosition(), 12,
                state -> state.getBlock() == Blocks.GRASS_BLOCK
                        || state.getBlock() == Blocks.DIRT);

        if (spot == null) {
            currentTask.complete("没有合适的位置种树");
            return;
        }

        targetPos = spot;
        if (moveToward(spot, 2.0)) {
            // 检查背包里是否有树苗
            boolean hasSapling = bot.getBotInventory().hasItemCategory(
                    stack -> stack.getItem() instanceof net.minecraft.world.item.BoneMealItem
                            || stack.getItem() == Items.OAK_SAPLING
            );

            if (hasSapling || true) { // 强制种植，种子由命令 /give 提供
                level.setBlockAndUpdate(spot.above(), Blocks.OAK_SAPLING.defaultBlockState());
                // 如果背包有骨粉就施肥
                if (bot.getBotInventory().hasItemCategory(
                        stack -> stack.getItem() == Items.BONE_MEAL)) {
                    level.setBlockAndUpdate(spot.above(),
                            Blocks.OAK_SAPLING.defaultBlockState()
                                    .setValue(SaplingBlock.STAGE, 1));
                }
                currentTask.complete("已种树");
            } else {
                currentTask.fail("没有树苗");
            }
        }
    }

    // ==================== 战斗 ====================

    private void executeFight() {
        ServerLevel level = (ServerLevel) bot.level();
        var monsters = level.getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class,
                bot.getBoundingBox().inflate(20),
                mob -> mob.isAlive() && !mob.isAlliedTo(bot));

        if (!monsters.isEmpty()) {
            var target = monsters.iterator().next();
            targetPos = target.blockPosition();
            if (moveToward(targetPos, 2.5)) {
                bot.swing(InteractionHand.MAIN_HAND);
                bot.doHurtTarget(target);
                currentTask.setProgress(Math.min(100, currentTask.getProgress() + 10));
            }
        } else {
            wander();
            currentTask.setProgress(currentTask.getProgress() + 2);
            if (currentTask.getProgress() >= 100) currentTask.complete("巡逻完成");
        }
    }

    // ==================== 跟随 ====================

    private void executeFollow() {
        if (MinisBotMod.SERVER == null) return;
        String name = currentTask.getTarget();
        ServerPlayer player = MinisBotMod.SERVER.getPlayerList().getPlayerByName(name);
        if (player == null) {
            currentTask.fail("玩家 " + name + " 不在线");
            return;
        }

        // 如果玩家在不同维度，传送
        if (!bot.level().dimension().equals(player.level().dimension())) {
            bot.teleportToDimension((ServerLevel) player.level(),
                    player.getX(), player.getY(), player.getZ());
            return;
        }

        double dist = bot.distanceToSqr(player);
        if (dist > 100) {
            bot.teleportTo(player.getX(), player.getY(), player.getZ());
            currentDescription = "已传送到 " + name;
        } else if (dist > 9) {
            targetPos = player.blockPosition();
            moveToward(targetPos, 2.0);
            currentDescription = "跟随 " + name;
        } else {
            currentDescription = "在 " + name + " 身边待命";
        }
    }

    // ==================== 采集 ====================

    private void executeCollect() {
        ServerLevel level = (ServerLevel) bot.level();
        String target = currentTask.getTarget().toLowerCase();

        BlockPos pos = findNearest(level, bot.blockPosition(), 16,
                state -> {
                    String name = state.getBlock().getName().getString().toLowerCase();
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    return name.contains(target) || id.getPath().contains(target);
                });

        if (pos == null) {
            wander();
            currentTask.setProgress(currentTask.getProgress() + 5);
            if (currentTask.getProgress() >= 100) currentTask.complete("采集完成");
            return;
        }

        targetPos = pos;
        if (moveToward(pos, 2.0)) {
            breakBlock(pos);
            currentTask.setProgress(currentTask.getProgress() + 20);
            if (currentTask.getProgress() >= 100) currentTask.complete("已采集 " + target);
        }
    }

    // ==================== 回家 ====================

    private void executeGoHome() {
        Level level = bot.level();
        BlockPos spawnPos = level.getSharedSpawnPos();
        if (spawnPos == null) {
            currentTask.complete("没有重生点");
            return;
        }

        // 如果不在主世界，回到主世界
        if (!level.dimension().equals(Level.OVERWORLD)) {
            ServerLevel overworld = bot.getServer().overworld();
            bot.teleportToDimension(overworld, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        }

        targetPos = spawnPos;
        double dist = bot.position().distanceTo(Vec3.atCenterOf(spawnPos));
        if (dist < 3) {
            currentTask.complete("已到家");
        } else {
            moveToward(spawnPos, 2.0);
            currentDescription = "回家中... 距离 " + (int) dist;
        }
    }

    // ==================== 下界专用方法 ====================

    private boolean isInNether(Level level) {
        return level.dimension().equals(Level.NETHER);
    }

    private void goToNether() {
        if (MinisBotMod.SERVER == null) return;

        ServerLevel nether = bot.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            currentTask.fail("下界不存在");
            return;
        }

        // 计算下界传送门位置（主世界坐标 / 8）
        BlockPos currentPos = bot.blockPosition();
        BlockPos netherPos = new BlockPos(
                currentPos.getX() / 8,
                Math.min(64, Math.max(8, currentPos.getY() / 2)),
                currentPos.getZ() / 8
        );

        // 或者找最近的传送门
        BlockPos portal = findNearest(nether, netherPos, 64,
                state -> state.getBlock() == Blocks.NETHER_PORTAL);
        if (portal != null) {
            netherPos = portal;
        }

        bot.teleportToDimension(nether, netherPos.getX(), netherPos.getY(), netherPos.getZ());
        currentDescription = "已进入下界，开始寻找远古残骸！";

        // 传送到 Y=15 附近（远古残骸最密集的层）
        bot.teleportTo(netherPos.getX(), 15, netherPos.getZ());
        currentTask.setProgress(5);
    }

    // ==================== 寻路和移动 ====================

    private boolean moveToward(BlockPos target, double stopDistance) {
        Vec3 targetVec = Vec3.atCenterOf(target);
        Vec3 botPos = bot.position();
        double dist = botPos.distanceTo(targetVec);

        if (dist <= stopDistance) {
            bot.setDeltaMovement(Vec3.ZERO);
            bot.getNavigation().stop();
            return true;
        }

        bot.getNavigation().moveTo(target.getX(), target.getY(), target.getZ(), 0.8);
        return false;
    }

    private void breakBlock(BlockPos pos) {
        ServerLevel level = (ServerLevel) bot.level();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        bot.swing(InteractionHand.MAIN_HAND);
        level.destroyBlockProgress(bot.getId(), pos, 10);
        level.destroyBlock(pos, true, bot);
        collectNearbyDrops(level);
    }

    private void collectNearbyDrops(ServerLevel level) {
        var items = level.getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class,
                bot.getBoundingBox().inflate(5));
        for (var item : items) {
            if (item.isAlive()) {
                bot.getBotInventory().addItem(item.getItem());
                item.discard();
            }
        }
    }

    private void wander() {
        Random rand = new Random();
        BlockPos pos = bot.blockPosition();
        int x = pos.getX() + rand.nextInt(20) - 10;
        int z = pos.getZ() + rand.nextInt(20) - 10;
        // 确保目标在合理高度
        int y = pos.getY();
        if (isInNether((ServerLevel) bot.level())) {
            y = Math.min(22, Math.max(8, y));
        }
        bot.getNavigation().moveTo(x, y, z, 0.6);
    }

    // ==================== 挖掘方式 ====================

    private void digDown(ServerLevel level) {
        BlockPos below = bot.blockPosition().below();
        BlockState state = level.getBlockState(below);
        if (!state.isAir() && state.getDestroySpeed(level, below) >= 0) {
            breakBlock(below);
        }
    }

    /** 阶梯式下挖 */
    private void digDownStaircase(ServerLevel level, Direction dir, int step) {
        BlockPos pos = bot.blockPosition();
        for (int i = 0; i < step; i++) {
            // 挖脚下的方块
            BlockPos below = pos.below();
            if (!level.getBlockState(below).isAir()) breakBlock(below);

            // 挖斜前方的方块（形成阶梯）
            BlockPos stepPos = pos.offset(dir.getStepX(), -1, dir.getStepZ());
            if (!level.getBlockState(stepPos).isAir()) breakBlock(stepPos);
        }
    }

    /** 横向挖隧道 */
    private void tunnelHorizontal(ServerLevel level, int length) {
        Direction dir = bot.getDirection();
        BlockPos pos = bot.blockPosition();
        for (int i = 0; i < length; i++) {
            BlockPos forward = pos.offset(dir.getStepX() * i, 0, dir.getStepZ() * i);
            BlockState state = level.getBlockState(forward);
            if (!state.isAir() && state.getDestroySpeed(level, forward) >= 0) {
                breakBlock(forward);
            }
        }
    }

    // ==================== 方块搜索 ====================

    private BlockPos findOre(ServerLevel level, BlockPos center, int radius, String target) {
        target = target.toLowerCase();

        if (target.equals("all") || target.isEmpty()) {
            // 找最高优先级矿石
            return findNearest(level, center, radius, state -> {
                for (String ore : List.of("ancient_debris", "diamond", "emerald",
                        "gold_ore", "iron_ore")) {
                    if (isTargetOre(state, ore)) return true;
                }
                return false;
            });
        }

        return findNearest(level, center, radius, state -> isTargetOre(state, target));
    }

    private boolean isTargetOre(BlockState state, String target) {
        if (state.isAir()) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath().toLowerCase();

        // 远古残骸匹配
        if (target.contains("ancient") || target.contains("debris") || target.contains("残骸")) {
            return path.contains("ancient_debris");
        }

        // 精确/模糊匹配矿石
        if (target.contains("_")) {
            return path.equals(target) || path.equals(target + "_ore")
                    || path.equals("deepslate_" + target + "_ore");
        }

        // 模糊匹配
        return path.contains(target) || path.contains(target + "_ore");
    }

    private BlockPos findNearest(ServerLevel level, BlockPos center, int radius,
                                  java.util.function.Predicate<BlockState> predicate) {
        BlockPos closest = null;
        double closestDist = Double.MAX_VALUE;

        int r = radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (predicate.test(level.getBlockState(pos))) {
                        double dist = center.distSqr(pos);
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = pos;
                        }
                    }
                }
            }
        }
        return closest;
    }

    // ==================== 公共接口 ====================

    public String assignTask(TaskType type, String target) {
        BotTask task = new BotTask(type, target != null ? target : "");
        taskQueue.add(task);
        return "✅ 已添加任务: " + type.name() + " " + (target != null ? target : "");
    }

    public void clearTasks() {
        taskQueue.clear();
        if (currentTask != null && !currentTask.isCompleted()) {
            currentTask.complete("已取消");
        }
        currentTask = null;
        bot.getNavigation().stop();
        targetPos = null;
        currentDescription = "空闲";
    }

    public void onAttacked(LivingEntity attacker) {
        if (attacker != null) {
            taskQueue.clear();
            taskQueue.add(new BotTask(TaskType.FIGHT, attacker.getName().getString()));
            currentDescription = "反击 " + attacker.getName().getString();
        }
    }

    public String getCurrentTaskDescription() { return currentDescription; }
    public int getTaskQueueSize() { return taskQueue.size() + (currentTask != null && !currentTask.isCompleted() ? 1 : 0); }
}
