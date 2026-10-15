package com.wisprflow.keyboard;

/**
 * Defines voice transformation flow styles and their system prompt directives.
 */
public enum FlowMode {
    POLISH("polish", "✨ Polish", "Strips filler words, fixes stutters, adds punctuation."),
    CASUAL("casual", "💬 Casual", "Natural, friendly, with fitting emojis for WhatsApp & DMs."),
    EMAIL("email", "✉️ Email", "Structured professional email with greeting & sign-off."),
    NOTES("notes", "📝 Notes", "Structured Markdown bullets and checklist items."),
    DIRECT("direct", "⚡ Raw", "Exact 1:1 transcription with punctuation."),
    CODE("code", "💻 Code", "Formats camelCase, SQL queries, and code blocks."),
    COMMAND("command", "🪄 Action", "Executes spoken instructions directly.");

    private final String id;
    private final String displayName;
    private final String description;

    FlowMode(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static FlowMode fromId(String id) {
        if (id == null) return POLISH;
        for (FlowMode mode : values()) {
            if (mode.id.equalsIgnoreCase(id)) {
                return mode;
            }
        }
        return POLISH;
    }

    public String getSystemPrompt() {
        String base = "You are Smart AI Keyboard, an ultra-fast voice-to-text dictation engine. " +
                "Transform the spoken transcript into clean, perfectly formatted text ready to paste. " +
                "DO NOT add conversational responses like 'Here is your text'. Return ONLY the finished text.\n" +
                "CRITICAL NUMBERS RULE: When consecutive digits or numbers are spoken (e.g. phone numbers, OTPs, PINs, IDs, 'nine eight seven six', '5 4 3 2 1'), format them as continuous digits WITHOUT spaces in between (e.g. '9876', '54321').\n\n";

        switch (this) {
            case CASUAL:
                return base + "MODE: CASUAL CHAT (For WhatsApp, Slack, texting).\n" +
                        "- Remove filler words ('um', 'uh', 'like', 'you know', 'basically', 'I mean').\n" +
                        "- Friendly, conversational, natural tone with light fitting emojis where appropriate.\n" +
                        "- Fix grammar and punctuation.";
            case EMAIL:
                return base + "MODE: EXECUTIVE EMAIL (For Gmail, Outlook).\n" +
                        "- Strip all filler words and disfluencies.\n" +
                        "- Format into a crisp professional email with greeting, short paragraphs, and sign-off.";
            case NOTES:
                return base + "MODE: STRUCTURED NOTES (For Notion, Apple Notes).\n" +
                        "- Structure thoughts into Markdown with bullet points.\n" +
                        "- Use '- [ ]' for action items or follow-ups.";
            case DIRECT:
                return base + "MODE: RAW DICTATION.\n" +
                        "- Exact 1:1 words spoken with correct capitalization and punctuation. Do not rephrase.";
            case CODE:
                return base + "MODE: CODE & DEVELOPER.\n" +
                        "- Format variable names into camelCase or snake_case as implied. Uppercase SQL keywords.";
            case COMMAND:
                return base + "MODE: AI ACTION / PROMPT.\n" +
                        "- The user is speaking a command or prompt to you. Execute it directly and output the deliverable.";
            case POLISH:
            default:
                return base + "MODE: SMART POLISH (Smart AI Default).\n" +
                        "- Strip all verbal ticks: 'um', 'uh', 'you know', 'like', 'basically', 'I mean'.\n" +
                        "- Fix stutters and false starts.\n" +
                        "- Add natural punctuation and capitalization while preserving exact original meaning.";
        }
    }

    public String buildPrompt(String rawSpeech) {
        return getSystemPrompt() + "\n\nSpoken transcript to process:\n\"\"\"\n" + (rawSpeech != null ? rawSpeech : "") + "\n\"\"\"";
    }
}
