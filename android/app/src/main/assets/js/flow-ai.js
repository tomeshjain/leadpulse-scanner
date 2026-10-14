/**
 * Wispr Flow - AI Flow Refinement & Context Transformation Service
 * Powers sub-second AI voice polishing, filler word removal, context adaptation,
 * and custom vocabulary injection using Google Gemini 2.0 Flash.
 */

class FlowAIService {
  constructor() {
    this.geminiBaseUrl = 'https://generativelanguage.googleapis.com/v1beta/models';
  }

  /**
   * Refines raw speech into polished, app-ready text according to the selected Flow Style.
   * Target latency: ~200ms - 400ms.
   */
  async polishSpeech(rawSpeech, mode = 'polish', customPrompt = '') {
    if (!rawSpeech || !rawSpeech.trim()) {
      return '';
    }

    const cleanInput = rawSpeech.trim();
    const apiKey = (window.flowStorage ? window.flowStorage.getGeminiApiKey() : '').trim();
    const model = (window.flowStorage ? window.flowStorage.getSelectedModel() : 'gemini-2.0-flash').replace(/^models\//, '');
    const dictionary = window.flowStorage ? window.flowStorage.getDictionary() : [];

    // If no API key is provided, use our built-in instant rule-based polishing engine
    if (!apiKey) {
      console.log('Wispr Flow: Utilizing built-in instant rule-based polishing engine (No API Key).');
      return this.ruleBasedPolish(cleanInput, mode, dictionary);
    }

    // Build system prompt for the specific Flow Mode
    const systemPrompt = this.buildSystemPrompt(mode, dictionary, customPrompt);
    const userPrompt = `RAW SPOKEN TRANSCRIPT:\n"${cleanInput}"\n\nPOLISHED OUTPUT:`;

    const requestBody = {
      contents: [
        {
          role: 'user',
          parts: [
            { text: systemPrompt + '\n\n' + userPrompt }
          ]
        }
      ],
      generationConfig: {
        temperature: mode === 'command' ? 0.3 : 0.1, // Near-zero temperature for deterministic transcription fidelity
        topP: 0.95,
        maxOutputTokens: 1024
      }
    };

    const startTime = performance.now();
    try {
      const url = `${this.geminiBaseUrl}/${model}:generateContent?key=${encodeURIComponent(apiKey)}`;
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody)
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        const message = errorData.error ? errorData.error.message : `HTTP ${response.status}`;
        console.warn('Gemini Flow API notice, using instant rule-based fallback:', message);
        return this.ruleBasedPolish(cleanInput, mode, dictionary);
      }

      const data = await response.json();
      const candidate = data.candidates && data.candidates[0];
      const outputText = candidate && candidate.content && candidate.content.parts && candidate.content.parts[0]
        ? candidate.content.parts[0].text.trim()
        : '';

      const elapsed = Math.round(performance.now() - startTime);
      console.log(`Wispr Flow: Gemini polished in ${elapsed}ms [${mode}]`);

      return outputText || this.ruleBasedPolish(cleanInput, mode, dictionary);
    } catch (err) {
      console.warn('Network exception during Flow AI, falling back to local polisher:', err);
      return this.ruleBasedPolish(cleanInput, mode, dictionary);
    }
  }

  /**
   * Generates optimized system instructions tailored for the active Flow Style
   */
  buildSystemPrompt(mode, dictionary = [], customPrompt = '') {
    const vocabList = dictionary.map(d => `${d.word} -> ${d.replacement}`).join(', ');
    const vocabDirective = vocabList
      ? `\nCRITICAL USER VOCABULARY & JARGON (Ensure accurate spelling if phonetically heard): [${vocabList}]`
      : '';

    const baseRules = `You are Wispr Flow, the world's most responsive AI voice-to-text dictation engine.
Your sole job is to transform spoken speech into clean, perfectly formatted text ready to be pasted into mobile apps.
DO NOT add conversational filler like "Here is your text" or "Sure". Return ONLY the finished text.
${vocabDirective}`;

    switch (mode) {
      case 'casual':
        return `${baseRules}
MODE: CASUAL CHAT (For WhatsApp, Telegram, Slack DMs, texting).
- Remove all hesitation sounds and filler words ("um", "uh", "like", "you know", "basically", "so yeah", "I mean").
- Keep it natural, friendly, conversational, and concise.
- Add light, fitting emojis where appropriate (e.g. 👍, 😊, 🚀), but do not overdo it.
- Fix grammar, punctuation, and capitalization without making it sound stiff or robotic.`;

      case 'email':
        return `${baseRules}
MODE: EXECUTIVE EMAIL (For Gmail, Outlook, business messaging).
- Strip all filler words, stutters, and rambling.
- Format into a crisp, polished professional email.
- Include an appropriate polite greeting (e.g., "Hi [Name]," or "Hello team,") if context implies a new email.
- Separate distinct points into short paragraphs or bullet points for readability.
- Add a professional sign-off (e.g., "Best regards," or "Thanks,").`;

      case 'notes':
        return `${baseRules}
MODE: STRUCTURED NOTES & BRAIN DUMP (For Notion, Apple Notes, Obsidian).
- Transform rambling spoken thoughts into structured, beautiful Markdown.
- Create a concise title (e.g. "# Project Sync Notes" or "# Ideas on...").
- Use bullet points for key concepts.
- Use "- [ ]" checkboxes for any spoken action items or follow-ups.
- Remove all filler words and disfluencies.`;

      case 'direct':
        return `${baseRules}
MODE: RAW DICTATION (Exact 1:1 fidelity).
- Transcribe the exact words spoken with ZERO creative rewriting or omission.
- Add proper sentence capitalization, commas, periods, and question marks.
- Do NOT rephrase or summarize anything.`;

      case 'code':
        return `${baseRules}
MODE: CODE & DEVELOPER (For IDEs, Terminal, GitHub).
- Identify programming concepts and keywords.
- Format variable names into camelCase or snake_case as implied.
- Format SQL queries with uppercase keywords.
- Wrap code snippets in markdown code blocks with language tags if applicable.
- Output terminal commands on separate lines.`;

      case 'command':
        return `${baseRules}
MODE: VOICE PROMPT & COMMAND EXECUTION (AI Action Mode).
- The user is speaking an INSTRUCTION, PROMPT, or COMMAND to you.
- Execute the request directly and return the final written deliverable.
Examples:
- Spoken: "Draft a polite reply saying I can attend the meeting Thursday at 3 PM" -> Output the reply directly.
- Spoken: "Translate this to Spanish: We are launching the product next week" -> Output the Spanish translation.
- Spoken: "Summarize this in three quick bullet points: [text]" -> Output the three bullet points.`;

      case 'polish':
      default:
        return `${baseRules}
MODE: SMART POLISH (Wispr Flow Default).
- Strip all disfluencies and verbal ticks: "um", "uh", "er", "you know", "like", "basically", "I mean", "so yeah".
- Correct any stutters, false starts, and duplicate words.
- Add natural punctuation (commas, periods, question marks, apostrophes).
- Capitalize proper nouns and sentence beginnings.
- Keep the speaker's original tone, cadence, and exact message intact.`;
    }
  }

  /**
   * Ultra-fast local rule-based polisher for instant offline execution (<5ms)
   */
  ruleBasedPolish(rawText, mode = 'polish', dictionary = []) {
    if (!rawText) return '';

    let text = rawText.trim();

    // 1. Strip common filler words
    const fillers = [
      /\b(um|uh|erm|er|ah)\b/gi,
      /\b(like)\s(?=(you know|basically|i mean))/gi,
      /\b(you know|basically|so yeah|i mean)\b/gi,
      /\b(kind of|sort of)\b(?=\s(like|um))/gi
    ];

    fillers.forEach(pattern => {
      text = text.replace(pattern, '');
    });

    // 2. Remove duplicate words (stutters like "the the", "I I")
    text = text.replace(/\b(\w+)\s+\1\b/gi, '$1');

    // 3. Clean multiple spaces
    text = text.replace(/\s+/g, ' ').trim();

    // 4. Apply custom vocabulary replacements
    if (Array.isArray(dictionary)) {
      dictionary.forEach(d => {
        if (d.word && d.replacement) {
          const reg = new RegExp(`\\b${d.word}\\b`, 'gi');
          text = text.replace(reg, d.replacement);
        }
      });
    }

    // 5. Smart Capitalization
    text = text.replace(/(^\w|[.?!]\s+\w)/g, c => c.toUpperCase());
    text = text.replace(/\bi\b/g, 'I');

    // 6. Inferred punctuation if missing
    if (text && !/[.?!]$/.test(text)) {
      const isQuestion = /^(who|what|where|when|why|how|can|could|would|will|is|are|do|does|did)\b/i.test(text);
      text += isQuestion ? '?' : '.';
    }

    // Mode-specific formatting in rule-based engine
    if (mode === 'email') {
      text = `Hi,\n\n${text}\n\nBest regards,`;
    } else if (mode === 'notes') {
      text = `# Voice Note\n\n• ${text}`;
    }

    return text;
  }

  /**
   * Discovers available Google Gemini models
   */
  async fetchAvailableModels(apiKey) {
    if (!apiKey) throw new Error('API key required');
    const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(apiKey)}`;
    const res = await fetch(url);
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error ? err.error.message : `HTTP ${res.status}`);
    }

    const data = await res.json();
    const models = (data.models || [])
      .filter(m => m.supportedGenerationMethods && m.supportedGenerationMethods.includes('generateContent'))
      .map(m => ({
        id: m.name.replace(/^models\//, ''),
        name: m.displayName || m.name.replace(/^models\//, ''),
        description: m.description
      }));

    return models;
  }
}

// Global Flow AI Singleton
window.flowAI = new FlowAIService();
