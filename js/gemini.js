/**
 * LeadPulse Gemini AI Service
 * Powered by Google Gemini 1.5/2.0/2.5 Multimodal Flash & Pro Models
 * Performs Visiting Card OCR + Contact Extraction + LinkedIn & Past Work Enrichment
 * Includes Dynamic Model Discovery, Selection & Self-Healing Model Fallbacks.
 */

class GeminiService {
  constructor() {
    this.baseEndpoint = 'https://generativelanguage.googleapis.com/v1beta/models';
  }

  /**
   * Fetches real list of available models supported by the user's specific API key
   * Filters for models that support multimodal generateContent
   */
  async fetchAvailableModels(customApiKey = null) {
    const apiKey = customApiKey || window.storageService.getGeminiApiKey();
    if (!apiKey) {
      throw new Error('Please enter a Google Gemini API Key first.');
    }

    const url = `https://generativelanguage.googleapis.com/v1beta/models?key=${encodeURIComponent(apiKey)}`;
    const response = await fetch(url);

    if (!response.ok) {
      const err = await response.json().catch(() => ({}));
      const msg = err.error ? err.error.message : `HTTP ${response.status}`;
      throw new Error(`Failed to fetch models from Google: ${msg}`);
    }

    const data = await response.json();
    const rawModels = data.models || [];

    // Filter models supporting generateContent and vision
    const contentModels = rawModels.filter(m => {
      const methods = m.supportedGenerationMethods || [];
      return methods.includes('generateContent') && !m.name.includes('embedding') && !m.name.includes('aqa');
    });

    // Format clean model objects
    const formatted = contentModels.map(m => {
      const cleanId = m.name.replace(/^models\//, '');
      const isFlash = cleanId.includes('flash');
      const isPro = cleanId.includes('pro');
      return {
        id: cleanId,
        name: `${m.displayName || cleanId} [${cleanId}]`,
        description: m.description || '',
        isRecommended: isFlash
      };
    });

    // Sort: flash models first, then pro, then others
    formatted.sort((a, b) => {
      if (a.isRecommended && !b.isRecommended) return -1;
      if (!a.isRecommended && b.isRecommended) return 1;
      return a.id.localeCompare(b.id);
    });

    if (formatted.length > 0) {
      window.storageService.setAvailableModels(formatted);
    }

    return formatted;
  }

  /**
   * Main scan & enrich function
   * @param {string} imageBase64 - Raw base64 data url (data:image/jpeg;base64,...)
   * @param {string} [customApiKey] - Optional API key
   * @param {string} [customModel] - Optional specific model ID
   */
  async scanAndEnrichVisitingCard(imageBase64, customApiKey = null, customModel = null) {
    const apiKey = customApiKey || window.storageService.getGeminiApiKey();

    if (!apiKey) {
      console.warn('No Gemini API key found. Using intelligent demo simulation fallback.');
      return this.simulateCardExtraction(imageBase64);
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
    // Remove duplicates
    const modelQueue = [...new Set(fallbackCandidates)];

    let lastError = null;

    for (const modelId of modelQueue) {
      try {
        console.log(`Attempting visiting card OCR with model: ${modelId}`);
        const result = await this.callGeminiVisionAPI(imageBase64, apiKey, modelId);
        
        // If we succeeded with a fallback model different from saved, update setting
        if (modelId !== preferredModel) {
          console.log(`Auto-switched preferred model to working model: ${modelId}`);
          window.storageService.setSelectedModel(modelId);
          if (window.app && window.app.showToast) {
            window.app.showToast(`Auto-switched to active model: ${modelId}`, 'info');
          }
        }
        return result;
      } catch (error) {
        lastError = error;
        console.warn(`Model ${modelId} failed: ${error.message}. Checking next candidate...`);
        // If the error was NOT a 404 / model not found (e.g. invalid API key or quota exceeded), don't keep cycling all models
        if (error.message.includes('API_KEY_INVALID') || error.message.includes('RESOURCE_EXHAUSTED')) {
          break;
        }
      }
    }

    console.error('All Gemini API models failed, falling back to simulated analysis:', lastError);
    const simulated = await this.simulateCardExtraction(imageBase64);
    simulated._warning = `Live API note: ${lastError ? lastError.message : 'Model unavailable'}. Loaded offline intelligence mode. (Go to Settings to select a verified model).`;
    return simulated;
  }

  /**
   * Calls Google Gemini Vision API for a specific model ID
   */
  async callGeminiVisionAPI(imageBase64, apiKey, modelId) {
    const cleanModel = modelId.replace(/^models\//, '');
    const endpoint = `${this.baseEndpoint}/${cleanModel}:generateContent?key=${encodeURIComponent(apiKey)}`;

    // Extract raw base64 data and mimeType
    const matches = imageBase64.match(/^data:([a-zA-Z0-9]+\/[a-zA-Z0-9-.+]+);base64,(.+)$/);
    let mimeType = 'image/jpeg';
    let rawBase64 = imageBase64;

    if (matches && matches.length === 3) {
      mimeType = matches[1];
      rawBase64 = matches[2];
    }

    const customPromptInstructions = window.storageService.getCustomPrompt();

    const prompt = `
You are an executive sales intelligence agent and business card OCR expert.
Thoroughly examine the provided visiting/business card image.
Extract all contact details and generate comprehensive professional background enrichment.

Return strictly a valid JSON object with EXACTLY this structure (no markdown fences, no extra text):
{
  "name": "Full Name",
  "honorific": "Mr./Ms./Dr./Prof. or empty",
  "title": "Designation / Role",
  "company": "Company Name",
  "industry": "Industry Sector (e.g., Enterprise SaaS, FinTech, Industrial Automation)",
  "email": "Email address",
  "phone": "Direct phone number with country code",
  "whatsapp": "WhatsApp number (digits with +)",
  "website": "Company website URL",
  "address": "Office or headquarters address",
  "linkedinUrl": "LinkedIn profile or company search URL (e.g. https://www.linkedin.com/in/firstname-lastname)",
  "pastWorkSummary": "A detailed 2-4 sentence executive summary of this person's likely past career, achievements, and leadership scale.",
  "companyOverview": "A concise 2-3 sentence overview of this company's business model, solutions, and enterprise value proposition.",
  "salesIcebreakers": [
    "Conversational icebreaker 1 tailored for a sales meeting",
    "Conversational icebreaker 2 tailored to their industry"
  ],
  "suggestedActionItems": [
    "Suggested follow-up action item 1 (e.g., Send enterprise product overview deck)",
    "Suggested follow-up action item 2 (e.g., Book 30-min discovery call)"
  ],
  "decisionStatus": "Decision Maker / Influencer / Evaluator",
  "leadScore": "Hot / Warm / Nurture"
}

${customPromptInstructions ? `Additional user instructions: ${customPromptInstructions}` : ''}
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
        temperature: 0.2,
        topK: 32,
        topP: 0.95,
        maxOutputTokens: 1500
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
   * Sanitizes and parses Gemini output (handling markdown codeblocks)
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
      if (!Array.isArray(parsed.salesIcebreakers)) parsed.salesIcebreakers = [];
      if (!Array.isArray(parsed.suggestedActionItems)) parsed.suggestedActionItems = [];
      return parsed;
    } catch (e) {
      console.error('JSON parsing failed, attempting regex extraction:', e);
      return this.fallbackRegexExtraction(text);
    }
  }

  /**
   * Regex fallback if LLM returned non-JSON prose
   */
  fallbackRegexExtraction(text) {
    const extract = (pattern) => {
      const m = text.match(pattern);
      return m ? m[1].trim() : '';
    };

    return {
      name: extract(/"name":\s*"([^"]+)"/) || 'Extracted Lead',
      honorific: extract(/"honorific":\s*"([^"]+)"/),
      title: extract(/"title":\s*"([^"]+)"/) || 'Executive',
      company: extract(/"company":\s*"([^"]+)"/) || 'Enterprise Partner',
      industry: extract(/"industry":\s*"([^"]+)"/) || 'Technology',
      email: extract(/"email":\s*"([^"]+)"/),
      phone: extract(/"phone":\s*"([^"]+)"/),
      whatsapp: extract(/"whatsapp":\s*"([^"]+)"/),
      website: extract(/"website":\s*"([^"]+)"/),
      address: extract(/"address":\s*"([^"]+)"/),
      linkedinUrl: extract(/"linkedinUrl":\s*"([^"]+)"/),
      pastWorkSummary: extract(/"pastWorkSummary":\s*"([^"]+)"/) || 'Senior executive with extensive domain background.',
      companyOverview: extract(/"companyOverview":\s*"([^"]+)"/) || 'Enterprise solution provider.',
      salesIcebreakers: [
        'Ask about their primary infrastructure priorities for the coming fiscal quarter.',
        'Explore how our mobile offline workflows can accelerate their field operations.'
      ],
      suggestedActionItems: [
        'Send introductory email with solution brief',
        'Connect on LinkedIn with customized message'
      ]
    };
  }

  /**
   * High-fidelity realistic simulation fallback when offline or no API key
   */
  async simulateCardExtraction(imageBase64) {
    await new Promise(r => setTimeout(r, 900));

    const sampleProfiles = [
      {
        name: 'Vikramaditya Sharma',
        honorific: 'Mr.',
        title: 'Vice President of Engineering',
        company: 'Nexora Cloud Systems',
        industry: 'Cloud Infrastructure & AI',
        email: 'vikram.sharma@nexoracloud.io',
        phone: '+91 98201 45872',
        whatsapp: '+919820145872',
        website: 'https://nexoracloud.io',
        address: 'Cyber City, Tower B, Gurugram, India',
        linkedinUrl: 'https://www.linkedin.com/in/vikramaditya-sharma-cloud',
        pastWorkSummary: '14+ years spearheading hybrid cloud scalability and containerized architectures. Previously Principal Architect at Infosys FinTech Labs and Senior Lead at Cisco Systems. Led high-throughput API gateway processing 50M+ requests/day.',
        companyOverview: 'Nexora builds enterprise-grade multi-cloud observability and zero-trust orchestration for Fortune 500 banks.',
        salesIcebreakers: [
          'Ask about their recent migration to Kubernetes 1.31 and latency milestones.',
          'Discuss how our solution reduces cloud egress spend during peak database queries.'
        ],
        suggestedActionItems: [
          'Share custom Enterprise security whitepaper',
          'Book 30-min discovery demo with Solutions Architect'
        ],
        decisionStatus: 'Decision Maker',
        leadScore: 'Hot'
      },
      {
        name: 'Dr. Ananya Deshmukh',
        honorific: 'Dr.',
        title: 'Chief Product Officer',
        company: 'AuraHealth Intelligence',
        industry: 'HealthTech & MedTech AI',
        email: 'ananya.d@aurahealth.ai',
        phone: '+91 91673 89210',
        whatsapp: '+919167389210',
        website: 'https://aurahealth.ai',
        address: 'Indiranagar 100ft Road, Bengaluru, India',
        linkedinUrl: 'https://www.linkedin.com/in/ananya-deshmukh-healthtech',
        pastWorkSummary: 'Former Head of AI Product at GE Healthcare South Asia. Holds a Ph.D. in Biomedical Informatics from Stanford. Led the FDA-cleared triage algorithm for cardiac imaging deployed across 120+ diagnostic hospitals.',
        companyOverview: 'AuraHealth provides clinical workflow copilots that automate patient discharge summaries and clinician EHR documentation with HIPAA & GDPR compliance.',
        salesIcebreakers: [
          'Compliment her keynote speech on reducing physician burnout using ambient voice AI.',
          'Explore potential pilot integration with our offline-capable mobile workflow platform.'
        ],
        suggestedActionItems: [
          'Send draft mutual Non-Disclosure Agreement (NDA)',
          'Follow-up regarding hospital pilot cohort size'
        ],
        decisionStatus: 'Decision Maker',
        leadScore: 'Hot'
      },
      {
        name: 'Rajiv Menon',
        honorific: 'Mr.',
        title: 'Chief Operating Officer',
        company: 'RoboGrid Industrial IoT',
        industry: 'Industrial Automation & Robotics',
        email: 'rmenon@robogrid.de',
        phone: '+49 152 9847120',
        whatsapp: '+491529847120',
        website: 'https://robogrid.de',
        address: 'Siemensdamm 50, 13629 Berlin, Germany',
        linkedinUrl: 'https://www.linkedin.com/in/rajiv-menon-robogrid',
        pastWorkSummary: '18 years leading manufacturing operational excellence and Industry 4.0 sensor telemetry. Formerly VP of Supply Chain at Bosch Industrial Germany. Oversaw 8 gigafactory deployments across EU and Asia.',
        companyOverview: 'RoboGrid manufactures ruggedized edge sensors and predictive maintenance AI modules for heavy machinery and automotive assembly lines.',
        salesIcebreakers: [
          'Mention our ultra-low latency sync architecture for industrial factory floors.',
          'Inquire about their field maintenance teams and how mobile-first workflow software can eliminate paper field audits.'
        ],
        suggestedActionItems: [
          'Send field-service mobile app case study',
          'Follow up regarding their Q1 RFP for field automation software'
        ],
        decisionStatus: 'Decision Maker',
        leadScore: 'Warm'
      },
      {
        name: 'Marcus Vance',
        honorific: 'Mr.',
        title: 'Managing Director, APAC Growth',
        company: 'Synthetix Global Consulting',
        industry: 'Digital Transformation & Enterprise Advisory',
        email: 'm.vance@synthetixglobal.com',
        phone: '+65 8291 4032',
        whatsapp: '+6582914032',
        website: 'https://synthetixglobal.com',
        address: 'Marina Bay Financial Centre Tower 2, Singapore',
        linkedinUrl: 'https://www.linkedin.com/in/marcus-vance-synthetix',
        pastWorkSummary: 'Over 20 years guiding Global 2000 digital supply chain modernization and ERP cloud transformations across Singapore, Tokyo, Sydney, and Mumbai. Former Partner at Accenture Strategy.',
        companyOverview: 'Synthetix provides high-impact advisory on AI adoption, omni-channel customer operations, and agile organization design.',
        salesIcebreakers: [
          'Mention our enterprise rollout speed and SOC2 / ISO compliance credentials.',
          'Ask about APAC corporate client demand for mobile CRM field execution tools.'
        ],
        suggestedActionItems: [
          'Send partner enablement deck and commission structure',
          'Set up follow-up lunch meeting during next regional trade summit'
        ],
        decisionStatus: 'Influencer',
        leadScore: 'Hot'
      }
    ];

    const index = Math.floor(Math.random() * sampleProfiles.length);
    const chosen = JSON.parse(JSON.stringify(sampleProfiles[index]));
    chosen._isSimulated = true;
    return chosen;
  }
}

window.geminiService = new GeminiService();
