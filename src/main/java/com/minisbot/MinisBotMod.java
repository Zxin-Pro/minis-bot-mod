package com.minisbot;

import com.minisbot.command.MinisBotCommands;
import com.minisbot.entity.BotPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(MinisBotMod.MOD_ID)
public class MinisBotMod {
    public static final String MOD_ID = "minis_bot_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static MinecraftServer SERVER = null;

    public MinisBotMod(IEventBus modEventBus) {
        LOGGER.info("[MinisBot] 初始化纯服务端模组...");

        // 注册实体
        ModEntities.register(modEventBus);

        // 注册 BotPlayer 属性
        modEventBus.addListener(this::registerEntityAttributes);

        // 通用设置
        modEventBus.addListener(this::onCommonSetup);

        // 注册服务端事件
        var gameBus = NeoForge.EVENT_BUS;

        // 注册游戏内命令
        gameBus.addListener(this::onRegisterCommands);

        // 服务端生命周期
        gameBus.addListener(this::onServerStarted);
        gameBus.addListener(this::onServerStopping);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("[MinisBot] 通用设置完成");
    }

    private void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BOT_PLAYER.get(), BotPlayer.createAttributes().build());
        LOGGER.info("[MinisBot] BotPlayer 属性已注册");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        MinisBotCommands.register(event.getDispatcher());
        LOGGER.info("[MinisBot] 命令已注册");
    }

    private void onServerStarted(ServerStartedEvent event) {
        SERVER = event.getServer();
        LOGGER.info("[MinisBot] 服务端就绪");
    }

    private void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[MinisBot] 服务端关闭");
    }
}
