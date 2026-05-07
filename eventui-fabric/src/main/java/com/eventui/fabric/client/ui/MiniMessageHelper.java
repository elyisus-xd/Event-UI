package com.eventui.fabric.client.ui;

import com.google.gson.JsonParser;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.JsonOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MiniMessageHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiniMessageHelper.class);
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private MiniMessageHelper() {}

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        return CACHE.computeIfAbsent(input, MiniMessageHelper::parseInternal);
    }

    private static Component parseInternal(String input) {
        if (!hasMiniMessageTags(input)) {
            return Component.literal(input);
        }
        try {
            var adventureComponent = MINI.deserialize(input);
            String json = GSON_SERIALIZER.serialize(adventureComponent);
            var jsonElement = JsonParser.parseString(json);
            RegistryAccess registryAccess = getRegistryAccess();
            RegistryOps<com.google.gson.JsonElement> ops =
                    RegistryOps.create(JsonOps.INSTANCE, registryAccess);

            var result = ComponentSerialization.CODEC.parse(ops, jsonElement);

            if (result.isError()) {
                LOGGER.warn("[MiniMessage] Codec error for '{}': {}",
                        input, result.error().map(Object::toString).orElse("unknown"));
                return Component.literal(stripTags(input));
            }

            return result.result().orElseGet(() -> {
                LOGGER.warn("[MiniMessage] Codec returned empty for: '{}'", input);
                return Component.literal(stripTags(input));
            });

        } catch (Exception e) {
            LOGGER.warn("[MiniMessage] Exception parsing '{}': {}", input, e.getMessage());
            return Component.literal(stripTags(input));
        }
    }

    private static RegistryAccess getRegistryAccess() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) return mc.level.registryAccess();
            if (mc.getConnection() != null) return mc.getConnection().registryAccess();
        } catch (Exception ignored) {}
        return RegistryAccess.EMPTY;
    }

    public static int width(net.minecraft.client.gui.Font font, String input) {
        return font.width(parse(input));
    }

    public static String stripTags(String input) {
        if (input == null) return "";
        return MINI.stripTags(input);
    }

    public static boolean hasMiniMessageTags(String input) {
        return input != null && input.contains("<") && input.contains(">");
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
