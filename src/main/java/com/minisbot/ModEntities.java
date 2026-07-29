package com.minisbot;

import com.minisbot.entity.BotPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MinisBotMod.MOD_ID);

    public static final Supplier<EntityType<BotPlayer>> BOT_PLAYER =
            ENTITIES.register("bot_player",
                    () -> EntityType.Builder.of(BotPlayer::new, MobCategory.MISC)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build("bot_player")
            );

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        MinisBotMod.LOGGER.info("[MinisBot] 实体已注册");
    }
}
