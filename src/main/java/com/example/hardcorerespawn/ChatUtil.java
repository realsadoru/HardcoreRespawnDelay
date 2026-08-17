package com.example.hardcorerespawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ChatUtil {

    private ChatUtil() {}

    /** Zamienia tekst z kodami kolorów & (np. &c&l) na komponent Adventure. */
    public static Component colorize(String raw) {
        if (raw == null) return Component.empty();
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}
