/**
 * LeadPulse - Gemini Multimodal Vision & Career Enrichment Service
 * Connects to Google Generative Language API (v1beta) to perform OCR
 * on visiting cards, discover models dynamically, and synthesize
 * structured past organization history for enterprise matching.
 * 
 * STRICT POLICY: Zero dummy profiles. Only genuine AI extraction or clean manual entry.
 */

class GeminiService {
  constructor() {
    this.baseEndpoint = 'https://generativelanguage.googleapis.com/v1beta/models';
  }

  /**
   * Dynamically query all available Gemini models for the user's API key
   * Filters for models supporting multimodal generateContent (vision).
   */
  async fetchAvailableModels(apiKey) {
    if (!apiKey) return [];

    const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(apiKey)}`;
    const response = await fetch(url);

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      throw new Error(err.error ? err.error.message : `Failed to list models: HTTP ${response.status}`);
    }

    const data = await response.json();
    if (!data.models || !Array.isArray(data.models)) {
      return [];
    }

    const visionCapable = data.models.filter(m => {
      const methods = m.supportedGenerationMethods || [];
      return methods.includes('generateContent') && !m.name.includes('embedding');
    });

    const formatted = visionCapable.map(m => {
      const cleanId = m.name.replace(/^models\//, '');
      return {
        id: cleanId,
        displayName: m.displayName || cleanId,
        description: m.description || '',
        isFlash: cleanId.includes('flash'),
        isPro: cleanId.includes('pro')
      };
    });

    formatted.sort((a, b) => {
      const score = (m) => {
        if (m.id === 'gemini-2.0-flash') return 100;
        if (m.id === 'gemini-1.5-flash') return 90;
        if (m.id === 'gemini-1.5-flash-latest') return 85;
        if (m.id === 'gemini-1.5-pro') return 80;
        if (m.isFlash) return 70;
        if (m.isPro) return 60;
        return 10;
      };
      return score(b) - score(a);
    });

    if (formatted.length > 0) {
      window.storageService.setAvailableModels(formatted);
    }

    return formatted;
  }

  /**
   * Main scan & enrich function
   * @param {string} imageBase64 - Raw base64 data url
   * @param {string} [customApiKey] - Optional API key
   * @param {string} [customModel] - Optional specific model ID
   */
  async scanAndEnrichVisitingCard(imageBase64, customApiKey = null, customModel = null) {
    const apiKey = customApiKey || window.storageService.getGeminiApiKey();

    if (!apiKey) {
      console.warn('No Gemini API key found. Providing clean manual review template.');
      return this.createCleanManualTemplate(imageBase64, 'Please set your Gemini API key in Settings for automated AI card reading and background intelligence.');
    }

    const preferredModel = customModel || window.storageService.getSelectedModel() || 'gemini-1.5-flash';
    const fallbackCandidates = [
      preferredModel,
      'gemini-2.0-flash',
      'gemini-1.5-flash',
      'gemini-1.5-flash-latest',
      'gemini-1.5-pro',
      'gemini-2.5-flash'
    ];
    const modelQueue = [...new Set(fallbackCandidates)];

    let lastError = null;

    for (const modelId of modelQueue) {
      try {
        console.log(`Attempting visiting card OCR with model: ${modelId}`);
        const result = await this.callGeminiVisionAPI(imageBase64, apiKey, modelId);
        
        if (modelId !== preferredModel) {
          console.log(`Auto-switched preferred model to working model: ${modelId}`);
          window.storageService.setSelectedModel(modelId);
          if (window.app && window.app.showToast) {
            window.app.showToast(`Active model: ${modelId}`, 'info');
          }
        }
        return result;
      } catch (error) {
        lastError = error;
        console.warn(`Model ${modelId} failed: ${error.message}. Checking next candidate...`);
        if (error.message.includes('API_KEY_INVALID') || error.message.includes('RESOURCE_EXHAUSTED')) {
          break;
        }
      }
    }

    console.error('All Gemini API models failed:', lastError);
    return this.createCleanManualTemplate(
      imageBase64, 
      `AI Service Note: ${lastError ? lastError.message : 'Model temporarily unavailable'}. Card image attached for manual review.`
    );
  }

  /**
   * Calls Google Gemini Vision API for a specific model ID
   */
  async callGeminiVisionAPI(imageBase64, apiKey, modelId) {
    const cleanModel = modelId.replace(/^models\//, '');
    const endpoint = `${this.baseEndpoint}/${cleanModel}:generateContent?key=${encodeURIComponent(apiKey)}`;

    const matches = imageBase64.match(/^data:([a-zA-Z0-9]+\/[a-zA-Z0-9-.+]+);base64,(.+)$/);
    let mimeType = 'image/jpeg';
    let rawBase64 = imageBase64;

    if (matches && matches.length === 3) {
      mimeType = matches[1];
      rawBase64 = matches[2];
    }

    const customPromptInstructions = window.storageService.getCustomPromptInstructions();

    const prompt = `
You are an elite executive sales intelligence assistant and OCR engine for business cards.
Examine this visiting/business card image with extreme precision.

Extract all printed contact details and synthesize accurate professional enrichment.
Crucially, extract or infer any PAST ORGANIZATIONS, PREVIOUS EMPLOYERS, or ALUMNI TIES (e.g., Ex-Google, Ex-Infosys, Ex-Oracle, BITS Pilani Alumni, IIT Alumni) mentioned on the card or inferred from their executive seniority.

Return strictly a valid JSON object matching EXACTLY this schema (no markdown formatting, no codeblock markers):
{
  "name": "Full Name",
  "honorific": "Mr./Ms./Dr./Prof. or empty",
  "title": "Designation / Role",
  "company": "Current Company / Organization Name",
  "industry": "Industry Sector (e.g., Enterprise SaaS, FinTech, Industrial IoT, HealthTech)",
  "email": "Email address",
  "phone": "Direct phone number with country code",
  "whatsapp": "WhatsApp number (digits with +)",
  "website": "Company website URL",
  "address": "Office or headquarters address",
  "linkedinUrl": "LinkedIn profile or company search URL (e.g. https://www.linkedin.com/in/firstname-lastname)",
  
  "pastWorkSummary": "A concise 2-3 sentence executive summary of this person's professional career background, past employers, and operational scope.",
  
  "pastOrganizations": [
    {
      "companyName": "Former Organization Name (e.g., Oracle, Infosys, Cisco, TCS, Google, Microsoft, Accenture)",
      "role": "Former Role (e.g. Senior Director, Lead Architect, Project Manager)",
      "duration": "e.g. 2018-2022 or Estimated",
      "notes": "Brief notes on their domain or department"
    }
  ],
  "pastCompaniesList": ["Former Company 1", "Former Company 2"],
  "alumniInstitutions": ["University / College name if known"],

  "companyOverview": "A concise 2-sentence overview of this company's business model and value proposition.",
  "salesIcebreakers": [
    "Relevant conversational icebreaker 1 tailored for a sales meeting",
    "Relevant conversational icebreaker 2 tailored to their industry"
  ],
  "suggestedActionItems": [
    "Follow-up action item 1 (e.g., Send enterprise capability deck)",
    "Follow-up action item 2 (e.g., Schedule discovery call)"
  ],
  "decisionStatus": "Decision Maker / Influencer / Evaluator",
  "leadScore": "Hot / Warm / Nurture"
}

${customPromptInstructions ? `Additional User Instructions: ${customPromptInstructions}` : ''}
`;

    const requestBody = {
      contents: [
        {
          parts: [
            { text: prompt },
            {
              inline_data: {
                mime_type: mimeType,
                data: rawBase64
              }
            }
          ]
        }
      ],
      generationConfig: {
        temperature: 0.15,
        topK: 32,
        topP: 0.95,
        maxOutputTokens: 1800
      }
    };

    const response = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const errJson = await response.json().catch(() => ({}));
      const msg = errJson.error ? errJson.error.message : `HTTP error ${response.status}`;
      throw new Error(msg);
    }

    const data = await response.json();
    const candidate = data.candidates && data.candidates[0];
    if (!candidate || !candidate.content || !candidate.content.parts || !candidate.content.parts[0]) {
      throw new Error('Invalid response structure received from Gemini API');
    }

    const rawText = candidate.content.parts[0].text.trim();
    return this.parseGeminiJsonResponse(rawText);
  }

  /**
   * Sanitizes and parses Gemini output
   */
  parseGeminiJsonResponse(text) {
    let cleanJson = text;
    if (cleanJson.includes('```json')) {
      cleanJson = cleanJson.split('```json')[1].split('```')[0].trim();
    } else if (cleanJson.includes('```')) {
      cleanJson = cleanJson.split('```')[1].split('```')[0].trim();
    }

    try {
      const parsed = JSON.parse(cleanJson);
      if (!Array.isArray(parsed.pastOrganizations)) parsed.pastOrganizations = [];
      if (!Array.isArray(parsed.pastCompaniesList)) {
        parsed.pastCompaniesList = parsed.pastOrganizations.map(o => o.companyName).filter(Boolean);
      }
      if (!Array.isArray(parsed.alumniInstitutions)) parsed.alumniInstitutions = [];
      if (!Array.isArray(parsed.salesIcebreakers)) parsed.salesIcebreakers = [];
      if (!Array.isArray(parsed.suggestedActionItems)) parsed.suggestedActionItems = [];
      return parsed;
    } catch (e) {
      console.error('JSON parsing failed, attempting regex extraction:', e);
      return this.fallbackRegexExtraction(text);
    }
  }

  fallbackRegexExtraction(text) {
    const extract = (pattern) => {
      const m = text.match(pattern);
      return m ? m[1].trim() : '';
    };

    return {
      name: extract(/"name":\s*"([^"]+)"/) || '',
      honorific: extract(/"honorific":\s*"([^"]+)"/),
      title: extract(/"title":\s*"([^"]+)"/) || '',
      company: extract(/"company":\s*"([^"]+)"/) || '',
      industry: extract(/"industry":\s*"([^"]+)"/) || '',
      email: extract(/"email":\s*"([^"]+)"/),
      phone: extract(/"phone":\s*"([^"]+)"/),
      whatsapp: extract(/"whatsapp":\s*"([^"]+)"/),
      website: extract(/"website":\s*"([^"]+)"/),
      address: extract(/"address":\s*"([^"]+)"/),
      linkedinUrl: extract(/"linkedinUrl":\s*"([^"]+)"/),
      pastWorkSummary: extract(/"pastWorkSummary":\s*"([^"]+)"/) || '',
      pastOrganizations: [],
      pastCompaniesList: [],
      alumniInstitutions: [],
      companyOverview: extract(/"companyOverview":\s*"([^"]+)"/) || '',
      salesIcebreakers: [
        'Discuss current infrastructure goals for the upcoming quarter.',
        'Explore how streamlined field workflows can improve their operational turnaround.'
      ],
      suggestedActionItems: [
        'Send introductory follow-up email with company deck',
        'Connect on LinkedIn with tailored note'
      ],
      decisionStatus: 'Evaluator',
      leadScore: 'Warm'
    };
  }

  /**
   * Generates a clean empty template for manual review when no API key or offline.
   * GUARANTEE: NEVER injects dummy names, dummy phones, or fake companies.
   */
  createCleanManualTemplate(imageBase64, warningMessage = '') {
    return {
      name: '',
      honorific: '',
      title: '',
      company: '',
      industry: '',
      email: '',
      phone: '',
      whatsapp: '',
      website: '',
      address: '',
      linkedinUrl: '',
      pastWorkSummary: '',
      pastOrganizations: [],
      pastCompaniesList: [],
      alumniInstitutions: [],
      companyOverview: '',
      salesIcebreakers: [],
      suggestedActionItems: [
        'Send follow-up email with product overview deck',
        'Schedule discovery meeting'
      ],
      decisionStatus: 'Evaluator',
      leadScore: 'Warm',
      _warning: warningMessage,
      _isManual: true
    };
  }
}

// Global Singleton Initialization
window.geminiService = new GeminiService();
