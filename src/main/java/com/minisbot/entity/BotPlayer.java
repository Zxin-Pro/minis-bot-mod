package com.minisbot.entity;

import com.minisbot.MinisBotMod;
import com.minisbot.ai.BotAI;
import com.minisbot.ai.TaskType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * BotPlayer — 模拟真实玩家的机器人实体
 * 拥有背包、AI行为系统、可通过游戏内命令控制
 */
public class BotPlayer extends LivingEntity {
    private static final EntityDataAccessor<String> DATA_BOT_NAME =
            SynchedEntityData.defineId(BotPlayer.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_STATUS =
            SynchedEntityData.defineId(BotPlayer.class, EntityDataSerializers.STRING);

    private final BotInventory inventory = new BotInventory(36);
    private final BotAI ai;
    private String botName = "小助手";

    public BotPlayer(EntityType<? extends BotPlayer> type, Level level) {
        super(type, level);
        this.ai = new BotAI(this);
        this.setCustomName(Component.literal(botName));
        this.setCustomNameVisible(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BOT_NAME, botName);
        builder.define(DATA_STATUS, "空闲");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.BLOCK_INTERACTION_RANGE, 5.0D)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 4.0D);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && isAlive()) {
            ai.tick();
            setStatus(ai.getCurrentTaskDescription());
        }
    }

    // ==================== 命令系统接口 ====================

    public String executeCommand(String cmd) {
        String[] parts = cmd.split(" ", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        return switch (command) {
            case "status" -> getStatusReport();
            case "follow" -> assignTask(TaskType.FOLLOW_PLAYER, args);
            case "mine" -> assignTask(TaskType.MINE, args);
            case "collect" -> assignTask(TaskType.COLLECT, args);
            case "chop" -> assignTask(TaskType.CHOP_TREE, args);
            case "farm" -> assignTask(TaskType.FARM, args);
            case "fight" -> assignTask(TaskType.FIGHT, args);
            case "stop" -> {
                ai.clearTasks();
                yield "✅ 已停止所有任务";
            }
            case "come" -> {
                if (args.isEmpty()) yield "❌ 用法: come <玩家名>";
                tpToPlayer(args);
                yield "✅ 正在前往 " + args;
            }
            case "inv", "inventory" -> "🎒 " + inventory.getFormattedList();
            case "home" -> assignTask(TaskType.GO_HOME, "");
            case "nether" -> assignTask(TaskType.MINE, "ancient_debris");
            case "help" -> getHelpText();
            default -> "❌ 未知命令。输入 help 查看可用命令";
        };
    }

    public String assignTask(TaskType type, String target) {
        return ai.assignTask(type, target);
    }

    public String getStatusReport() {
        String pos = String.format("[%d, %d, %d]",
                blockPosition().getX(), blockPosition().getY(), blockPosition().getZ());
        String dim = level().dimension().location().toString();
        dim = dim.replace("minecraft:", "");
        String health = String.format("%.0f/%.0f", getHealth(), getMaxHealth());

        return String.format(
                "§e=== %s 状态 ===\n" +
                "§f位置: §a%s §7(%s)\n" +
                "§f生命: §c%s\n" +
                "§f任务队列: §b%d\n" +
                "§f当前: §e%s\n" +
                "§f背包: §6%d 组物品",
                botName, pos, dim, health,
                ai.getTaskQueueSize(),
                ai.getCurrentTaskDescription(),
                inventory.getItemCount()
        );
    }

    private String getHelpText() {
        return """
                §e=== MinisBot 命令 ===
                §fhelp §7- 显示帮助
                status §7- 查看状态
                mine <矿物> §7- 挖矿 (diamond/ancient_debris/iron/all)
                chop §7- 砍树
                farm §7- 植树
                fight §7- 战斗/反击
                collect <物品> §7- 采集
                follow <玩家> §7- 跟随
                come <玩家> §7- 传送到玩家身边
                stop §7- 停止所有任务
                inventory §7- 查看背包
                home §7- 回家
                nether §7- 去下界挖远古残骸
                say <消息> §7- 说话
                (前面加 /zxbot 使用，如 /zxbot mine)
                """;
    }

    // ==================== 传送 ====================

    public void tpToPlayer(String playerName) {
        if (MinisBotMod.SERVER == null) return;
        var player = MinisBotMod.SERVER.getPlayerList().getPlayerByName(playerName);
        if (player != null) {
            if (!player.level().dimension().equals(this.level().dimension())) {
                teleportToDimension((ServerLevel) player.level(),
                        player.getX(), player.getY(), player.getZ());
            } else {
                teleportTo(player.getX(), player.getY(), player.getZ());
            }
        }
    }

    public void teleportTo(double x, double y, double z) {
        if (level() instanceof ServerLevel sl) {
            teleportTo(sl, x, y, z, getYRot(), getXRot());
        }
    }

    public void teleportToDimension(ServerLevel targetLevel, double x, double y, double z) {
        if (level() instanceof ServerLevel currentLevel) {
            teleportTo(targetLevel, x, y, z, getYRot(), getXRot());
        }
    }

    // ==================== Getter / Setter ====================

    public void setBotName(String name) {
        this.botName = name;
        entityData.set(DATA_BOT_NAME, name);
        setCustomName(Component.literal(name));
    }

    public void setStatus(String status) {
        entityData.set(DATA_STATUS, status);
    }

    public String getBotStatus() {
        return entityData.get(DATA_STATUS);
    }

    public BotInventory getBotInventory() { return inventory; }
    public BotAI getBotAI() { return ai; }

    public ServerLevel getServerLevel() {
        return (ServerLevel) level();
    }

    // ==================== NBT 持久化 ====================

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("BotName")) botName = tag.getString("BotName");
        inventory.readFromNBT(tag.getCompound("Inventory"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("BotName", botName);
        tag.put("Inventory", inventory.writeToNBT());
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            sp.sendSystemMessage(Component.literal(getStatusReport()));
            return InteractionResult.SUCCESS;
        }
        return super.interactAt(player, vec, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && ai != null) {
            var attacker = source.getEntity();
            if (attacker instanceof LivingEntity living) {
                ai.onAttacked(living);
            }
        }
        return hurt;
    }
}
