package com.example.hardcorerespawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ChatUtil {

    private ChatUtil() {}

    /** Converts text with & color codes (e.g. &c&l) into an Adventure Component. */
    public static Component colorize(String raw) {
        if (raw == null) return Component.empty();
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}
