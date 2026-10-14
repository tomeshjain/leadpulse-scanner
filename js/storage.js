/**
 * LeadPulse Storage Service
 * Handles persistence for Contacts, Events, Tasks, and Settings.
 */

const STORAGE_KEYS = {
  CONTACTS: 'leadpulse_contacts_v1',
  EVENTS: 'leadpulse_events_v1',
  TASKS: 'leadpulse_tasks_v1',
  SETTINGS: 'leadpulse_settings_v1',
  ACTIVE_EVENT: 'leadpulse_active_event_id'
};

const DEFAULT_EVENTS = [
  {
    id: 'evt-101',
    name: 'Global Tech Expo 2026',
    date: '2026-10-18',
    location: 'Pragati Maidan, New Delhi (Hall 5)',
    description: 'Premier B2B technology trade summit connecting enterprise solution providers with tech executives.',
    badgeColor: '#6366f1', // Indigo
    createdAt: '2026-10-10T10:00:00.000Z'
  },
  {
    id: 'evt-102',
    name: 'SaaS Enterprise Conclave',
    date: '2026-11-05',
    location: 'JW Marriott, Bengaluru',
    description: 'Exclusive leadership gathering focused on AI-driven workflows and cloud CRM transformation.',
    badgeColor: '#ec4899', // Pink
    createdAt: '2026-10-12T14:30:00.000Z'
  },
  {
    id: 'evt-103',
    name: 'Industrial & IoT Summit',
    date: '2026-11-20',
    location: 'HITEX Exhibition Centre, Hyderabad',
    description: 'Hardware, embedded systems, and industrial automation partnership conference.',
    badgeColor: '#10b981', // Emerald
    createdAt: '2026-10-13T09:15:00.000Z'
  }
];

const DEFAULT_CONTACTS = [
  {
    id: 'cnt-1001',
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
    eventId: 'evt-101',
    linkedinUrl: 'https://www.linkedin.com/in/vikramaditya-sharma-cloud',
    pastWorkSummary: '14+ years spearheading hybrid cloud scalability and containerized architectures. Previously Principal Architect at Infosys FinTech Labs and Senior Lead at Cisco Systems. Led high-throughput API gateway processing 50M+ requests/day.',
    companyOverview: 'Nexora builds enterprise-grade multi-cloud observability and zero-trust orchestration for Fortune 500 banks.',
    salesIcebreakers: [
      'Ask about their recent migration to Kubernetes 1.31 and latency milestones.',
      'Discuss how our solution reduces cloud egress spend during peak database queries.'
    ],
    cardImage: '',
    cardColorTheme: 'linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)',
    notes: 'Met at Booth #24. Very interested in our real-time audit logs. Decision maker for Q4 budget.',
    actionItems: [
      {
        id: 'act-1',
        title: 'Share custom Enterprise security whitepaper',
        dueDate: '2026-10-20',
        priority: 'high',
        completed: false
      },
      {
        id: 'act-2',
        title: 'Book 30-min discovery demo with Solutions Architect',
        dueDate: '2026-10-23',
        priority: 'medium',
        completed: false
      }
    ],
    createdAt: '2026-10-14T11:20:00.000Z'
  },
  {
    id: 'cnt-1002',
    name: 'Ananya Deshmukh',
    honorific: 'Dr.',
    title: 'Chief Product Officer',
    company: 'AuraHealth Intelligence',
    industry: 'HealthTech & MedTech AI',
    email: 'ananya.d@aurahealth.ai',
    phone: '+91 91673 89210',
    whatsapp: '+919167389210',
    website: 'https://aurahealth.ai',
    address: 'Indiranagar 100ft Road, Bengaluru, India',
    eventId: 'evt-102',
    linkedinUrl: 'https://www.linkedin.com/in/ananya-deshmukh-healthtech',
    pastWorkSummary: 'Former Head of AI Product at GE Healthcare South Asia. Holds a Ph.D. in Biomedical Informatics from Stanford. Led the FDA-cleared triage algorithm for cardiac imaging deployed across 120+ diagnostic hospitals.',
    companyOverview: 'AuraHealth provides clinical workflow copilots that automate patient discharge summaries and clinician EHR documentation with HIPAA & GDPR compliance.',
    salesIcebreakers: [
      'Compliment her keynote speech on reducing physician burnout using ambient voice AI.',
      'Explore potential pilot integration with our offline-capable mobile workflow platform.'
    ],
    cardImage: '',
    cardColorTheme: 'linear-gradient(135deg, #831843 0%, #be185d 100%)',
    notes: 'Excited about mobile scanning capabilities for their field medical representatives.',
    actionItems: [
      {
        id: 'act-3',
        title: 'Send draft mutual Non-Disclosure Agreement (NDA)',
        dueDate: '2026-10-16',
        priority: 'high',
        completed: true
      },
      {
        id: 'act-4',
        title: 'Follow-up regarding hospital pilot cohort size',
        dueDate: '2026-10-28',
        priority: 'medium',
        completed: false
      }
    ],
    createdAt: '2026-10-14T14:45:00.000Z'
  },
  {
    id: 'cnt-1003',
    name: 'Rajeev Menon',
    honorific: 'Mr.',
    title: 'Director of Strategic Sourcing',
    company: 'Apex Kinetic Dynamics',
    industry: 'Automotive & Industrial Automation',
    email: 'rmenon@apexkinetic.com',
    phone: '+91 97412 33451',
    whatsapp: '+919741233451',
    website: 'https://apexkinetic.com',
    address: 'Whitefield Industrial Area, Bengaluru, India',
    eventId: 'evt-103',
    linkedinUrl: 'https://www.linkedin.com/in/rajeev-menon-sourcing',
    pastWorkSummary: '18 years in tier-1 automotive manufacturing supply chain. Former Global Procurement Director at Bosch Mobility Services. Managed vendor portfolios in excess of $80M across EV powertrain components.',
    companyOverview: 'Apex manufactures precision CNC machined robotics arms and smart torque sensors for electric vehicle battery pack assembly lines.',
    salesIcebreakers: [
      'Inquire about their supplier qualification cycle for upcoming battery gigafactory lines.',
      'Highlight our rapid API integration with legacy SAP ERP systems.'
    ],
    cardImage: '',
    cardColorTheme: 'linear-gradient(135deg, #064e3b 0%, #047857 100%)',
    notes: 'Looking for a streamlined contact capturing tool for their 40-member international field sales team.',
    actionItems: [
      {
        id: 'act-5',
        title: 'Prepare cost-per-seat comparison sheet vs Salesforce Mobile',
        dueDate: '2026-10-22',
        priority: 'high',
        completed: false
      }
    ],
    createdAt: '2026-10-14T16:10:00.000Z'
  }
];

class StorageService {
  constructor() {
    this.init();
  }

  init() {
    if (!localStorage.getItem(STORAGE_KEYS.EVENTS)) {
      this.saveEvents(DEFAULT_EVENTS);
    }
    if (!localStorage.getItem(STORAGE_KEYS.CONTACTS)) {
      this.saveContacts(DEFAULT_CONTACTS);
    }
    if (!localStorage.getItem(STORAGE_KEYS.ACTIVE_EVENT)) {
      this.setActiveEventId(DEFAULT_EVENTS[0].id);
    }
  }

  // --- EVENTS ---
  getEvents() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.EVENTS);
      return data ? JSON.parse(data) : DEFAULT_EVENTS;
    } catch (e) {
      console.error('Failed to get events:', e);
      return DEFAULT_EVENTS;
    }
  }

  saveEvents(events) {
    localStorage.setItem(STORAGE_KEYS.EVENTS, JSON.stringify(events));
  }

  addEvent(eventData) {
    const events = this.getEvents();
    const newEvent = {
      id: 'evt-' + Date.now(),
      name: eventData.name.trim(),
      date: eventData.date || new Date().toISOString().split('T')[0],
      location: eventData.location ? eventData.location.trim() : '',
      description: eventData.description ? eventData.description.trim() : '',
      badgeColor: eventData.badgeColor || '#4f46e5',
      createdAt: new Date().toISOString()
    };
    events.unshift(newEvent);
    this.saveEvents(events);
    return newEvent;
  }

  deleteEvent(eventId) {
    let events = this.getEvents();
    events = events.filter(e => e.id !== eventId);
    this.saveEvents(events);

    // If active event was deleted, switch to the first available event
    if (this.getActiveEventId() === eventId) {
      this.setActiveEventId(events.length > 0 ? events[0].id : '');
    }
  }

  getEventById(id) {
    return this.getEvents().find(e => e.id === id) || null;
  }

  getActiveEventId() {
    return localStorage.getItem(STORAGE_KEYS.ACTIVE_EVENT) || '';
  }

  setActiveEventId(id) {
    localStorage.setItem(STORAGE_KEYS.ACTIVE_EVENT, id);
  }

  // --- CONTACTS ---
  getContacts() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.CONTACTS);
      return data ? JSON.parse(data) : DEFAULT_CONTACTS;
    } catch (e) {
      console.error('Failed to get contacts:', e);
      return DEFAULT_CONTACTS;
    }
  }

  saveContacts(contacts) {
    localStorage.setItem(STORAGE_KEYS.CONTACTS, JSON.stringify(contacts));
  }

  getContactById(id) {
    return this.getContacts().find(c => c.id === id) || null;
  }

  addContact(contactData) {
    const contacts = this.getContacts();
    const newContact = {
      id: 'cnt-' + Date.now(),
      name: contactData.name || 'Unnamed Contact',
      honorific: contactData.honorific || '',
      title: contactData.title || '',
      company: contactData.company || '',
      industry: contactData.industry || 'General Business',
      email: contactData.email || '',
      phone: contactData.phone || '',
      whatsapp: contactData.whatsapp || (contactData.phone ? contactData.phone.replace(/[^0-9+]/g, '') : ''),
      website: contactData.website || '',
      address: contactData.address || '',
      eventId: contactData.eventId || this.getActiveEventId() || '',
      linkedinUrl: contactData.linkedinUrl || '',
      pastWorkSummary: contactData.pastWorkSummary || '',
      companyOverview: contactData.companyOverview || '',
      salesIcebreakers: Array.isArray(contactData.salesIcebreakers) ? contactData.salesIcebreakers : [],
      cardImage: contactData.cardImage || '',
      cardColorTheme: contactData.cardColorTheme || 'linear-gradient(135deg, #1e293b 0%, #334155 100%)',
      notes: contactData.notes || '',
      actionItems: Array.isArray(contactData.actionItems) ? contactData.actionItems : [],
      createdAt: new Date().toISOString()
    };
    contacts.unshift(newContact);
    this.saveContacts(contacts);
    return newContact;
  }

  updateContact(id, updatedFields) {
    const contacts = this.getContacts();
    const index = contacts.findIndex(c => c.id === id);
    if (index !== -1) {
      contacts[index] = { ...contacts[index], ...updatedFields, updatedAt: new Date().toISOString() };
      this.saveContacts(contacts);
      return contacts[index];
    }
    return null;
  }

  deleteContact(id) {
    let contacts = this.getContacts();
    contacts = contacts.filter(c => c.id !== id);
    this.saveContacts(contacts);
  }

  // --- ACTION ITEMS (CONTACT-BOUND & GLOBAL) ---
  addActionItem(contactId, itemData) {
    const contacts = this.getContacts();
    const contact = contacts.find(c => c.id === contactId);
    if (!contact) return null;

    if (!Array.isArray(contact.actionItems)) {
      contact.actionItems = [];
    }

    const newItem = {
      id: 'act-' + Date.now() + '-' + Math.floor(Math.random() * 1000),
      title: itemData.title.trim(),
      dueDate: itemData.dueDate || new Date().toISOString().split('T')[0],
      priority: itemData.priority || 'medium', // 'low', 'medium', 'high'
      completed: false,
      createdAt: new Date().toISOString()
    };

    contact.actionItems.push(newItem);
    this.saveContacts(contacts);
    return newItem;
  }

  toggleActionItem(contactId, actionItemId) {
    const contacts = this.getContacts();
    const contact = contacts.find(c => c.id === contactId);
    if (!contact || !Array.isArray(contact.actionItems)) return null;

    const item = contact.actionItems.find(a => a.id === actionItemId);
    if (item) {
      item.completed = !item.completed;
      this.saveContacts(contacts);
      return item;
    }
    return null;
  }

  deleteActionItem(contactId, actionItemId) {
    const contacts = this.getContacts();
    const contact = contacts.find(c => c.id === contactId);
    if (!contact || !Array.isArray(contact.actionItems)) return;

    contact.actionItems = contact.actionItems.filter(a => a.id !== actionItemId);
    this.saveContacts(contacts);
  }

  getAllActionItems() {
    const contacts = this.getContacts();
    const allItems = [];
    contacts.forEach(contact => {
      if (Array.isArray(contact.actionItems)) {
        contact.actionItems.forEach(action => {
          allItems.push({
            ...action,
            contactId: contact.id,
            contactName: contact.name,
            contactCompany: contact.company,
            contactPhone: contact.phone,
            contactEmail: contact.email,
            eventId: contact.eventId
          });
        });
      }
    });

    // Sort: incomplete first, then by due date
    return allItems.sort((a, b) => {
      if (a.completed !== b.completed) return a.completed ? 1 : -1;
      return new Date(a.dueDate) - new Date(b.dueDate);
    });
  }

  // --- SETTINGS (GEMINI API KEY, MODEL SELECTION, PREFERENCES) ---
  getSettings() {
    const defaultSettings = {
      geminiApiKey: '',
      aiModel: 'gemini-1.5-flash',
      autoEnrich: true,
      customPrompt: '',
      availableModels: [
        { id: 'gemini-1.5-flash', name: 'Gemini 1.5 Flash (Default - Fast & High Accuracy)' },
        { id: 'gemini-2.0-flash', name: 'Gemini 2.0 Flash (Next-Gen Multimodal)' },
        { id: 'gemini-1.5-pro', name: 'Gemini 1.5 Pro (Deep Context Reasoning)' },
        { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash (Latest Experimental)' },
        { id: 'gemini-1.5-flash-8b', name: 'Gemini 1.5 Flash 8B (High Speed)' }
      ]
    };

    try {
      const data = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      return data ? { ...defaultSettings, ...JSON.parse(data) } : defaultSettings;
    } catch (e) {
      return defaultSettings;
    }
  }

  saveSettings(settings) {
    localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(settings));
  }

  getGeminiApiKey() {
    const settings = this.getSettings();
    return settings.geminiApiKey ? settings.geminiApiKey.trim() : '';
  }

  setGeminiApiKey(key) {
    const settings = this.getSettings();
    settings.geminiApiKey = key.trim();
    this.saveSettings(settings);
  }

  getSelectedModel() {
    const settings = this.getSettings();
    return settings.aiModel || 'gemini-1.5-flash';
  }

  setSelectedModel(modelId) {
    const settings = this.getSettings();
    settings.aiModel = modelId;
    this.saveSettings(settings);
  }

  getAvailableModels() {
    const settings = this.getSettings();
    return settings.availableModels || [];
  }

  setAvailableModels(modelsList) {
    const settings = this.getSettings();
    settings.availableModels = modelsList;
    this.saveSettings(settings);
  }

  getCustomPrompt() {
    const settings = this.getSettings();
    return settings.customPrompt || '';
  }

  setCustomPrompt(prompt) {
    const settings = this.getSettings();
    settings.customPrompt = prompt;
    this.saveSettings(settings);
  }
}

window.storageService = new StorageService();
