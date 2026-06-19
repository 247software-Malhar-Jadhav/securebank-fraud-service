package com.securebank.fraud.support;

/**
 * The locales the platform supports end-to-end: English, Hindi, Marathi.
 *
 * <p>Centralising the parsing here means every localized surface (assistant answers,
 * insights summaries, deterministic fallbacks) resolves an incoming locale string the
 * same way, defaulting to English on anything unrecognised.</p>
 */
public enum SupportedLocale {
    EN, HI, MR;

    /** Lenient parse: "hi", "HI", "hi-IN" -> HI; anything unknown/blank -> EN. */
    public static SupportedLocale parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EN;
        }
        String lang = raw.trim().toLowerCase();
        if (lang.startsWith("hi")) {
            return HI;
        }
        if (lang.startsWith("mr")) {
            return MR;
        }
        return EN;
    }
}
