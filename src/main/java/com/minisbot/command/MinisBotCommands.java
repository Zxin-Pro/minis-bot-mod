package com.minisbot.command;

import com.minisbot.MinisBotMod;
import com.minisbot.ai.TaskType;
import com.minisbot.entity.BotPlayer;
import com.minisbot.entity.ModEntities;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * /zxbot 命令 — 控制机器人
 */
public class MinisBotCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zxbot")
                .requires(src -> src.hasPermission(0)) // 所有人可用
                .then(Commands.literal("spawn")
                        .executes(ctx -> spawnBot(ctx.getSource(), "小助手"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> spawnBot(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx.getSource())))
                .then(Commands.literal("stop")
                        .executes(ctx -> stopBot(ctx.getSource())))
                .then(Commands.literal("mine")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .executes(ctx -> commandBot(ctx.getSource(), "mine",
                                        StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("chop")
                        .executes(ctx -> commandBot(ctx.getSource(), "chop", "")))
                .then(Commands.literal("farm")
                        .executes(ctx -> commandBot(ctx.getSource(), "farm", "")))
                .then(Commands.literal("fight")
                        .executes(ctx -> commandBot(ctx.getSource(), "fight", "")))
                .then(Commands.literal("follow")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> commandBot(ctx.getSource(), "follow",
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("come")
                        .executes(ctx -> commandBot(ctx.getSource(), "come",
                                ctx.getSource().getPlayerOrException().getName().getString()))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> commandBot(ctx.getSource(), "come",
                                        StringArgumentType.getString(ctx, "player")))))
                .then(Commands.literal("collect")
                        .then(Commands.argument("target", StringArgumentType.greedyString())
                                .executes(ctx -> commandBot(ctx.getSource(), "collect",
                                        StringArgumentType.getString(ctx, "target")))))
                .then(Commands.literal("inv")
                        .executes(ctx -> commandBot(ctx.getSource(), "inventory", "")))
                .then(Commands.literal("inventory")
                        .executes(ctx -> commandBot(ctx.getSource(), "inventory", "")))
                .then(Commands.literal("home")
                        .executes(ctx -> commandBot(ctx.getSource(), "home", "")))
                .then(Commands.literal("nether")
                        .executes(ctx -> commandBot(ctx.getSource(), "nether", "")))
                .then(Commands.literal("help")
                        .executes(ctx -> showHelp(ctx.getSource())))
                .then(Commands.literal("say")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String msg = StringArgumentType.getString(ctx, "message");
                                    ctx.getSource().getServer().getPlayerList()
                                            .broadcastSystemMessage(
                                                    Component.literal("§7[机器人] " + msg), false);
                                    return 1;
                                })))
                .then(Commands.literal("tp")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> {
                                    BotPlayer bot = findBot(ctx.getSource());
                                    if (bot == null) {
                                        ctx.getSource().sendFailure(
                                                Component.literal("❌ 机器人不存在"));
                                        return 0;
                                    }
                                    Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                    bot.teleportTo(pos.x, pos.y, pos.z);
                                    ctx.getSource().sendSuccess(() ->
                                            Component.literal("✅ 已传送机器人"), true);
                                    return 1;
                                })))
        );
    }

    private static int spawnBot(CommandSourceStack source, String name) {
        BotPlayer existing = findBot(source);
        if (existing != null) {
            source.sendFailure(Component.literal("❌ 机器人已存在"));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();

        BotPlayer bot = new BotPlayer(ModEntities.BOT_PLAYER.get(), level);
        bot.setPos(pos.x, pos.y, pos.z);
        bot.setBotName(name);
        level.addFreshEntity(bot);

        source.sendSuccess(() -> Component.literal("✅ 机器人 '" + name + "' 已生成"), true);
        MinisBotMod.LOGGER.info("[MinisBot] 机器人 '{}' 在 {} 被召唤", name, pos);
        return 1;
    }

    private static int showStatus(CommandSourceStack source) {
        BotPlayer bot = findBot(source);
        if (bot == null) {
            source.sendFailure(Component.literal("❌ 没有机器人，使用 /zxbot spawn 召唤"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(bot.getStatusReport()), false);
        return 1;
    }

    private static int stopBot(CommandSourceStack source) {
        BotPlayer bot = findBot(source);
        if (bot == null) {
            source.sendFailure(Component.literal("❌ 没有机器人"));
            return 0;
        }
        bot.getBotAI().clearTasks();
        source.sendSuccess(() -> Component.literal("✅ 已停止所有任务"), true);
        return 1;
    }

    private static int commandBot(CommandSourceStack source, String cmd, String args) {
        BotPlayer bot = findBot(source);
        if (bot == null) {
            source.sendFailure(Component.literal("❌ 没有机器人，使用 /zxbot spawn 召唤"));
            return 0;
        }

        String fullCmd = args.isEmpty() ? cmd : cmd + " " + args;
        String result = bot.executeCommand(fullCmd);
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int showHelp(CommandSourceStack source) {
        BotPlayer bot = findBot(source);
        String help = bot != null ? bot.executeCommand("help") :
                """
                §e=== MinisBot 帮助 ===
                §f/zxbot spawn [名字] §7- 生成机器人
                §f/zxbot help §7- 显示帮助
                """;
        source.sendSuccess(() -> Component.literal(help), false);
        return 1;
    }

    private static BotPlayer findBot(CommandSourceStack source) {
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof BotPlayer bot) {
                    return bot;
                }
            }
        }
        return null;
    }
}
