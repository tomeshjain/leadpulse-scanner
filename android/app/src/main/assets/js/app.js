/**
 * LeadPulse - Enterprise Sales Card Scanner & CRM Controller
 * Provides clean zero-dummy-data architecture, Sales Rep corporate attribution,
 * Cross-team sync & sharing, and structured organization & alumni matching.
 */

class AppController {
  constructor() {
    this.currentView = 'viewScanner';
    this.currentEventFilter = 'all';
    this.currentTaskFilter = 'all';
    this.currentScopeFilter = 'all'; // 'all' | 'my' | 'hot' | 'decision'
    this.searchQuery = '';
    this.orgSearchQuery = '';
    this.activeContactDetailId = null;
    this.tempScannedCard = null;

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => this.init());
    } else {
      this.init();
    }
  }

  async init() {
    console.log('LeadPulse Enterprise Initializing...');

    // Guarantee: Purge any legacy dummy records from previous app versions
    if (window.storageService && typeof window.storageService.purgeLegacyDummyData === 'function') {
      window.storageService.purgeLegacyDummyData();
    }

    this.cacheDomElements();
    this.bindEvents();
    this.updateSalesRepUI();

    // Set initial active event if none configured
    if (!window.storageService.getActiveEventId()) {
      const events = window.storageService.getEvents();
      if (events.length > 0) {
        window.storageService.setActiveEventId(events[0].id);
      }
    }

    this.updateHeaderActiveEvent();
    this.renderContacts();
    this.renderOrganizations();
    this.renderEvents();
    this.renderTasks();
    this.renderEventFilterPills();
    this.loadSettings();

    // Initialize camera stream
    await this.initCamera();

    // Register service worker for standalone PWA capabilities
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('./sw.js').catch(err => {
        console.log('SW registration note:', err);
      });
    }
  }

  cacheDomElements() {
    // Navigation
    this.navBtns = document.querySelectorAll('.nav-item-btn[data-target]');
    this.navBtnCenterScan = document.getElementById('navBtnCenterScan');
    this.viewScreens = document.querySelectorAll('.view-screen');

    // Header & Sales Rep Profile
    this.btnHeaderSalesRep = document.getElementById('btnHeaderSalesRep');
    this.headerRepInitials = document.getElementById('headerRepInitials');
    this.headerRepName = document.getElementById('headerRepName');
    this.btnActiveEventPill = document.getElementById('btnActiveEventPill');
    this.headerActiveEventName = document.getElementById('headerActiveEventName');
    this.btnOpenSettings = document.getElementById('btnOpenSettings');

    // Scanner
    this.cameraVideo = document.getElementById('cameraVideo');
    this.btnCapture = document.getElementById('btnCapture');
    this.btnUploadGallery = document.getElementById('btnUploadGallery');
    this.fileCardInput = document.getElementById('fileCardInput');
    this.cameraPhotoInput = document.getElementById('cameraPhotoInput');
    this.btnManualEntry = document.getElementById('btnManualEntry');
    this.btnToggleTorch = document.getElementById('btnToggleTorch');
    this.btnRestartCamera = document.getElementById('btnRestartCamera');
    this.scannerActiveEventLabel = document.getElementById('scannerActiveEventLabel');
    this.btnChangeActiveEvent = document.getElementById('btnChangeActiveEvent');

    // AI Overlay
    this.aiProcessingOverlay = document.getElementById('aiProcessingOverlay');
    this.aiProcessingTitle = document.getElementById('aiProcessingTitle');
    this.aiProcessingDesc = document.getElementById('aiProcessingDesc');

    // Contacts View
    this.contactsFeed = document.getElementById('contactsFeed');
    this.inputSearchContacts = document.getElementById('inputSearchContacts');
    this.btnClearSearch = document.getElementById('btnClearSearch');
    this.scopeChips = document.querySelectorAll('.scope-chip');
    this.contactsEventFilterPills = document.getElementById('contactsEventFilterPills');
    this.statTotalContacts = document.getElementById('statTotalContacts');
    this.statTotalOrgs = document.getElementById('statTotalOrgs');
    this.statPendingTasks = document.getElementById('statPendingTasks');
    this.contactsCountLabel = document.getElementById('contactsCountLabel');
    this.btnExportAllCSV = document.getElementById('btnExportAllCSV');

    // Organizations View
    this.orgsFeed = document.getElementById('orgsFeed');
    this.inputSearchOrgs = document.getElementById('inputSearchOrgs');
    this.btnClearSearchOrgs = document.getElementById('btnClearSearchOrgs');
    this.orgsCountLabel = document.getElementById('orgsCountLabel');

    // Events View
    this.eventsList = document.getElementById('eventsList');
    this.btnAddNewEvent = document.getElementById('btnAddNewEvent');
    this.bannerActiveEventName = document.getElementById('bannerActiveEventName');
    this.bannerActiveEventLocation = document.getElementById('bannerActiveEventLocation');

    // Tasks View
    this.tasksFeed = document.getElementById('tasksFeed') || document.getElementById('tasksList');
    this.taskFilterPills = document.querySelectorAll('.task-filter-pill');
    this.tasksCountLabel = document.getElementById('tasksCountLabel');

    // Modals
    this.modalScanReview = document.getElementById('modalScanReview');
    this.btnCloseScanReview = document.getElementById('btnCloseScanReview');
    this.btnCancelReview = document.getElementById('btnCancelReview');
    this.btnSaveReviewContact = document.getElementById('btnSaveReviewContact');
    this.reviewCardImagePreview = document.getElementById('reviewCardImagePreview');
    this.reviewEventSelect = document.getElementById('reviewEventSelect');
    this.reviewPastOrgsList = document.getElementById('reviewPastOrgsList');
    this.reviewDecisionStatus = document.getElementById('reviewDecisionStatus');
    this.reviewLeadScore = document.getElementById('reviewLeadScore');
    this.reviewBudget = document.getElementById('reviewBudget');
    this.reviewTimeline = document.getElementById('reviewTimeline');

    this.modalContactDetail = document.getElementById('modalContactDetail');
    this.btnCloseContactDetail = document.getElementById('btnCloseContactDetail');
    this.detailSheetContentBody = document.getElementById('detailSheetContentBody');
    this.btnDeleteContact = document.getElementById('btnDeleteContact');
    this.btnDownloadVCardSingle = document.getElementById('btnDownloadVCardSingle');
    this.detailSheetHeaderTitle = document.getElementById('detailSheetHeaderTitle');

    this.modalOrgMatching = document.getElementById('modalOrgMatching');
    this.btnCloseOrgMatching = document.getElementById('btnCloseOrgMatching');
    this.orgMatchingModalTitle = document.getElementById('orgMatchingModalTitle');
    this.orgMatchingModalBody = document.getElementById('orgMatchingModalBody');

    this.modalSalesRep = document.getElementById('modalSalesRep');
    this.btnCloseSalesRepModal = document.getElementById('btnCloseSalesRepModal');
    this.btnCancelSalesRep = document.getElementById('btnCancelSalesRep');
    this.btnSaveSalesRep = document.getElementById('btnSaveSalesRep');
    this.repFormName = document.getElementById('repFormName');
    this.repFormEmpId = document.getElementById('repFormEmpId');
    this.repFormPhone = document.getElementById('repFormPhone');
    this.repFormEmail = document.getElementById('repFormEmail');
    this.repFormRegion = document.getElementById('repFormRegion');
    this.repFormDesignation = document.getElementById('repFormDesignation');
    this.repFormTeamCode = document.getElementById('repFormTeamCode');

    this.modalAddEvent = document.getElementById('modalAddEvent');
    this.btnCloseAddEvent = document.getElementById('btnCloseAddEvent');
    this.btnCancelAddEvent = document.getElementById('btnCancelAddEvent');
    this.btnSubmitAddEvent = document.getElementById('btnSubmitAddEvent');

    // Settings View Elements
    this.settingsRepAvatar = document.getElementById('settingsRepAvatar');
    this.settingsRepNameDisplay = document.getElementById('settingsRepNameDisplay');
    this.settingsRepMetaDisplay = document.getElementById('settingsRepMetaDisplay');
    this.btnEditSalesRepProfile = document.getElementById('btnEditSalesRepProfile');
    this.inputTeamWorkspaceCode = document.getElementById('inputTeamWorkspaceCode');
    this.btnSyncTeamNow = document.getElementById('btnSyncTeamNow');
    this.btnShareTeamBundle = document.getElementById('btnShareTeamBundle');
    this.btnImportTeamBundle = document.getElementById('btnImportTeamBundle');
    this.fileImportTeamBundle = document.getElementById('fileImportTeamBundle');
    this.settingGeminiApiKey = document.getElementById('settingGeminiApiKey');
    this.settingModelSelect = document.getElementById('settingModelSelect');
    this.btnRefreshModels = document.getElementById('btnRefreshModels');
    this.btnTestModel = document.getElementById('btnTestModel');
    this.btnExportFullBackup = document.getElementById('btnExportFullBackup');
    this.btnRestoreBackup = document.getElementById('btnRestoreBackup');
    this.fileRestoreBackup = document.getElementById('fileRestoreBackup');
    this.btnClearAllData = document.getElementById('btnClearAllData');

    // Toast Container
    this.toastContainer = document.getElementById('toastContainer');
  }

  bindEvents() {
    // Tab Navigation
    this.navBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const target = btn.getAttribute('data-target');
        this.switchView(target);
      });
    });

    if (this.navBtnCenterScan) {
      this.navBtnCenterScan.addEventListener('click', () => {
        this.switchView('viewScanner');
      });
    }

    // Header buttons
    if (this.btnHeaderSalesRep) {
      this.btnHeaderSalesRep.addEventListener('click', () => this.openSalesRepModal());
    }
    if (this.btnActiveEventPill) {
      this.btnActiveEventPill.addEventListener('click', () => this.switchView('viewEvents'));
    }
    if (this.btnChangeActiveEvent) {
      this.btnChangeActiveEvent.addEventListener('click', () => this.switchView('viewEvents'));
    }
    if (this.btnOpenSettings) {
      this.btnOpenSettings.addEventListener('click', () => this.switchView('viewSettings'));
    }

    // Camera & Scanning
    if (this.btnCapture) {
      this.btnCapture.addEventListener('click', () => this.handleCaptureCard());
    }
    if (this.btnUploadGallery) {
      this.btnUploadGallery.addEventListener('click', () => {
        if (this.fileCardInput) this.fileCardInput.click();
      });
    }
    if (this.fileCardInput) {
      this.fileCardInput.addEventListener('change', (e) => this.handleFileUpload(e));
    }
    if (this.cameraPhotoInput) {
      this.cameraPhotoInput.addEventListener('change', (e) => this.handleFileUpload(e));
    }
    if (this.btnManualEntry) {
      this.btnManualEntry.addEventListener('click', () => this.openManualContactModal());
    }
    if (this.btnToggleTorch) {
      this.btnToggleTorch.addEventListener('click', () => this.toggleTorch());
    }
    if (this.btnRestartCamera) {
      this.btnRestartCamera.addEventListener('click', () => this.initCamera());
    }

    // Contacts Directory: Search & Scope Filter Chips
    if (this.inputSearchContacts) {
      this.inputSearchContacts.addEventListener('input', (e) => {
        this.searchQuery = e.target.value.toLowerCase().trim();
        if (this.btnClearSearch) {
          this.btnClearSearch.style.display = this.searchQuery ? 'block' : 'none';
        }
        this.renderContacts();
      });
    }
    if (this.btnClearSearch) {
      this.btnClearSearch.addEventListener('click', () => {
        if (this.inputSearchContacts) this.inputSearchContacts.value = '';
        this.searchQuery = '';
        this.btnClearSearch.style.display = 'none';
        this.renderContacts();
      });
    }

    this.scopeChips.forEach(chip => {
      chip.addEventListener('click', () => {
        this.scopeChips.forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
        this.currentScopeFilter = chip.getAttribute('data-scope') || 'all';
        this.renderContacts();
      });
    });

    if (this.btnExportAllCSV) {
      this.btnExportAllCSV.addEventListener('click', () => {
        const contacts = window.storageService.getContacts();
        if (contacts.length === 0) {
          this.showToast('No contacts to export yet.', 'info');
          return;
        }
        window.exportService.exportContactsToCSV(contacts, 'LeadPulse_Team_Leads.csv');
        this.showToast('CSV Export generated successfully!', 'success');
      });
    }

    // Organization Directory: Search & Clear
    if (this.inputSearchOrgs) {
      this.inputSearchOrgs.addEventListener('input', (e) => {
        this.orgSearchQuery = e.target.value.toLowerCase().trim();
        if (this.btnClearSearchOrgs) {
          this.btnClearSearchOrgs.style.display = this.orgSearchQuery ? 'block' : 'none';
        }
        this.renderOrganizations();
      });
    }
    if (this.btnClearSearchOrgs) {
      this.btnClearSearchOrgs.addEventListener('click', () => {
        if (this.inputSearchOrgs) this.inputSearchOrgs.value = '';
        this.orgSearchQuery = '';
        this.btnClearSearchOrgs.style.display = 'none';
        this.renderOrganizations();
      });
    }

    // Tasks Filters
    this.taskFilterPills.forEach(pill => {
      pill.addEventListener('click', () => {
        this.taskFilterPills.forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        this.currentTaskFilter = pill.getAttribute('data-task-filter') || 'all';
        this.renderTasks();
      });
    });

    // Modals: Scan Review
    if (this.btnCloseScanReview) {
      this.btnCloseScanReview.addEventListener('click', () => this.closeModal('modalScanReview'));
    }
    if (this.btnCancelReview) {
      this.btnCancelReview.addEventListener('click', () => this.closeModal('modalScanReview'));
    }
    if (this.btnSaveReviewContact) {
      this.btnSaveReviewContact.addEventListener('click', () => this.handleSaveReviewContact());
    }

    // Modals: Contact Details
    if (this.btnCloseContactDetail) {
      this.btnCloseContactDetail.addEventListener('click', () => this.closeModal('modalContactDetail'));
    }
    if (this.btnDeleteContact) {
      this.btnDeleteContact.addEventListener('click', () => this.handleDeleteCurrentContact());
    }
    if (this.btnDownloadVCardSingle) {
      this.btnDownloadVCardSingle.addEventListener('click', () => {
        if (this.activeContactDetailId) {
          const c = window.storageService.getContactById(this.activeContactDetailId);
          if (c) {
            window.exportService.downloadVCard(c);
            this.showToast(`vCard for ${c.name} downloaded`, 'success');
          }
        }
      });
    }

    // Modals: Organization Matching
    if (this.btnCloseOrgMatching) {
      this.btnCloseOrgMatching.addEventListener('click', () => this.closeModal('modalOrgMatching'));
    }

    // Modals: Sales Representative Profile
    if (this.btnCloseSalesRepModal) {
      this.btnCloseSalesRepModal.addEventListener('click', () => this.closeModal('modalSalesRep'));
    }
    if (this.btnCancelSalesRep) {
      this.btnCancelSalesRep.addEventListener('click', () => this.closeModal('modalSalesRep'));
    }
    if (this.btnSaveSalesRep) {
      this.btnSaveSalesRep.addEventListener('click', () => this.handleSaveSalesRepProfile());
    }
    if (this.btnEditSalesRepProfile) {
      this.btnEditSalesRepProfile.addEventListener('click', () => this.openSalesRepModal());
    }

    // Modals: Event Creation
    if (this.btnAddNewEvent) {
      this.btnAddNewEvent.addEventListener('click', () => this.openModal('modalAddEvent'));
    }
    if (this.btnCloseAddEvent) {
      this.btnCloseAddEvent.addEventListener('click', () => this.closeModal('modalAddEvent'));
    }
    if (this.btnCancelAddEvent) {
      this.btnCancelAddEvent.addEventListener('click', () => this.closeModal('modalAddEvent'));
    }
    if (this.btnSubmitAddEvent) {
      this.btnSubmitAddEvent.addEventListener('click', () => this.handleCreateNewEvent());
    }

    // Settings & Team Cloud Sync
    if (this.btnSyncTeamNow) {
      this.btnSyncTeamNow.addEventListener('click', () => this.handleSyncTeamWorkspace());
    }
    if (this.btnShareTeamBundle) {
      this.btnShareTeamBundle.addEventListener('click', () => this.handleExportTeamBundle());
    }
    if (this.btnImportTeamBundle) {
      this.btnImportTeamBundle.addEventListener('click', () => {
        if (this.fileImportTeamBundle) this.fileImportTeamBundle.click();
      });
    }
    if (this.fileImportTeamBundle) {
      this.fileImportTeamBundle.addEventListener('change', (e) => this.handleImportTeamBundle(e));
    }
    if (this.btnExportFullBackup) {
      this.btnExportFullBackup.addEventListener('click', () => this.handleExportFullBackup());
    }
    if (this.btnRestoreBackup) {
      this.btnRestoreBackup.addEventListener('click', () => {
        if (this.fileRestoreBackup) this.fileRestoreBackup.click();
      });
    }
    if (this.fileRestoreBackup) {
      this.fileRestoreBackup.addEventListener('change', (e) => this.handleRestoreBackup(e));
    }
    if (this.btnClearAllData) {
      this.btnClearAllData.addEventListener('click', () => this.handleClearAllData());
    }

    // Gemini API & Models
    if (this.btnRefreshModels) {
      this.btnRefreshModels.addEventListener('click', () => this.handleFetchModels());
    }
    if (this.btnTestModel) {
      this.btnTestModel.addEventListener('click', () => this.handleTestGeminiModel());
    }
    if (this.settingGeminiApiKey) {
      this.settingGeminiApiKey.addEventListener('change', () => {
        window.storageService.setGeminiApiKey(this.settingGeminiApiKey.value.trim());
        this.showToast('Gemini API key updated', 'info');
      });
    }
    if (this.settingModelSelect) {
      this.settingModelSelect.addEventListener('change', () => {
        window.storageService.setSelectedModel(this.settingModelSelect.value);
        this.showToast(`Active AI Model: ${this.settingModelSelect.value}`, 'info');
      });
    }
    if (this.inputTeamWorkspaceCode) {
      this.inputTeamWorkspaceCode.addEventListener('change', () => {
        const code = this.inputTeamWorkspaceCode.value.trim().toUpperCase();
        window.storageService.saveTeamSyncConfig({ workspaceCode: code });
        this.showToast(`Team Workspace Code set to: ${code}`, 'success');
      });
    }
  }

  // --- VIEW SWITCHER ---
  switchView(viewId) {
    this.currentView = viewId;
    this.viewScreens.forEach(screen => {
      screen.classList.toggle('active', screen.id === viewId);
    });

    this.navBtns.forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-target') === viewId);
    });

    if (viewId === 'viewContacts') {
      this.renderContacts();
      this.renderEventFilterPills();
    } else if (viewId === 'viewOrganizations') {
      this.renderOrganizations();
    } else if (viewId === 'viewEvents') {
      this.renderEvents();
    } else if (viewId === 'viewTasks') {
      this.renderTasks();
    } else if (viewId === 'viewScanner') {
      this.updateHeaderActiveEvent();
    } else if (viewId === 'viewSettings') {
      this.loadSettings();
      this.updateSalesRepUI();
    }
  }

  // --- SALES REPRESENTATIVE PROFILE ---
  updateSalesRepUI() {
    const profile = window.storageService.getSalesRepProfile();
    const isConfigured = Boolean(profile.name && profile.employeeId);

    const initials = isConfigured
      ? profile.name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase()
      : '👤';

    if (this.headerRepInitials) this.headerRepInitials.textContent = initials;
    if (this.headerRepName) {
      this.headerRepName.textContent = isConfigured ? profile.name : 'Setup Rep Profile';
    }

    if (this.settingsRepAvatar) this.settingsRepAvatar.textContent = initials;
    if (this.settingsRepNameDisplay) {
      this.settingsRepNameDisplay.textContent = isConfigured
        ? profile.name
        : 'Setup Sales Profile';
    }
    if (this.settingsRepMetaDisplay) {
      this.settingsRepMetaDisplay.textContent = isConfigured
        ? `${profile.employeeId} • ${profile.email || 'Team Workspace: ' + profile.teamCode}`
        : 'Stamp your scans with Employee ID & email';
    }

    if (this.inputTeamWorkspaceCode && profile.teamCode) {
      this.inputTeamWorkspaceCode.value = profile.teamCode;
    }
  }

  openSalesRepModal() {
    const profile = window.storageService.getSalesRepProfile();
    if (this.repFormName) this.repFormName.value = profile.name || '';
    if (this.repFormEmpId) this.repFormEmpId.value = profile.employeeId || '';
    if (this.repFormPhone) this.repFormPhone.value = profile.phone || '';
    if (this.repFormEmail) this.repFormEmail.value = profile.email || '';
    if (this.repFormRegion) this.repFormRegion.value = profile.region || '';
    if (this.repFormDesignation) this.repFormDesignation.value = profile.designation || '';
    if (this.repFormTeamCode) this.repFormTeamCode.value = profile.teamCode || 'ALPHA-SALES-2026';

    this.openModal('modalSalesRep');
  }

  handleSaveSalesRepProfile() {
    const name = (this.repFormName ? this.repFormName.value : '').trim();
    const employeeId = (this.repFormEmpId ? this.repFormEmpId.value : '').trim().toUpperCase();
    const email = (this.repFormEmail ? this.repFormEmail.value : '').trim().toLowerCase();
    const phone = (this.repFormPhone ? this.repFormPhone.value : '').trim();
    const region = (this.repFormRegion ? this.repFormRegion.value : '').trim();
    const designation = (this.repFormDesignation ? this.repFormDesignation.value : '').trim();
    const teamCode = ((this.repFormTeamCode ? this.repFormTeamCode.value : '').trim() || 'ALPHA-SALES-2026').toUpperCase();

    if (!name || !employeeId) {
      alert('Please provide at least your Employee Name and Employee ID.');
      return;
    }

    const saved = window.storageService.saveSalesRepProfile({
      name,
      employeeId,
      email,
      phone,
      region,
      designation,
      teamCode
    });

    this.closeModal('modalSalesRep');
    this.updateSalesRepUI();
    this.showToast(`Sales Profile saved for ${saved.name} (${saved.employeeId})`, 'success');
  }

  // --- CAMERA & CARD SCANNING ---
  async initCamera() {
    try {
      const res = await window.cameraService.startCamera(this.cameraVideo);
      if (!res.success) {
        console.log('Camera note: Live viewfinder unavailable or permission pending.');
      }
    } catch (e) {
      console.warn('Camera error:', e);
    }
  }

  async toggleTorch() {
    if (!window.cameraService.stream) {
      this.showToast('Torch is available only during active camera preview', 'info');
      return;
    }
    const track = window.cameraService.stream.getVideoTracks()[0];
    if (track && track.getCapabilities && track.getCapabilities().torch) {
      const newState = !window.cameraService.isTorchOn;
      await track.applyConstraints({ advanced: [{ torch: newState }] });
      window.cameraService.isTorchOn = newState;
      this.showToast(newState ? 'Flashlight ON' : 'Flashlight OFF', 'info');
    } else {
      this.showToast('Torch hardware not supported on this device', 'info');
    }
  }

  async handleCaptureCard() {
    let base64 = window.cameraService.captureFrame();

    // If live camera frame is not available, open native camera file capture
    if (!base64) {
      if (this.cameraPhotoInput) {
        this.cameraPhotoInput.click();
        return;
      }
      this.showToast('Please select a business card image from your camera or gallery.', 'info');
      return;
    }

    await this.processCardWithAI(base64);
  }

  async handleFileUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    try {
      this.showToast('Loading visiting card image...', 'info');
      const base64 = await window.cameraService.readFileAsBase64(file);
      await this.processCardWithAI(base64);
    } catch (err) {
      this.showToast('Failed to load image: ' + err.message, 'error');
    } finally {
      if (this.fileCardInput) this.fileCardInput.value = '';
      if (this.cameraPhotoInput) this.cameraPhotoInput.value = '';
    }
  }

  openManualContactModal() {
    const template = window.geminiService.createCleanManualTemplate();
    this.tempScannedCard = template;
    this.openScanReviewModal(this.tempScannedCard);
  }

  async processCardWithAI(imageBase64) {
    this.showAIProcessing(true, 'Scanning Visiting Card...', 'Extracting corporate identity, names & contact information');

    const descSteps = [
      'Extracting company name, phone, WhatsApp & email...',
      'Deep AI analysis: Querying LinkedIn career profile & company background...',
      'Synthesizing past organizations, alumni networks & conversation icebreakers...',
      'Finalizing CRM action items...'
    ];

    let stepIndex = 0;
    const interval = setInterval(() => {
      if (stepIndex < descSteps.length) {
        if (this.aiProcessingDesc) {
          this.aiProcessingDesc.textContent = descSteps[stepIndex];
        }
        stepIndex++;
      }
    }, 600);

    try {
      const extracted = await window.geminiService.scanAndEnrichVisitingCard(imageBase64);
      clearInterval(interval);
      this.showAIProcessing(false);

      this.tempScannedCard = {
        ...extracted,
        cardImage: imageBase64
      };

      this.openScanReviewModal(this.tempScannedCard);
    } catch (err) {
      clearInterval(interval);
      this.showAIProcessing(false);
      this.showToast('Scan note: ' + err.message, 'error');
    }
  }

  showAIProcessing(show, title, desc) {
    if (!this.aiProcessingOverlay) return;
    if (show) {
      if (this.aiProcessingTitle) this.aiProcessingTitle.textContent = title || 'Analyzing Card...';
      if (this.aiProcessingDesc) this.aiProcessingDesc.textContent = desc || 'Gemini Vision AI is extracting details...';
      this.aiProcessingOverlay.classList.add('active');
    } else {
      this.aiProcessingOverlay.classList.remove('active');
    }
  }

  // --- MODAL: SCAN REVIEW ---
  openScanReviewModal(data) {
    this.populateEventSelect('reviewEventSelect', data.eventId || window.storageService.getActiveEventId());

    if (this.reviewCardImagePreview) {
      this.reviewCardImagePreview.src = data.cardImage || '';
      this.reviewCardImagePreview.parentElement.style.display = data.cardImage ? 'flex' : 'none';
    }

    const setVal = (id, val) => {
      const el = document.getElementById(id);
      if (el) el.value = val || '';
    };

    setVal('reviewHonorific', data.honorific);
    setVal('reviewName', data.name);
    setVal('reviewTitle', data.title);
    setVal('reviewCompany', data.company);
    setVal('reviewIndustry', data.industry);
    setVal('reviewPhone', data.phone);
    setVal('reviewWhatsapp', data.whatsapp || data.phone);
    setVal('reviewEmail', data.email);
    setVal('reviewLinkedinUrl', data.linkedinUrl);
    setVal('reviewPastWork', data.pastWorkSummary);
    setVal('reviewNotes', data.notes);

    // Populate comma-separated past organizations list
    if (this.reviewPastOrgsList) {
      const list = Array.isArray(data.pastCompaniesList)
        ? data.pastCompaniesList
        : (Array.isArray(data.pastOrganizations) ? data.pastOrganizations.map(o => typeof o === 'string' ? o : o.companyName) : []);
      this.reviewPastOrgsList.value = list.filter(Boolean).join(', ');
    }

    // Qualification fields
    if (this.reviewDecisionStatus) this.reviewDecisionStatus.value = data.decisionStatus || 'Decision Maker';
    if (this.reviewLeadScore) this.reviewLeadScore.value = data.leadScore || 'Hot';
    if (this.reviewBudget) this.reviewBudget.value = data.dealBudget || data.budget || '$50k - $200k+';
    if (this.reviewTimeline) this.reviewTimeline.value = data.buyingTimeline || data.timeline || 'Immediate (< 1 mo)';

    // Action items checkboxes
    const actionContainer = document.getElementById('reviewSuggestedActionItems');
    if (actionContainer) {
      actionContainer.innerHTML = '';
      const suggested = (Array.isArray(data.suggestedActionItems) && data.suggestedActionItems.length > 0)
        ? data.suggestedActionItems
        : [
            'Send introduction email with product overview deck',
            'Connect on LinkedIn with personalized note',
            'Schedule 15-minute qualification call'
          ];

      suggested.forEach((itemText, idx) => {
        const div = document.createElement('label');
        div.style.display = 'flex';
        div.style.alignItems = 'center';
        div.style.gap = '8px';
        div.style.fontSize = '12px';
        div.style.color = '#cbd5e1';
        div.style.cursor = 'pointer';
        div.innerHTML = `
          <input type="checkbox" checked value="${itemText.replace(/"/g, '&quot;')}" class="review-action-checkbox">
          <span>${itemText}</span>
        `;
        actionContainer.appendChild(div);
      });
    }

    this.openModal('modalScanReview');
  }

  handleSaveReviewContact() {
    const nameInput = document.getElementById('reviewName');
    const name = nameInput ? nameInput.value.trim() : '';
    if (!name) {
      alert('Please enter at least the contact name.');
      return;
    }

    // Collect checked action items
    const checkedCheckboxes = document.querySelectorAll('.review-action-checkbox:checked');
    const actionItems = Array.from(checkedCheckboxes).map((cb, idx) => ({
      id: 'act-' + Date.now() + '-' + idx,
      title: cb.value,
      dueDate: new Date(Date.now() + (idx + 1) * 2 * 86400000).toISOString().split('T')[0],
      priority: idx === 0 ? 'high' : 'medium',
      completed: false
    }));

    // Collect structured past organizations
    let pastOrganizations = [];
    let pastCompaniesList = [];
    if (this.reviewPastOrgsList && this.reviewPastOrgsList.value.trim()) {
      pastCompaniesList = this.reviewPastOrgsList.value
        .split(',')
        .map(s => s.trim())
        .filter(Boolean);

      pastOrganizations = pastCompaniesList.map(orgName => ({
        companyName: orgName,
        role: 'Former Associate / Alumni',
        duration: '',
        notes: ''
      }));
    }

    const rep = window.storageService.getSalesRepProfile();

    const getVal = (id) => {
      const el = document.getElementById(id);
      return el ? el.value.trim() : '';
    };

    const contactData = {
      name: name,
      honorific: getVal('reviewHonorific'),
      title: getVal('reviewTitle'),
      company: getVal('reviewCompany'),
      industry: getVal('reviewIndustry'),
      phone: getVal('reviewPhone'),
      whatsapp: getVal('reviewWhatsapp'),
      email: getVal('reviewEmail'),
      eventId: document.getElementById('reviewEventSelect') ? document.getElementById('reviewEventSelect').value : '',
      linkedinUrl: getVal('reviewLinkedinUrl'),
      pastWorkSummary: getVal('reviewPastWork'),
      pastOrganizations: pastOrganizations,
      pastCompaniesList: pastCompaniesList,
      alumniInstitutions: (this.tempScannedCard && Array.isArray(this.tempScannedCard.alumniInstitutions)) ? this.tempScannedCard.alumniInstitutions : [],
      companyOverview: (this.tempScannedCard && this.tempScannedCard.companyOverview) || '',
      salesIcebreakers: (this.tempScannedCard && Array.isArray(this.tempScannedCard.salesIcebreakers)) ? this.tempScannedCard.salesIcebreakers : [],
      notes: getVal('reviewNotes'),
      cardImage: this.tempScannedCard ? this.tempScannedCard.cardImage : '',
      actionItems: actionItems,
      decisionStatus: this.reviewDecisionStatus ? this.reviewDecisionStatus.value : 'Decision Maker',
      leadScore: this.reviewLeadScore ? this.reviewLeadScore.value : 'Hot',
      dealBudget: this.reviewBudget ? this.reviewBudget.value : '$50k - $200k+',
      buyingTimeline: this.reviewTimeline ? this.reviewTimeline.value : 'Immediate (< 1 mo)',
      scannedBy: {
        name: rep.name || 'Sales Rep',
        email: rep.email || '',
        employeeId: rep.employeeId || 'REP-01',
        region: rep.region || ''
      }
    };

    const saved = window.storageService.addContact(contactData);
    this.closeModal('modalScanReview');
    this.showToast(`✓ Lead "${saved.name}" saved with rep attribution!`, 'success');

    // Prompt to save .vcf card directly to Android native contacts
    setTimeout(() => {
      if (confirm(`Would you like to save ${saved.name} directly to your phone's Android contact book?`)) {
        window.exportService.downloadVCard(saved);
      }
    }, 250);

    this.renderContacts();
    this.renderOrganizations();
    this.renderEvents();
    this.renderTasks();
    this.updateHeaderActiveEvent();
  }

  // --- CONTACTS DIRECTORY VIEW ---
  renderContacts() {
    const allContacts = window.storageService.getContacts();
    const orgs = window.storageService.getAllOrganizations();
    const allTasks = window.storageService.getAllActionItems();
    const pendingTasks = allTasks.filter(t => !t.completed);
    const rep = window.storageService.getSalesRepProfile();

    // Stats Bar
    if (this.statTotalContacts) this.statTotalContacts.textContent = allContacts.length;
    if (this.statTotalOrgs) this.statTotalOrgs.textContent = orgs.length;
    if (this.statPendingTasks) this.statPendingTasks.textContent = pendingTasks.length;

    // Filter Logic
    let filtered = allContacts;

    // 1. Event filter
    if (this.currentEventFilter !== 'all') {
      filtered = filtered.filter(c => c.eventId === this.currentEventFilter);
    }

    // 2. Scope filter (All Leads vs My Scans vs Hot Leads vs Decision Makers)
    if (this.currentScopeFilter === 'my') {
      filtered = filtered.filter(c => {
        const creatorId = c.scannedBy ? (c.scannedBy.employeeId || '') : '';
        const creatorName = c.scannedBy ? (c.scannedBy.name || '') : '';
        return (rep.employeeId && creatorId === rep.employeeId) ||
               (rep.name && creatorName.toLowerCase() === rep.name.toLowerCase());
      });
    } else if (this.currentScopeFilter === 'hot') {
      filtered = filtered.filter(c => c.leadScore === 'Hot');
    } else if (this.currentScopeFilter === 'decision') {
      filtered = filtered.filter(c => c.decisionStatus === 'Decision Maker');
    }

    // 3. Search query
    if (this.searchQuery) {
      filtered = filtered.filter(c => {
        const pastOrgsString = (c.pastCompaniesList || []).join(' ');
        const alumniString = (c.alumniInstitutions || []).join(' ');
        const repString = c.scannedBy ? `${c.scannedBy.name} ${c.scannedBy.employeeId}` : '';
        const fullSearchable = `${c.name} ${c.company} ${c.title} ${c.industry} ${c.email} ${c.phone} ${c.pastWorkSummary} ${pastOrgsString} ${alumniString} ${repString} ${c.notes}`.toLowerCase();
        return fullSearchable.includes(this.searchQuery);
      });
    }

    if (this.contactsCountLabel) {
      this.contactsCountLabel.textContent = `${filtered.length} of ${allContacts.length} Leads`;
    }

    if (!this.contactsFeed) return;
    this.contactsFeed.innerHTML = '';

    // Clean Zero-Dummy Empty State
    if (filtered.length === 0) {
      const isFiltered = this.searchQuery || this.currentScopeFilter !== 'all' || this.currentEventFilter !== 'all';
      this.contactsFeed.innerHTML = `
        <div class="empty-state-card">
          <div class="empty-state-icon">📇</div>
          <div class="empty-state-title">${isFiltered ? 'No matching leads' : 'No Business Cards Scanned Yet'}</div>
          <div class="empty-state-desc">
            ${isFiltered
              ? 'Try resetting your search query or switching scope filters.'
              : 'Scan visiting cards from trade shows, networking summits, or add leads manually.'}
          </div>
          ${!isFiltered ? `
            <button class="btn-primary" style="margin-top: 14px;" onclick="window.app.switchView('viewScanner')">
              📸 Scan Your First Card
            </button>
          ` : ''}
        </div>
      `;
      return;
    }

    filtered.forEach(contact => {
      const event = window.storageService.getEventById(contact.eventId);
      const eventName = event ? event.name : 'General Lead';
      const eventColor = event ? event.badgeColor : '#6366f1';

      const initials = (contact.name || 'C')
        .split(' ')
        .map(n => n[0])
        .slice(0, 2)
        .join('')
        .toUpperCase();

      const card = document.createElement('div');
      card.className = 'contact-card';
      card.setAttribute('data-id', contact.id);

      const pendingCount = (contact.actionItems || []).filter(a => !a.completed).length;
      const repAttribution = contact.scannedBy ? (contact.scannedBy.name || contact.scannedBy.employeeId) : null;
      const pastOrgs = contact.pastCompaniesList || [];

      card.innerHTML = `
        <div class="contact-card-top">
          <div class="contact-avatar" style="background: ${contact.cardColorTheme || 'linear-gradient(135deg, #6366f1 0%, #4338ca 100%)'};">
            ${initials}
          </div>
          <div class="contact-info-meta">
            <div class="contact-name-row">
              <span class="contact-name">${contact.honorific ? contact.honorific + ' ' : ''}${contact.name}</span>
              <span class="event-badge-tag" style="border-color: ${eventColor}55; color: ${eventColor}; background: ${eventColor}15;">
                ${eventName}
              </span>
            </div>
            <div class="contact-title-company">
              ${contact.title ? contact.title + ' • ' : ''}<strong>${contact.company || 'Private Entity'}</strong>
            </div>
            ${repAttribution ? `
              <div style="font-size: 10.5px; color: var(--text-subtle); margin-top: 2px;">
                👤 Scanned by: <strong>${repAttribution}</strong>
              </div>
            ` : ''}
          </div>
        </div>

        ${contact.pastWorkSummary ? `
          <div class="contact-ai-preview">
            <strong>Past Work & Role:</strong> ${contact.pastWorkSummary}
          </div>
        ` : ''}

        ${pastOrgs.length > 0 ? `
          <div class="org-tags-row" style="display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px;">
            ${pastOrgs.slice(0, 3).map(orgName => `
              <span class="org-tag-chip" onclick="event.stopPropagation(); window.app.openOrgMatchingModal('${orgName.replace(/'/g, "\\'")}')" title="View all contacts associated with ${orgName}">
                🏢 ${orgName}
              </span>
            `).join('')}
            ${pastOrgs.length > 3 ? `<span style="font-size: 10px; color: var(--text-subtle); align-self: center;">+${pastOrgs.length - 3} more</span>` : ''}
          </div>
        ` : ''}

        <div class="contact-card-actions">
          <div class="contact-quick-buttons">
            ${contact.phone ? `
              <a href="tel:${contact.phone}" class="action-icon-pill" title="Call ${contact.phone}" onclick="event.stopPropagation();">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg>
              </a>
            ` : ''}
            ${contact.whatsapp ? `
              <a href="https://wa.me/${contact.whatsapp.replace(/[^0-9]/g, '')}" target="_blank" class="action-icon-pill whatsapp" title="WhatsApp Chat" onclick="event.stopPropagation();">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path></svg>
              </a>
            ` : ''}
            ${contact.email ? `
              <a href="mailto:${contact.email}" class="action-icon-pill" title="Send Email" onclick="event.stopPropagation();">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
              </a>
            ` : ''}
            ${contact.linkedinUrl ? `
              <a href="${contact.linkedinUrl}" target="_blank" class="action-icon-pill linkedin" title="LinkedIn Profile" onclick="event.stopPropagation();">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z"></path><rect x="2" y="9" width="4" height="12"></rect><circle cx="4" cy="4" r="2"></circle></svg>
              </a>
            ` : ''}
            <button class="action-icon-pill" title="Save .vcf to Phone" onclick="event.stopPropagation(); window.app.downloadSingleVCard('${contact.id}');">
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline><polyline points="7 3 7 8 15 8"></polyline></svg>
            </button>
          </div>

          <div style="display: flex; align-items: center; gap: 8px;">
            ${pendingCount > 0 ? `
              <span class="contact-tasks-badge">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 11 12 14 22 4"></polyline></svg>
                ${pendingCount} task${pendingCount > 1 ? 's' : ''}
              </span>
            ` : ''}
            <span style="font-size: 11px; color: #818cf8; font-weight: 600;">Details ›</span>
          </div>
        </div>
      `;

      card.addEventListener('click', () => {
        this.openContactDetailModal(contact.id);
      });

      this.contactsFeed.appendChild(card);
    });
  }

  renderEventFilterPills() {
    if (!this.contactsEventFilterPills) return;
    const events = window.storageService.getEvents();

    this.contactsEventFilterPills.innerHTML = `
      <button class="filter-pill ${this.currentEventFilter === 'all' ? 'active' : ''}" data-event-id="all">
        All Events
      </button>
    `;

    events.forEach(evt => {
      const pill = document.createElement('button');
      pill.className = `filter-pill ${this.currentEventFilter === evt.id ? 'active' : ''}`;
      pill.setAttribute('data-event-id', evt.id);
      pill.textContent = evt.name;
      this.contactsEventFilterPills.appendChild(pill);
    });

    this.contactsEventFilterPills.querySelectorAll('.filter-pill').forEach(pill => {
      pill.addEventListener('click', () => {
        this.contactsEventFilterPills.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        this.currentEventFilter = pill.getAttribute('data-event-id');
        this.renderContacts();
      });
    });
  }

  // --- ORGANIZATIONS & ALUMNI DIRECTORY VIEW ---
  renderOrganizations() {
    const orgs = window.storageService.getAllOrganizations();

    let filtered = orgs;
    if (this.orgSearchQuery) {
      filtered = filtered.filter(org => {
        const memberNames = [...org.currentContacts, ...org.pastContacts]
          .map(m => m.contact ? m.contact.name : '')
          .join(' ');
        const full = `${org.name} ${org.industry} ${memberNames}`.toLowerCase();
        return full.includes(this.orgSearchQuery);
      });
    }

    if (this.orgsCountLabel) {
      this.orgsCountLabel.textContent = `${filtered.length} Organizations Identified`;
    }

    if (!this.orgsFeed) return;
    this.orgsFeed.innerHTML = '';

    if (filtered.length === 0) {
      this.orgsFeed.innerHTML = `
        <div class="empty-state-card">
          <div class="empty-state-icon">🏢</div>
          <div class="empty-state-title">No Organizations Found</div>
          <div class="empty-state-desc">
            As you scan business cards, companies and past employers are automatically cataloged here for organizational matching.
          </div>
        </div>
      `;
      return;
    }

    filtered.forEach(org => {
      const initials = (org.name || 'O')
        .split(' ')
        .map(w => w[0])
        .slice(0, 2)
        .join('')
        .toUpperCase();

      const card = document.createElement('div');
      card.className = 'org-card';

      card.innerHTML = `
        <div class="org-card-header">
          <div class="org-avatar-badge">${initials}</div>
          <div class="org-info-block">
            <h4 class="org-name">${org.name}</h4>
            <span class="org-industry">${org.industry}</span>
          </div>
        </div>

        <div class="org-stats-row">
          <div class="org-badge-pill current">
            🏢 ${org.currentContacts.length} Current Member${org.currentContacts.length === 1 ? '' : 's'}
          </div>
          <div class="org-badge-pill past">
            🎓 ${org.pastContacts.length} Alumni / Past
          </div>
        </div>

        <div class="org-members-preview">
          ${[...org.currentContacts.slice(0, 2), ...org.pastContacts.slice(0, 2)].map(m => `
            <div class="org-member-chip">
              <span class="org-member-dot ${m.type === 'current' ? 'current' : 'past'}"></span>
              <span class="org-member-name">${m.contact ? m.contact.name : 'Contact'}</span>
              <span class="org-member-role">(${m.role || (m.type === 'current' ? 'Staff' : 'Alumni')})</span>
            </div>
          `).join('')}
          ${(org.totalCount > 4) ? `<span style="font-size: 11px; color: var(--text-subtle); padding-left: 4px;">+${org.totalCount - 4} more associated</span>` : ''}
        </div>

        <div class="org-card-footer">
          <span style="font-size: 11.5px; color: var(--text-muted);">
            ${org.totalCount} Associated Professional${org.totalCount === 1 ? '' : 's'}
          </span>
          <button class="btn-secondary" style="height: 32px; font-size: 11px; padding: 0 12px;" onclick="window.app.openOrgMatchingModal('${org.name.replace(/'/g, "\\'")}')">
            View Matches ›
          </button>
        </div>
      `;

      this.orgsFeed.appendChild(card);
    });
  }

  // --- MODAL: ORGANIZATION MATCHING INSPECTOR ---
  openOrgMatchingModal(orgName) {
    const data = window.storageService.getContactsForOrganization(orgName);
    if (!data) return;

    if (this.orgMatchingModalTitle) {
      this.orgMatchingModalTitle.textContent = `Affiliations for "${data.name}"`;
    }

    if (!this.orgMatchingModalBody) return;

    const currentHtml = (data.currentContacts.length > 0)
      ? data.currentContacts.map(m => `
          <div class="org-match-item current">
            <div style="display: flex; justify-content: space-between; align-items: flex-start;">
              <div>
                <div style="font-size: 14px; font-weight: 700; color: #fff;">
                  ${m.contact.honorific ? m.contact.honorific + ' ' : ''}${m.contact.name}
                </div>
                <div style="font-size: 12px; color: #a5b4fc; font-weight: 600;">
                  ${m.role || m.contact.title || 'Executive'}
                </div>
                <div style="font-size: 11px; color: var(--text-muted); margin-top: 2px;">
                  Score: <strong>${m.contact.leadScore || 'Hot'}</strong> • Status: ${m.contact.decisionStatus || 'Decision Maker'}
                </div>
              </div>
              <span class="org-badge-pill current" style="font-size: 10px;">Current Staff</span>
            </div>
            <div style="display: flex; gap: 8px; margin-top: 8px;">
              ${m.contact.phone ? `<a href="tel:${m.contact.phone}" class="action-icon-pill" title="Call"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg></a>` : ''}
              ${m.contact.email ? `<a href="mailto:${m.contact.email}" class="action-icon-pill" title="Email"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg></a>` : ''}
              <button class="btn-text-sm" onclick="window.app.closeModal('modalOrgMatching'); window.app.openContactDetailModal('${m.contact.id}')" style="margin-left: auto;">
                View Lead Card ›
              </button>
            </div>
          </div>
        `).join('')
      : '<div style="font-size: 12px; color: var(--text-muted); padding: 8px 0;">No active staff recorded currently at this company.</div>';

    const pastHtml = (data.pastContacts.length > 0)
      ? data.pastContacts.map(m => `
          <div class="org-match-item past">
            <div style="display: flex; justify-content: space-between; align-items: flex-start;">
              <div>
                <div style="font-size: 14px; font-weight: 700; color: #fff;">
                  ${m.contact.honorific ? m.contact.honorific + ' ' : ''}${m.contact.name}
                </div>
                <div style="font-size: 12px; color: #cbd5e1;">
                  Now: <strong>${m.contact.title || 'Executive'}</strong> at <strong>${m.contact.company || 'Enterprise'}</strong>
                </div>
                <div style="font-size: 11px; color: #93c5fd; margin-top: 2px;">
                  Past Association: ${m.role || 'Former Employee / Alumni'}
                </div>
              </div>
              <span class="org-badge-pill past" style="font-size: 10px;">Alumni</span>
            </div>
            <div style="display: flex; gap: 8px; margin-top: 8px;">
              ${m.contact.phone ? `<a href="tel:${m.contact.phone}" class="action-icon-pill" title="Call"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg></a>` : ''}
              ${m.contact.email ? `<a href="mailto:${m.contact.email}" class="action-icon-pill" title="Email"><svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg></a>` : ''}
              <button class="btn-text-sm" onclick="window.app.closeModal('modalOrgMatching'); window.app.openContactDetailModal('${m.contact.id}')" style="margin-left: auto;">
                View Lead Card ›
              </button>
            </div>
          </div>
        `).join('')
      : '<div style="font-size: 12px; color: var(--text-muted); padding: 8px 0;">No alumni from this company in database.</div>';

    this.orgMatchingModalBody.innerHTML = `
      <!-- Intelligence Summary Box -->
      <div style="background: rgba(30, 41, 59, 0.7); border-radius: var(--border-radius-md); padding: 12px; border: 1px solid var(--border-color); margin-bottom: 16px;">
        <div style="font-size: 12px; color: #a5b4fc; font-weight: 700; margin-bottom: 4px;">
          Strategic Warm Introduction Network
        </div>
        <div style="font-size: 11.5px; color: var(--text-muted); line-height: 1.4;">
          Identified <strong>${data.totalCount} associated professional${data.totalCount === 1 ? '' : 's'}</strong> (${data.currentContacts.length} current, ${data.pastContacts.length} alumni). Use former colleagues to request warm introductions into ${data.name}.
        </div>
      </div>

      <div class="detail-section-block">
        <h3 style="display: flex; align-items: center; justify-content: space-between;">
          <span>Current Organization Members</span>
          <span style="font-size: 11px; color: #10b981;">(${data.currentContacts.length})</span>
        </h3>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          ${currentHtml}
        </div>
      </div>

      <div class="detail-section-block">
        <h3 style="display: flex; align-items: center; justify-content: space-between;">
          <span>Alumni & Former Affiliations</span>
          <span style="font-size: 11px; color: #60a5fa;">(${data.pastContacts.length})</span>
        </h3>
        <div style="display: flex; flex-direction: column; gap: 8px;">
          ${pastHtml}
        </div>
      </div>
    `;

    this.openModal('modalOrgMatching');
  }

  // --- MODAL: FULL CONTACT DETAILS ---
  openContactDetailModal(contactId) {
    this.activeContactDetailId = contactId;
    const contact = window.storageService.getContactById(contactId);
    if (!contact) return;

    const event = window.storageService.getEventById(contact.eventId);
    const eventName = event ? event.name : 'Unassigned Lead';
    const repInfo = contact.scannedBy || { name: 'Sales Rep', employeeId: 'EMP' };
    const pastOrgs = contact.pastCompaniesList || [];

    if (this.detailSheetHeaderTitle) {
      this.detailSheetHeaderTitle.textContent = contact.name || 'Contact Details';
    }

    this.detailSheetContentBody.innerHTML = `
      <!-- Contact Hero Card -->
      <div class="contact-hero-card" style="background: ${contact.cardColorTheme || 'linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)'};">
        <div class="contact-hero-details">
          <h2>${contact.honorific ? contact.honorific + ' ' : ''}${contact.name}</h2>
          <div class="hero-title-company">${contact.title || 'Executive'} at <strong>${contact.company || 'Enterprise'}</strong></div>
          <div style="font-size: 11px; margin-top: 6px; opacity: 0.85;">📍 Event: ${eventName} • ${contact.industry || 'Technology'}</div>

          <!-- Sales Rep Corporate Stamp -->
          <div style="font-size: 11px; color: #c7d2fe; margin-top: 8px; display: flex; align-items: center; gap: 6px;">
            <span>👤 Scanned by: <strong>${repInfo.name}</strong> (${repInfo.employeeId || 'REP'})</span>
          </div>

          <!-- Executive Sales Badges -->
          <div style="display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px;">
            <span class="event-badge-tag" style="background: rgba(239, 68, 68, 0.3); border-color: #ef4444; color: #fca5a5; font-size: 11px;">
              ${contact.leadScore === 'Hot' ? '🔥 Hot Lead' : contact.leadScore === 'Warm' ? '⚡ Warm Lead' : '🌱 Nurture'}
            </span>
            <span class="event-badge-tag" style="background: rgba(16, 185, 129, 0.3); border-color: #10b981; color: #6ee7b7; font-size: 11px;">
              👔 ${contact.decisionStatus || 'Decision Maker'}
            </span>
            ${contact.dealBudget ? `
              <span class="event-badge-tag" style="background: rgba(245, 158, 11, 0.3); border-color: #f59e0b; color: #fde68a; font-size: 11px;">
                💰 ${contact.dealBudget}
              </span>
            ` : ''}
            ${contact.buyingTimeline ? `
              <span class="event-badge-tag" style="background: rgba(6, 182, 212, 0.3); border-color: #06b6d4; color: #a5f3fc; font-size: 11px;">
                ⏱️ ${contact.buyingTimeline}
              </span>
            ` : ''}
          </div>
        </div>
      </div>

      <!-- Quick Action Matrix -->
      <div class="quick-action-matrix">
        <a href="tel:${contact.phone}" class="matrix-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="#6366f1" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg>
          <span>Call</span>
        </a>
        <a href="https://wa.me/${(contact.whatsapp || contact.phone || '').replace(/[^0-9]/g, '')}" target="_blank" class="matrix-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="#10b981" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"></path></svg>
          <span>WhatsApp</span>
        </a>
        <a href="mailto:${contact.email}" class="matrix-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="#06b6d4" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
          <span>Email</span>
        </a>
        <a href="${contact.linkedinUrl || '#'}" target="_blank" class="matrix-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="#38bdf8" stroke-width="2"><path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z"></path><rect x="2" y="9" width="4" height="12"></rect><circle cx="4" cy="4" r="2"></circle></svg>
          <span>LinkedIn</span>
        </a>
      </div>

      <!-- Past Career & Structured Organizations -->
      <div class="detail-section-block">
        <h3>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
          Career History & Associated Organizations
        </h3>
        <p class="detail-text-p">${contact.pastWorkSummary || 'Career background recorded from business card.'}</p>

        ${pastOrgs.length > 0 ? `
          <div style="margin-top: 10px;">
            <div style="font-size: 11px; color: var(--text-muted); margin-bottom: 6px;">Tap company to inspect all team leads affiliated with it:</div>
            <div style="display: flex; gap: 6px; flex-wrap: wrap;">
              ${pastOrgs.map(orgName => `
                <span class="org-tag-chip" onclick="window.app.openOrgMatchingModal('${orgName.replace(/'/g, "\\'")}')">
                  🏢 ${orgName} ›
                </span>
              `).join('')}
            </div>
          </div>
        ` : ''}

        ${Array.isArray(contact.alumniInstitutions) && contact.alumniInstitutions.length > 0 ? `
          <div style="margin-top: 8px; font-size: 11px; color: var(--text-muted);">
            🎓 Alumni: <strong>${contact.alumniInstitutions.join(', ')}</strong>
          </div>
        ` : ''}
      </div>

      <!-- Company Overview -->
      ${contact.companyOverview ? `
        <div class="detail-section-block">
          <h3>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>
            Company Overview
          </h3>
          <p class="detail-text-p">${contact.companyOverview}</p>
        </div>
      ` : ''}

      <!-- Sales Icebreakers -->
      ${Array.isArray(contact.salesIcebreakers) && contact.salesIcebreakers.length > 0 ? `
        <div class="detail-section-block">
          <h3>
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
            Sales Conversation Starters
          </h3>
          <ul class="icebreaker-list">
            ${contact.salesIcebreakers.map(ice => `<li class="icebreaker-item">💬 ${ice}</li>`).join('')}
          </ul>
        </div>
      ` : ''}

      <!-- Action Items & Follow-ups -->
      <div class="detail-section-block">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <h3 style="margin-bottom: 0;">Follow-Up Action Items</h3>
          <span style="font-size: 11px; color: var(--text-muted);" id="contactActionCountLabel">
            ${(contact.actionItems || []).filter(a => !a.completed).length} Pending
          </span>
        </div>

        <div id="contactActionItemsList" style="display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px;"></div>

        <!-- Inline Add Action Item Form -->
        <div style="display: flex; gap: 8px; align-items: center; background: rgba(15, 23, 42, 0.6); padding: 8px; border-radius: var(--border-radius-sm); border: 1px solid var(--border-color);">
          <input type="text" id="inputNewActionTitle" placeholder="Add next action item..." class="form-control" style="height: 36px; font-size: 12px;">
          <select id="selectNewActionPriority" class="form-control" style="width: 90px; height: 36px; font-size: 11px;">
            <option value="high">High</option>
            <option value="medium" selected>Med</option>
            <option value="low">Low</option>
          </select>
          <button class="btn-primary" id="btnAddContactActionItem" style="width: 36px; height: 36px; padding: 0; flex: none;">+</button>
        </div>
      </div>

      <!-- Scanned Card Image Preview (if present) -->
      ${contact.cardImage ? `
        <div class="detail-section-block">
          <h3>Scanned Visiting Card</h3>
          <img src="${contact.cardImage}" style="width: 100%; border-radius: var(--border-radius-sm); border: 1px solid var(--border-color);">
        </div>
      ` : ''}
    `;

    this.renderContactDetailActionItems(contact);

    // Bind inline action item adder
    const btnAddAction = document.getElementById('btnAddContactActionItem');
    const inputActionTitle = document.getElementById('inputNewActionTitle');
    const selectPriority = document.getElementById('selectNewActionPriority');

    if (btnAddAction && inputActionTitle) {
      btnAddAction.addEventListener('click', () => {
        const text = inputActionTitle.value.trim();
        if (!text) return;

        window.storageService.addActionItem(contact.id, {
          title: text,
          priority: selectPriority ? selectPriority.value : 'medium',
          dueDate: new Date(Date.now() + 3 * 86400000).toISOString().split('T')[0]
        });

        inputActionTitle.value = '';
        const updatedContact = window.storageService.getContactById(contact.id);
        this.renderContactDetailActionItems(updatedContact);
        this.renderTasks();
        this.renderContacts();
        this.showToast('Follow-up task added', 'success');
      });
    }

    this.openModal('modalContactDetail');
  }

  renderContactDetailActionItems(contact) {
    const listEl = document.getElementById('contactActionItemsList');
    if (!listEl) return;
    listEl.innerHTML = '';

    const items = contact.actionItems || [];
    if (items.length === 0) {
      listEl.innerHTML = '<div style="font-size: 12px; color: var(--text-muted); padding: 4px 0;">No pending action items for this contact.</div>';
      return;
    }

    items.forEach(action => {
      const row = document.createElement('div');
      row.className = `task-card-row ${action.completed ? 'completed' : ''}`;
      row.innerHTML = `
        <div class="task-checkbox-custom" data-action-id="${action.id}">
          ${action.completed ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>' : ''}
        </div>
        <div class="task-content-block">
          <div class="task-title">${action.title}</div>
          <div class="task-due-priority">
            <span class="priority-tag priority-${action.priority || 'medium'}">${action.priority || 'medium'}</span>
            <span style="color: var(--text-muted);">Due: ${action.dueDate || 'Soon'}</span>
          </div>
        </div>
        <button class="icon-btn" style="width: 26px; height: 26px; border: none; font-size: 12px; color: var(--text-subtle);" onclick="window.app.deleteContactAction('${contact.id}', '${action.id}')">✕</button>
      `;

      row.querySelector('.task-checkbox-custom').addEventListener('click', () => {
        window.storageService.toggleActionItem(contact.id, action.id);
        const updated = window.storageService.getContactById(contact.id);
        this.renderContactDetailActionItems(updated);
        this.renderTasks();
        this.renderContacts();
      });

      listEl.appendChild(row);
    });
  }

  deleteContactAction(contactId, actionId) {
    window.storageService.deleteActionItem(contactId, actionId);
    const updated = window.storageService.getContactById(contactId);
    this.renderContactDetailActionItems(updated);
    this.renderTasks();
    this.renderContacts();
    this.showToast('Action item removed', 'info');
  }

  handleDeleteCurrentContact() {
    if (!this.activeContactDetailId) return;
    const contact = window.storageService.getContactById(this.activeContactDetailId);
    if (!contact) return;

    if (confirm(`Are you sure you want to delete lead "${contact.name}"?`)) {
      window.storageService.deleteContact(this.activeContactDetailId);
      this.closeModal('modalContactDetail');
      this.showToast('Lead deleted', 'info');
      this.renderContacts();
      this.renderOrganizations();
      this.renderEvents();
      this.renderTasks();
    }
  }

  downloadSingleVCard(contactId) {
    const contact = window.storageService.getContactById(contactId);
    if (contact) {
      window.exportService.downloadVCard(contact);
      this.showToast(`Saved ${contact.name} to Android .vcf`, 'success');
    }
  }

  // --- EVENTS MANAGEMENT VIEW ---
  renderEvents() {
    const events = window.storageService.getEvents();
    const contacts = window.storageService.getContacts();
    const activeEventId = window.storageService.getActiveEventId();

    const activeEvent = window.storageService.getEventById(activeEventId) || events[0];
    if (activeEvent) {
      if (this.bannerActiveEventName) this.bannerActiveEventName.textContent = activeEvent.name;
      if (this.bannerActiveEventLocation) this.bannerActiveEventLocation.textContent = activeEvent.location || 'Trade Summit';
    }

    if (!this.eventsList) return;
    this.eventsList.innerHTML = '';

    if (events.length === 0) {
      this.eventsList.innerHTML = `
        <div class="empty-state-card">
          <div class="empty-state-icon">📅</div>
          <div class="empty-state-title">No Events Created Yet</div>
          <div class="empty-state-desc">Create your first sales conference or trade summit to organize leads by booth.</div>
        </div>
      `;
      return;
    }

    events.forEach(evt => {
      const eventContacts = contacts.filter(c => c.eventId === evt.id);
      const isActive = evt.id === activeEventId;

      let eventTasksCount = 0;
      eventContacts.forEach(c => {
        eventTasksCount += (c.actionItems || []).filter(a => !a.completed).length;
      });

      const card = document.createElement('div');
      card.className = `event-card ${isActive ? 'is-active' : ''}`;
      card.innerHTML = `
        <div class="event-card-header">
          <div class="event-name" style="color: ${evt.badgeColor || '#fff'};">${evt.name}</div>
        </div>

        <div class="event-meta-info">
          <span>📅 ${evt.date || 'TBD'}</span>
          <span>📍 ${evt.location || 'Exhibition Venue'}</span>
          ${evt.description ? `<p style="font-size: 11.5px; color: #cbd5e1; margin-top: 4px; line-height: 1.4;">${evt.description}</p>` : ''}
        </div>

        <div class="event-stats-bar">
          <div class="event-stat-item">
            <span class="num">${eventContacts.length}</span>
            <span class="label">Leads Captured</span>
          </div>
          <div class="event-stat-item">
            <span class="num" style="color: #f59e0b;">${eventTasksCount}</span>
            <span class="label">Follow-ups</span>
          </div>
        </div>

        <div class="event-card-footer">
          ${!isActive ? `
            <button class="btn-primary" style="height: 34px; font-size: 11.5px;" onclick="window.app.setActiveEvent('${evt.id}')">
              Set Active Target
            </button>
          ` : `
            <span style="font-size: 11.5px; font-weight: 700; color: #10b981;">✓ Target Selected</span>
          `}

          <div style="display: flex; gap: 6px;">
            <button class="btn-secondary" style="height: 34px; padding: 0 10px; font-size: 11px;" title="Export Leads to CSV" onclick="window.app.exportEventCSV('${evt.id}')">
              CSV
            </button>
            <button class="btn-secondary" style="height: 34px; padding: 0 10px; font-size: 11px;" title="View Leads in Contacts Tab" onclick="window.app.viewEventLeads('${evt.id}')">
              View Leads
            </button>
          </div>
        </div>
      `;

      this.eventsList.appendChild(card);
    });
  }

  setActiveEvent(eventId) {
    window.storageService.setActiveEventId(eventId);
    this.updateHeaderActiveEvent();
    this.renderEvents();
    this.showToast('Active event set for incoming card scans', 'success');
  }

  updateHeaderActiveEvent() {
    const activeEventId = window.storageService.getActiveEventId();
    const event = window.storageService.getEventById(activeEventId);
    const name = event ? event.name : 'All Events';

    if (this.headerActiveEventName) this.headerActiveEventName.textContent = name;
    if (this.scannerActiveEventLabel) this.scannerActiveEventLabel.textContent = `Tagging scans to: ${name}`;
  }

  viewEventLeads(eventId) {
    this.currentEventFilter = eventId;
    this.switchView('viewContacts');
  }

  exportEventCSV(eventId) {
    const evt = window.storageService.getEventById(eventId);
    const all = window.storageService.getContacts();
    const eventContacts = all.filter(c => c.eventId === eventId);

    if (eventContacts.length === 0) {
      alert('No contacts recorded yet for this event.');
      return;
    }

    const safeName = (evt ? evt.name : 'Event').replace(/[^a-z0-9]/gi, '_');
    window.exportService.exportContactsToCSV(eventContacts, `${safeName}_Leads.csv`);
    this.showToast(`Exported ${eventContacts.length} leads for ${evt ? evt.name : 'Event'}`, 'success');
  }

  handleCreateNewEvent() {
    const nameEl = document.getElementById('newEventName');
    const dateEl = document.getElementById('newEventDate');
    const locEl = document.getElementById('newEventLocation');
    const descEl = document.getElementById('newEventDescription');
    const colorEl = document.getElementById('newEventColor');

    const name = nameEl ? nameEl.value.trim() : '';
    const date = dateEl ? dateEl.value : '';

    if (!name || !date) {
      alert('Please fill in both Event Name and Date.');
      return;
    }

    const newEvt = window.storageService.addEvent({
      name,
      date,
      location: locEl ? locEl.value.trim() : '',
      description: descEl ? descEl.value.trim() : '',
      badgeColor: colorEl ? colorEl.value : '#6366f1'
    });

    window.storageService.setActiveEventId(newEvt.id);
    this.closeModal('modalAddEvent');
    this.showToast(`Event "${name}" created and set as active target!`, 'success');

    if (nameEl) nameEl.value = '';
    if (locEl) locEl.value = '';
    if (descEl) descEl.value = '';

    this.renderEvents();
    this.renderEventFilterPills();
    this.updateHeaderActiveEvent();
  }

  // --- TASKS / ACTION ITEMS VIEW ---
  renderTasks() {
    const allItems = window.storageService.getAllActionItems();
    const pendingItems = allItems.filter(t => !t.completed);
    const completedItems = allItems.filter(t => t.completed);
    const highItems = allItems.filter(t => t.priority === 'high' && !t.completed);

    if (this.tasksCountLabel) {
      this.tasksCountLabel.textContent = `${pendingItems.length} Action Items Pending`;
    }

    let itemsToDisplay = allItems;
    if (this.currentTaskFilter === 'pending') {
      itemsToDisplay = pendingItems;
    } else if (this.currentTaskFilter === 'high') {
      itemsToDisplay = highItems;
    } else if (this.currentTaskFilter === 'completed') {
      itemsToDisplay = completedItems;
    }

    if (!this.tasksFeed) return;
    this.tasksFeed.innerHTML = '';

    if (itemsToDisplay.length === 0) {
      this.tasksFeed.innerHTML = `
        <div class="empty-state-card">
          <div class="empty-state-icon">✅</div>
          <div class="empty-state-title">All Caught Up</div>
          <div class="empty-state-desc">No tasks matching this filter. Action items extracted from business cards will appear here.</div>
        </div>
      `;
      return;
    }

    itemsToDisplay.forEach(item => {
      const row = document.createElement('div');
      row.className = `task-card-row ${item.completed ? 'completed' : ''}`;
      row.innerHTML = `
        <div class="task-checkbox-custom" data-contact-id="${item.contactId}" data-action-id="${item.id}">
          ${item.completed ? '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="3"><polyline points="20 6 9 17 4 12"></polyline></svg>' : ''}
        </div>
        <div class="task-content-block">
          <div class="task-title">${item.title}</div>
          <div class="task-contact-meta" onclick="window.app.openContactDetailModal('${item.contactId}')">
            👤 ${item.contactName} (${item.contactCompany || 'Lead'}) ›
          </div>
          <div class="task-due-priority">
            <span class="priority-tag priority-${item.priority || 'medium'}">${item.priority || 'medium'}</span>
            <span style="color: var(--text-muted);">📅 Due: ${item.dueDate || 'Soon'}</span>
          </div>
        </div>
        <div style="display: flex; gap: 4px; align-items: center;">
          ${item.contactPhone ? `
            <a href="tel:${item.contactPhone}" class="action-icon-pill" title="Call">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z"></path></svg>
            </a>
          ` : ''}
        </div>
      `;

      row.querySelector('.task-checkbox-custom').addEventListener('click', () => {
        window.storageService.toggleActionItem(item.contactId, item.id);
        this.renderTasks();
        this.renderContacts();
      });

      this.tasksFeed.appendChild(row);
    });
  }

  // --- SETTINGS, GEMINI MODELS & CROSS-TEAM SYNC ---
  loadSettings() {
    if (this.settingGeminiApiKey) {
      this.settingGeminiApiKey.value = window.storageService.getGeminiApiKey();
    }
    const teamConfig = window.storageService.getTeamSyncConfig();
    if (this.inputTeamWorkspaceCode && teamConfig.workspaceCode) {
      this.inputTeamWorkspaceCode.value = teamConfig.workspaceCode;
    }
    this.populateModelsDropdown();
  }

  populateModelsDropdown() {
    if (!this.settingModelSelect) return;
    const models = window.storageService.getAvailableModels();
    const currentModel = window.storageService.getSelectedModel();

    if (models.length > 0) {
      this.settingModelSelect.innerHTML = '';
      models.forEach(m => {
        const opt = document.createElement('option');
        opt.value = m.id;
        opt.textContent = `${m.displayName || m.name} (${m.id})`;
        if (m.id === currentModel) opt.selected = true;
        this.settingModelSelect.appendChild(opt);
      });
    }
  }

  async handleFetchModels() {
    const key = (this.settingGeminiApiKey ? this.settingGeminiApiKey.value : '').trim();
    if (!key) {
      alert('Please enter your Google Gemini API key first to discover supported models.');
      return;
    }

    this.showToast('Querying Google Gemini API for supported vision models...', 'info');
    try {
      const fetched = await window.geminiService.fetchAvailableModels(key);
      this.populateModelsDropdown();
      this.showToast(`✓ Retrieved ${fetched.length} active Gemini models!`, 'success');
    } catch (err) {
      this.showToast(`Model detection note: ${err.message}`, 'error');
    }
  }

  async handleTestGeminiModel() {
    const key = (this.settingGeminiApiKey ? this.settingGeminiApiKey.value : '').trim();
    if (!key) {
      alert('Please enter your Google Gemini API key first.');
      return;
    }

    const modelId = (this.settingModelSelect ? this.settingModelSelect.value : 'gemini-1.5-flash').replace(/^models\//, '');
    this.showToast(`Verifying model [${modelId}] connection...`, 'info');
    try {
      const testUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelId}?key=${encodeURIComponent(key)}`;
      const res = await fetch(testUrl);
      if (res.ok) {
        const details = await res.json();
        this.showToast(`✓ Model "${details.displayName || modelId}" verified and active!`, 'success');
      } else {
        const errorData = await res.json().catch(() => ({}));
        const errText = errorData.error ? errorData.error.message : `HTTP ${res.status}`;
        this.showToast(`Model Notice: ${errText}. Select gemini-2.0-flash or gemini-1.5-flash.`, 'error');
      }
    } catch (e) {
      this.showToast('Connection test: ' + e.message, 'error');
    }
  }

  handleSyncTeamWorkspace() {
    const code = (this.inputTeamWorkspaceCode ? this.inputTeamWorkspaceCode.value : '').trim().toUpperCase();
    if (!code) {
      alert('Please enter a Team Workspace Code (e.g. ALPHA-SALES-2026).');
      return;
    }

    window.storageService.saveTeamSyncConfig({ workspaceCode: code });
    const rep = window.storageService.getSalesRepProfile();
    rep.teamCode = code;
    window.storageService.saveSalesRepProfile(rep);

    this.showToast(`✓ Connected to Workspace "${code}". All scans stamped with ${rep.employeeId || 'REP'}.`, 'success');
  }

  handleExportTeamBundle() {
    const bundle = window.storageService.exportFullDatabaseBundle();
    const rep = window.storageService.getSalesRepProfile();
    const filename = `LeadPulse_TeamBundle_${(rep.teamCode || 'WORKSPACE').replace(/[^a-z0-9]/gi, '_')}_${new Date().toISOString().split('T')[0]}.json`;

    const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
    window.exportService._triggerDownload(blob, filename);
    this.showToast(`Team Bundle exported (${bundle.contacts.length} leads). Share this with your colleagues.`, 'success');
  }

  async handleImportTeamBundle(event) {
    const file = event.target.files[0];
    if (!file) return;

    try {
      const text = await file.text();
      const bundle = JSON.parse(text);

      const result = window.storageService.importAndMergeDatabaseBundle(bundle);
      if (result.success) {
        this.showToast(`✓ Merged team bundle: +${result.addedCount} new leads, ${result.updatedCount} updated!`, 'success');
        this.renderContacts();
        this.renderOrganizations();
        this.renderEvents();
        this.renderTasks();
      } else {
        this.showToast('Merge error: ' + result.error, 'error');
      }
    } catch (e) {
      this.showToast('Failed to parse bundle: ' + e.message, 'error');
    } finally {
      if (this.fileImportTeamBundle) this.fileImportTeamBundle.value = '';
    }
  }

  handleExportFullBackup() {
    const backup = window.storageService.exportFullDatabaseBundle();
    const filename = `LeadPulse_Backup_${new Date().toISOString().split('T')[0]}.json`;
    const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
    window.exportService._triggerDownload(blob, filename);
    this.showToast('Offline CRM backup downloaded', 'success');
  }

  async handleRestoreBackup(event) {
    const file = event.target.files[0];
    if (!file) return;

    try {
      const text = await file.text();
      const bundle = JSON.parse(text);
      const result = window.storageService.importAndMergeDatabaseBundle(bundle);
      if (result.success) {
        this.showToast(`✓ Backup restored: ${result.totalContacts} total contacts in CRM`, 'success');
        this.renderContacts();
        this.renderOrganizations();
        this.renderEvents();
        this.renderTasks();
      } else {
        this.showToast('Restore error: ' + result.error, 'error');
      }
    } catch (e) {
      this.showToast('Invalid backup file: ' + e.message, 'error');
    } finally {
      if (this.fileRestoreBackup) this.fileRestoreBackup.value = '';
    }
  }

  handleClearAllData() {
    const confirmation = prompt('Type "RESET" to confirm clearing all contacts from local storage (clean slate):');
    if (confirmation && confirmation.trim().toUpperCase() === 'RESET') {
      window.storageService.clearAllData();
      this.renderContacts();
      this.renderOrganizations();
      this.renderEvents();
      this.renderTasks();
      this.showToast('Database reset to clean state (0 contacts).', 'info');
    }
  }

  // --- HELPERS ---
  populateEventSelect(selectId, selectedId) {
    const el = document.getElementById(selectId);
    if (!el) return;
    el.innerHTML = '';
    const events = window.storageService.getEvents();
    events.forEach(evt => {
      const opt = document.createElement('option');
      opt.value = evt.id;
      opt.textContent = evt.name;
      if (evt.id === selectedId) opt.selected = true;
      el.appendChild(opt);
    });
  }

  openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.add('active');
  }

  closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('active');
  }

  showToast(message, type = 'info') {
    if (!this.toastContainer) return;
    const toast = document.createElement('div');
    toast.className = `toast-msg ${type}`;
    toast.textContent = message;

    this.toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(-10px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 3200);
  }
}

// Global App Controller Singleton
window.app = new AppController();
