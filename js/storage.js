/**
 * LeadPulse Enterprise Storage & Collaboration Service
 * Handles persistence, data retention across updates, sales representative attribution,
 * structured organization matching & career history, and cross-team cloud synchronization.
 * 
 * STRICT POLICY: Zero dummy data. Only genuine user-created and team-synced records.
 */

const STORAGE_KEYS = {
  CONTACTS: 'leadpulse_contacts_v1',
  EVENTS: 'leadpulse_events_v1',
  TASKS: 'leadpulse_tasks_v1',
  SETTINGS: 'leadpulse_settings_v1',
  ACTIVE_EVENT: 'leadpulse_active_event_id',
  SALES_REP: 'leadpulse_sales_rep_profile_v1',
  TEAM_SYNC: 'leadpulse_team_sync_config_v1',
  DUMMY_PURGED_FLAG: 'leadpulse_zero_dummy_purged_v2',
  AVAILABLE_MODELS: 'leadpulse_cached_gemini_models_v1',
  SELECTED_MODEL: 'leadpulse_selected_model_v1'
};

// Known dummy demo IDs to purge from any legacy test runs
const LEGACY_DUMMY_IDS = new Set(['cnt-1001', 'cnt-1002', 'cnt-1003', 'cnt-1004']);
const LEGACY_DUMMY_NAMES = new Set([
  'Vikramaditya Sharma',
  'Ananya Deshmukh',
  'Dr. Ananya Deshmukh',
  'Rajeev Menon',
  'Rajiv Menon',
  'Marcus Vance'
]);

class StorageService {
  constructor() {
    this.init();
  }

  init() {
    // 1. Purge legacy dummy sample data if present from older versions
    this.purgeLegacyDummyData();

    // 2. Ensure initial events container
    if (!localStorage.getItem(STORAGE_KEYS.EVENTS)) {
      const initialEvent = [{
        id: 'evt-general',
        name: 'General Field Leads',
        date: new Date().toISOString().split('T')[0],
        location: 'Field & Client Meetings',
        description: 'Standard event category for day-to-day sales meetings and visiting card scans.',
        badgeColor: '#6366f1',
        createdAt: new Date().toISOString()
      }];
      this.saveEvents(initialEvent);
      this.setActiveEventId(initialEvent[0].id);
    }

    // 3. Ensure contacts container exists (empty by default - NO DUMMY DATA)
    if (!localStorage.getItem(STORAGE_KEYS.CONTACTS)) {
      this.saveContacts([]);
    }

    // 4. Ensure tasks container exists (empty by default)
    if (!localStorage.getItem(STORAGE_KEYS.TASKS)) {
      this.saveTasks([]);
    }

    // 5. Ensure default active event
    if (!this.getActiveEventId()) {
      const events = this.getEvents();
      if (events.length > 0) {
        this.setActiveEventId(events[0].id);
      }
    }
  }

  /**
   * Cleanses any leftover dummy test leads from early testing phases
   */
  purgeLegacyDummyData() {
    try {
      const isAlreadyPurged = localStorage.getItem(STORAGE_KEYS.DUMMY_PURGED_FLAG);
      if (!isAlreadyPurged) {
        const rawContacts = localStorage.getItem(STORAGE_KEYS.CONTACTS);
        if (rawContacts) {
          const contacts = JSON.parse(rawContacts);
          const cleanContacts = contacts.filter(c => 
            !LEGACY_DUMMY_IDS.has(c.id) && 
            !LEGACY_DUMMY_NAMES.has((c.name || '').trim())
          );
          this.saveContacts(cleanContacts);
        }

        // Clean legacy dummy events
        const rawEvents = localStorage.getItem(STORAGE_KEYS.EVENTS);
        if (rawEvents) {
          const events = JSON.parse(rawEvents);
          const cleanEvents = events.filter(e => e.id !== 'evt-101' && e.id !== 'evt-102' && e.id !== 'evt-103');
          if (cleanEvents.length > 0) {
            this.saveEvents(cleanEvents);
          }
        }

        localStorage.setItem(STORAGE_KEYS.DUMMY_PURGED_FLAG, 'true');
        console.log('LeadPulse: Database cleansed of legacy dummy data.');
      }
    } catch (e) {
      console.warn('Error purging legacy dummy data:', e);
    }
  }

  // =========================================================================
  // SALES REPRESENTATIVE (EMPLOYEE) PROFILE
  // =========================================================================

  /**
   * Retrieves active Sales Representative profile
   */
  getSalesRepProfile() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SALES_REP);
      return data ? JSON.parse(data) : {
        name: '',
        email: '',
        employeeId: '',
        phone: '',
        region: '',
        designation: '',
        teamCode: 'LEADPULSE-GLOBAL'
      };
    } catch (e) {
      console.error('Failed to get sales rep profile:', e);
      return {
        name: '',
        email: '',
        employeeId: '',
        phone: '',
        region: '',
        designation: '',
        teamCode: 'LEADPULSE-GLOBAL'
      };
    }
  }

  /**
   * Saves or updates active Sales Representative profile
   */
  saveSalesRepProfile(profile) {
    const cleanProfile = {
      name: (profile.name || '').trim(),
      email: (profile.email || '').trim().toLowerCase(),
      employeeId: (profile.employeeId || '').trim().toUpperCase(),
      phone: (profile.phone || '').trim(),
      region: (profile.region || '').trim(),
      designation: (profile.designation || '').trim(),
      teamCode: ((profile.teamCode || '').trim() || 'LEADPULSE-GLOBAL').toUpperCase(),
      updatedAt: new Date().toISOString()
    };
    localStorage.setItem(STORAGE_KEYS.SALES_REP, JSON.stringify(cleanProfile));
    return cleanProfile;
  }

  /**
   * Checks if user has configured their sales rep profile
   */
  isSalesRepConfigured() {
    const profile = this.getSalesRepProfile();
    return Boolean(profile.name && profile.employeeId);
  }

  // =========================================================================
  // CONTACTS MANAGEMENT (WITH STRUCTURED ORGANIZATIONS & ATTRIBUTION)
  // =========================================================================

  /**
   * Retrieves all contacts, ensuring schema integrity
   */
  getContacts() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.CONTACTS);
      const contacts = data ? JSON.parse(data) : [];
      return Array.isArray(contacts) ? contacts : [];
    } catch (e) {
      console.error('Failed to parse contacts from storage:', e);
      return [];
    }
  }

  saveContacts(contacts) {
    localStorage.setItem(STORAGE_KEYS.CONTACTS, JSON.stringify(contacts));
  }

  getContactById(contactId) {
    const contacts = this.getContacts();
    return contacts.find(c => c.id === contactId) || null;
  }

  /**
   * Adds a newly scanned / created contact with full attribution
   */
  addContact(contactData) {
    const contacts = this.getContacts();
    const rep = this.getSalesRepProfile();

    // Normalize past organizations
    let pastOrganizations = [];
    if (Array.isArray(contactData.pastOrganizations)) {
      pastOrganizations = contactData.pastOrganizations.map(o => ({
        companyName: typeof o === 'string' ? o.trim() : (o.companyName || '').trim(),
        role: typeof o === 'object' && o.role ? o.role.trim() : '',
        duration: typeof o === 'object' && o.duration ? o.duration.trim() : '',
        notes: typeof o === 'object' && o.notes ? o.notes.trim() : ''
      })).filter(o => o.companyName);
    } else if (Array.isArray(contactData.pastCompaniesList)) {
      pastOrganizations = contactData.pastCompaniesList.map(name => ({
        companyName: (name || '').trim(),
        role: '',
        duration: '',
        notes: ''
      })).filter(o => o.companyName);
    }

    // Extract quick lookup list of past companies
    const pastCompaniesList = [...new Set(pastOrganizations.map(o => o.companyName))];

    const newContact = {
      id: contactData.id || ('cnt_' + Date.now() + '_' + Math.random().toString(36).substring(2, 7)),
      name: (contactData.name || '').trim(),
      honorific: (contactData.honorific || '').trim(),
      title: (contactData.title || '').trim(),
      company: (contactData.company || '').trim(),
      industry: (contactData.industry || '').trim(),
      email: (contactData.email || '').trim().toLowerCase(),
      phone: (contactData.phone || '').trim(),
      whatsapp: (contactData.whatsapp || '').trim(),
      website: (contactData.website || '').trim(),
      address: (contactData.address || '').trim(),
      eventId: contactData.eventId || this.getActiveEventId() || 'evt-general',
      linkedinUrl: (contactData.linkedinUrl || '').trim(),
      
      // Structured Organization Affiliations
      pastWorkSummary: (contactData.pastWorkSummary || '').trim(),
      pastOrganizations: pastOrganizations,
      pastCompaniesList: pastCompaniesList,
      alumniInstitutions: Array.isArray(contactData.alumniInstitutions) ? contactData.alumniInstitutions : [],
      
      companyOverview: (contactData.companyOverview || '').trim(),
      salesIcebreakers: Array.isArray(contactData.salesIcebreakers) ? contactData.salesIcebreakers : [],
      cardImage: contactData.cardImage || '',
      cardColorTheme: contactData.cardColorTheme || 'linear-gradient(135deg, #1e293b 0%, #0f172a 100%)',
      notes: (contactData.notes || '').trim(),
      
      // Sales Qualification Metrics
      decisionStatus: contactData.decisionStatus || 'Evaluator',
      leadScore: contactData.leadScore || 'Warm',
      dealBudget: contactData.dealBudget || 'Undisclosed',
      buyingTimeline: contactData.buyingTimeline || 'Exploring',
      
      // Sales Employee Attribution
      scannedBy: contactData.scannedBy || {
        name: rep.name || 'Sales Rep',
        email: rep.email || '',
        employeeId: rep.employeeId || 'REP-01',
        region: rep.region || ''
      },

      actionItems: Array.isArray(contactData.actionItems) ? contactData.actionItems : [],
      createdAt: contactData.createdAt || new Date().toISOString(),
      updatedAt: new Date().toISOString()
    };

    contacts.unshift(newContact);
    this.saveContacts(contacts);

    // Sync action items to tasks list if any
    if (newContact.actionItems.length > 0) {
      newContact.actionItems.forEach(item => {
        this.addTask({
          contactId: newContact.id,
          contactName: newContact.name,
          contactCompany: newContact.company,
          title: item.title,
          dueDate: item.dueDate,
          priority: item.priority || 'medium',
          completed: Boolean(item.completed)
        });
      });
    }

    return newContact;
  }

  updateContact(contactId, updatedFields) {
    const contacts = this.getContacts();
    const index = contacts.findIndex(c => c.id === contactId);
    if (index === -1) return null;

    contacts[index] = {
      ...contacts[index],
      ...updatedFields,
      updatedAt: new Date().toISOString()
    };

    this.saveContacts(contacts);
    return contacts[index];
  }

  deleteContact(contactId) {
    let contacts = this.getContacts();
    contacts = contacts.filter(c => c.id !== contactId);
    this.saveContacts(contacts);

    // Remove associated tasks
    let tasks = this.getTasks();
    tasks = tasks.filter(t => t.contactId !== contactId);
    this.saveTasks(tasks);
  }

  // =========================================================================
  // ORGANIZATION DIRECTORY & AFFILIATION MATCHING
  // =========================================================================

  /**
   * Scans all contacts and aggregates by organization (current and past).
   * Identifies who all have been associated with any specific organization!
   */
  getAllOrganizations() {
    const contacts = this.getContacts();
    const orgMap = new Map();

    const normalizeOrgName = (name) => {
      if (!name) return '';
      return name.trim();
    };

    contacts.forEach(contact => {
      // 1. Current Company
      const currentOrg = normalizeOrgName(contact.company);
      if (currentOrg) {
        const key = currentOrg.toLowerCase();
        if (!orgMap.has(key)) {
          orgMap.set(key, {
            name: currentOrg,
            industry: contact.industry || 'Technology / Enterprise',
            currentContacts: [],
            pastContacts: [],
            totalCount: 0
          });
        }
        const record = orgMap.get(key);
        record.currentContacts.push({
          contact,
          role: contact.title || 'Current Member',
          type: 'current'
        });
      }

      // 2. Past Companies / Former Organizations
      const pastList = contact.pastOrganizations || [];
      pastList.forEach(pastOrg => {
        const pastName = normalizeOrgName(typeof pastOrg === 'string' ? pastOrg : pastOrg.companyName);
        if (pastName) {
          const key = pastName.toLowerCase();
          if (!orgMap.has(key)) {
            orgMap.set(key, {
              name: pastName,
              industry: 'Former Association',
              currentContacts: [],
              pastContacts: [],
              totalCount: 0
            });
          }
          const record = orgMap.get(key);
          // Avoid duplicate entry for the same person
          if (!record.pastContacts.some(p => p.contact.id === contact.id)) {
            record.pastContacts.push({
              contact,
              role: (typeof pastOrg === 'object' && pastOrg.role) ? pastOrg.role : 'Former Employee / Alumni',
              duration: (typeof pastOrg === 'object' && pastOrg.duration) ? pastOrg.duration : '',
              type: 'past'
            });
          }
        }
      });
    });

    // Calculate totals and convert to sorted array
    const orgs = Array.from(orgMap.values()).map(org => {
      org.totalCount = org.currentContacts.length + org.pastContacts.length;
      return org;
    });

    // Sort by highest connections first
    orgs.sort((a, b) => b.totalCount - a.totalCount || a.name.localeCompare(b.name));
    return orgs;
  }

  /**
   * Finds all contacts currently or previously associated with a specific company
   */
  getContactsForOrganization(orgName) {
    if (!orgName) return { name: '', currentContacts: [], pastContacts: [], totalCount: 0 };
    const query = orgName.trim().toLowerCase();
    const all = this.getAllOrganizations();
    const found = all.find(o => o.name.toLowerCase() === query);
    return found || {
      name: orgName,
      currentContacts: [],
      pastContacts: [],
      totalCount: 0
    };
  }

  // =========================================================================
  // EVENTS MANAGEMENT
  // =========================================================================

  getEvents() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.EVENTS);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      console.error('Failed to get events:', e);
      return [];
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
      badgeColor: eventData.badgeColor || '#6366f1',
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

    if (this.getActiveEventId() === eventId) {
      this.setActiveEventId(events.length > 0 ? events[0].id : '');
    }
  }

  getActiveEventId() {
    return localStorage.getItem(STORAGE_KEYS.ACTIVE_EVENT) || '';
  }

  setActiveEventId(eventId) {
    localStorage.setItem(STORAGE_KEYS.ACTIVE_EVENT, eventId);
  }

  // =========================================================================
  // TASKS / FOLLOW-UP ACTION ITEMS
  // =========================================================================

  getTasks() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.TASKS);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      console.error('Failed to get tasks:', e);
      return [];
    }
  }

  saveTasks(tasks) {
    localStorage.setItem(STORAGE_KEYS.TASKS, JSON.stringify(tasks));
  }

  addTask(taskData) {
    const tasks = this.getTasks();
    const newTask = {
      id: 'tsk-' + Date.now() + '-' + Math.random().toString(36).substring(2, 6),
      contactId: taskData.contactId || null,
      contactName: taskData.contactName || '',
      contactCompany: taskData.contactCompany || '',
      title: taskData.title.trim(),
      dueDate: taskData.dueDate || new Date().toISOString().split('T')[0],
      priority: taskData.priority || 'medium',
      completed: Boolean(taskData.completed),
      createdAt: new Date().toISOString()
    };
    tasks.unshift(newTask);
    this.saveTasks(tasks);
    return newTask;
  }

  toggleTaskCompletion(taskId) {
    const tasks = this.getTasks();
    const task = tasks.find(t => t.id === taskId);
    if (!task) return null;
    task.completed = !task.completed;
    this.saveTasks(tasks);

    // Also update contact actionItems if linked
    if (task.contactId) {
      const contacts = this.getContacts();
      const contact = contacts.find(c => c.id === task.contactId);
      if (contact && Array.isArray(contact.actionItems)) {
        const cItem = contact.actionItems.find(i => i.title === task.title || i.id === task.id);
        if (cItem) {
          cItem.completed = task.completed;
          this.saveContacts(contacts);
        }
      }
    }
    return task;
  }

  deleteTask(taskId) {
    let tasks = this.getTasks();
    tasks = tasks.filter(t => t.id !== taskId);
    this.saveTasks(tasks);
  }

  // =========================================================================
  // CROSS-TEAM COLLABORATION & MULTI-USER CLOUD SYNC
  // =========================================================================

  getTeamSyncConfig() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.TEAM_SYNC);
      return data ? JSON.parse(data) : {
        enabled: true,
        teamCode: 'LEADPULSE-GLOBAL',
        lastSynced: null,
        autoSyncOnScan: true
      };
    } catch (e) {
      return {
        enabled: true,
        teamCode: 'LEADPULSE-GLOBAL',
        lastSynced: null,
        autoSyncOnScan: true
      };
    }
  }

  saveTeamSyncConfig(config) {
    const current = this.getTeamSyncConfig();
    const updated = {
      ...current,
      ...config,
      teamCode: ((config.teamCode || current.teamCode || '').trim() || 'LEADPULSE-GLOBAL').toUpperCase()
    };
    localStorage.setItem(STORAGE_KEYS.TEAM_SYNC, JSON.stringify(updated));
    return updated;
  }

  /**
   * Generates a complete JSON backup bundle for direct team sharing
   */
  exportFullDatabaseBundle() {
    const bundle = {
      leadpulseVersion: '2.0.0',
      exportedAt: new Date().toISOString(),
      salesRep: this.getSalesRepProfile(),
      teamCode: this.getTeamSyncConfig().teamCode,
      contacts: this.getContacts(),
      events: this.getEvents(),
      tasks: this.getTasks()
    };
    return JSON.stringify(bundle, null, 2);
  }

  /**
   * Imports and merges team leads non-destructively.
   * Deduplicates by ID, Email, and Phone number.
   * Retains all existing data and integrates teammates' scans.
   */
  importAndMergeDatabaseBundle(jsonString) {
    try {
      const bundle = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
      if (!bundle || (!bundle.contacts && !Array.isArray(bundle))) {
        throw new Error('Invalid LeadPulse bundle format.');
      }

      const incomingContacts = Array.isArray(bundle) ? bundle : (bundle.contacts || []);
      const incomingEvents = bundle.events || [];
      const incomingTasks = bundle.tasks || [];

      let existingContacts = this.getContacts();
      let addedCount = 0;
      let updatedCount = 0;

      // Map existing by ID and phone/email
      const existingIdMap = new Map();
      const existingKeyMap = new Map();

      existingContacts.forEach(c => {
        existingIdMap.set(c.id, c);
        if (c.email) existingKeyMap.set(c.email.toLowerCase(), c);
        if (c.phone) existingKeyMap.set(c.phone.replace(/[^0-9]/g, ''), c);
      });

      incomingContacts.forEach(inc => {
        if (!inc || !inc.name) return;

        // Skip legacy dummy records from imported bundle
        if (LEGACY_DUMMY_IDS.has(inc.id) || LEGACY_DUMMY_NAMES.has((inc.name || '').trim())) {
          return;
        }

        const phoneKey = inc.phone ? inc.phone.replace(/[^0-9]/g, '') : null;
        const emailKey = inc.email ? inc.email.toLowerCase() : null;

        let match = existingIdMap.get(inc.id) || 
                    (emailKey && existingKeyMap.get(emailKey)) || 
                    (phoneKey && existingKeyMap.get(phoneKey));

        if (match) {
          // Merge missing fields
          let modified = false;
          if (!match.linkedinUrl && inc.linkedinUrl) { match.linkedinUrl = inc.linkedinUrl; modified = true; }
          if (!match.pastWorkSummary && inc.pastWorkSummary) { match.pastWorkSummary = inc.pastWorkSummary; modified = true; }
          if ((!match.pastOrganizations || match.pastOrganizations.length === 0) && inc.pastOrganizations) {
            match.pastOrganizations = inc.pastOrganizations;
            modified = true;
          }
          if (modified) updatedCount++;
        } else {
          // New contact from teammate!
          existingContacts.push(inc);
          existingIdMap.set(inc.id, inc);
          if (emailKey) existingKeyMap.set(emailKey, inc);
          if (phoneKey) existingKeyMap.set(phoneKey, inc);
          addedCount++;
        }
      });

      this.saveContacts(existingContacts);

      // Merge Events
      const existingEvents = this.getEvents();
      incomingEvents.forEach(e => {
        if (!existingEvents.some(ex => ex.id === e.id || ex.name.toLowerCase() === e.name.toLowerCase())) {
          existingEvents.push(e);
        }
      });
      this.saveEvents(existingEvents);

      // Merge Tasks
      const existingTasks = this.getTasks();
      incomingTasks.forEach(t => {
        if (!existingTasks.some(et => et.id === t.id)) {
          existingTasks.push(t);
        }
      });
      this.saveTasks(existingTasks);

      return {
        success: true,
        addedCount,
        updatedCount,
        totalContacts: existingContacts.length
      };
    } catch (e) {
      console.error('Import failed:', e);
      return { success: false, error: e.message };
    }
  }

  // =========================================================================
  // SETTINGS & GEMINI MODELS
  // =========================================================================

  getSettings() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.SETTINGS);
      return data ? JSON.parse(data) : {
        geminiApiKey: '',
        customPromptInstructions: '',
        enableOfflineFallbacks: true,
        selectedModel: 'gemini-1.5-flash',
        autoSyncWithTeam: true
      };
    } catch (e) {
      return {
        geminiApiKey: '',
        customPromptInstructions: '',
        enableOfflineFallbacks: true,
        selectedModel: 'gemini-1.5-flash',
        autoSyncWithTeam: true
      };
    }
  }

  saveSettings(settings) {
    const current = this.getSettings();
    const updated = { ...current, ...settings };
    localStorage.setItem(STORAGE_KEYS.SETTINGS, JSON.stringify(updated));
    return updated;
  }

  getGeminiApiKey() {
    return this.getSettings().geminiApiKey || '';
  }

  setGeminiApiKey(key) {
    this.saveSettings({ geminiApiKey: key.trim() });
  }

  getCustomPromptInstructions() {
    return this.getSettings().customPromptInstructions || '';
  }

  getSelectedModel() {
    return localStorage.getItem(STORAGE_KEYS.SELECTED_MODEL) || this.getSettings().selectedModel || 'gemini-1.5-flash';
  }

  setSelectedModel(modelId) {
    localStorage.setItem(STORAGE_KEYS.SELECTED_MODEL, modelId);
    this.saveSettings({ selectedModel: modelId });
  }

  getAvailableModels() {
    try {
      const data = localStorage.getItem(STORAGE_KEYS.AVAILABLE_MODELS);
      return data ? JSON.parse(data) : [];
    } catch (e) {
      return [];
    }
  }

  setAvailableModels(models) {
    localStorage.setItem(STORAGE_KEYS.AVAILABLE_MODELS, JSON.stringify(models));
  }

  /**
   * Resets all leads and tasks with explicit user confirmation.
   * GUARANTEE: Does NOT restore dummy data. Resets to a completely clean slate.
   */
  clearAllData() {
    this.saveContacts([]);
    this.saveTasks([]);
    console.log('LeadPulse: Database cleared to 0 contacts.');
  }
}

// Global Singleton Initialization
window.storageService = new StorageService();
