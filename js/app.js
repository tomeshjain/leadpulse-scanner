/**
 * LeadPulse - Main Application Controller
 * Handles UI interactions, Camera & AI Card Scanning, Contacts Directory,
 * Event Management, Follow-up Action Items, and vCard/CSV Export.
 */

class AppController {
  constructor() {
    this.currentView = 'viewScanner';
    this.currentEventFilter = 'all';
    this.currentTaskFilter = 'all';
    this.searchQuery = '';
    this.activeContactDetailId = null;
    this.tempScannedCard = null;

    // Wait for DOM
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => this.init());
    } else {
      this.init();
    }
  }

  async init() {
    console.log('LeadPulse App Initializing...');
    this.cacheDomElements();
    this.bindEvents();
    this.startClock();

    // Set initial active event if none
    if (!window.storageService.getActiveEventId()) {
      const events = window.storageService.getEvents();
      if (events.length > 0) {
        window.storageService.setActiveEventId(events[0].id);
      }
    }

    this.updateHeaderActiveEvent();
    this.renderContacts();
    this.renderEvents();
    this.renderTasks();
    this.renderEventFilterPills();
    this.loadSettings();

    // Start camera stream on initial launch
    await this.initCamera();

    // Register service worker for Android PWA if supported
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

    // Header
    this.btnActiveEventPill = document.getElementById('btnActiveEventPill');
    this.headerActiveEventName = document.getElementById('headerActiveEventName');
    this.btnOpenSettings = document.getElementById('btnOpenSettings');
    this.statusBarTime = document.getElementById('statusBarTime');

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
    this.demoChips = document.querySelectorAll('.demo-chip');

    // AI Overlay
    this.aiProcessingOverlay = document.getElementById('aiProcessingOverlay');
    this.aiProcessingTitle = document.getElementById('aiProcessingTitle');
    this.aiProcessingDesc = document.getElementById('aiProcessingDesc');

    // Contacts
    this.contactsFeed = document.getElementById('contactsFeed');
    this.inputSearchContacts = document.getElementById('inputSearchContacts');
    this.contactsEventFilterPills = document.getElementById('contactsEventFilterPills');
    this.statTotalContacts = document.getElementById('statTotalContacts');
    this.statActiveEvents = document.getElementById('statActiveEvents');
    this.statPendingTasks = document.getElementById('statPendingTasks');
    this.contactsCountLabel = document.getElementById('contactsCountLabel');
    this.btnExportAllCSV = document.getElementById('btnExportAllCSV');

    // Events
    this.eventsList = document.getElementById('eventsList');
    this.btnAddNewEvent = document.getElementById('btnAddNewEvent');
    this.bannerActiveEventName = document.getElementById('bannerActiveEventName');
    this.bannerActiveEventLocation = document.getElementById('bannerActiveEventLocation');

    // Tasks
    this.tasksList = document.getElementById('tasksList');
    this.taskFilterTabs = document.querySelectorAll('.task-filter-tab');
    this.taskCountAll = document.getElementById('taskCountAll');
    this.taskCountPending = document.getElementById('taskCountPending');
    this.taskCountCompleted = document.getElementById('taskCountCompleted');

    // Modals
    this.modalScanReview = document.getElementById('modalScanReview');
    this.btnCloseScanReview = document.getElementById('btnCloseScanReview');
    this.btnCancelReview = document.getElementById('btnCancelReview');
    this.btnSaveReviewContact = document.getElementById('btnSaveReviewContact');
    this.reviewCardImagePreview = document.getElementById('reviewCardImagePreview');
    this.reviewEventSelect = document.getElementById('reviewEventSelect');

    this.modalContactDetail = document.getElementById('modalContactDetail');
    this.btnCloseContactDetail = document.getElementById('btnCloseContactDetail');
    this.detailSheetContentBody = document.getElementById('detailSheetContentBody');
    this.btnDeleteContact = document.getElementById('btnDeleteContact');
    this.btnDownloadVCardSingle = document.getElementById('btnDownloadVCardSingle');

    this.modalAddEvent = document.getElementById('modalAddEvent');
    this.btnCloseAddEvent = document.getElementById('btnCloseAddEvent');
    this.btnCancelAddEvent = document.getElementById('btnCancelAddEvent');
    this.btnSubmitAddEvent = document.getElementById('btnSubmitAddEvent');

    // Settings
    this.settingGeminiApiKey = document.getElementById('settingGeminiApiKey');
    this.settingGeminiModel = document.getElementById('settingGeminiModel');
    this.btnFetchModels = document.getElementById('btnFetchModels');
    this.modelStatusNotice = document.getElementById('modelStatusNotice');
    this.btnToggleApiKeyVisibility = document.getElementById('btnToggleApiKeyVisibility');
    this.settingCustomPrompt = document.getElementById('settingCustomPrompt');
    this.btnSaveSettings = document.getElementById('btnSaveSettings');
    this.btnTestGemini = document.getElementById('btnTestGemini');
    this.btnExportAllJSON = document.getElementById('btnExportAllJSON');
    this.btnResetDefaultData = document.getElementById('btnResetDefaultData');

    // Review Modal Qualification Fields
    this.reviewDecisionStatus = document.getElementById('reviewDecisionStatus');
    this.reviewLeadScore = document.getElementById('reviewLeadScore');
    this.reviewBudget = document.getElementById('reviewBudget');
    this.reviewTimeline = document.getElementById('reviewTimeline');

    // Toast
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

    if (this.btnActiveEventPill) {
      this.btnActiveEventPill.addEventListener('click', () => {
        this.switchView('viewEvents');
      });
    }

    if (this.btnChangeActiveEvent) {
      this.btnChangeActiveEvent.addEventListener('click', () => {
        this.switchView('viewEvents');
      });
    }

    if (this.btnOpenSettings) {
      this.btnOpenSettings.addEventListener('click', () => {
        this.switchView('viewSettings');
      });
    }

    // Camera & Scanning
    if (this.btnCapture) {
      this.btnCapture.addEventListener('click', () => this.handleCaptureCard());
    }

    if (this.btnUploadGallery) {
      this.btnUploadGallery.addEventListener('click', () => {
        this.fileCardInput.click();
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

    // Demo card chips
    this.demoChips.forEach(chip => {
      chip.addEventListener('click', () => {
        const cardType = chip.getAttribute('data-card');
        this.handleScanDemoCard(cardType);
      });
    });

    // Contacts search & filter
    if (this.inputSearchContacts) {
      this.inputSearchContacts.addEventListener('input', (e) => {
        this.searchQuery = e.target.value.toLowerCase().trim();
        this.renderContacts();
      });
    }

    if (this.btnExportAllCSV) {
      this.btnExportAllCSV.addEventListener('click', () => {
        const contacts = window.storageService.getContacts();
        window.exportService.exportContactsToCSV(contacts, 'LeadPulse_All_Contacts.csv');
        this.showToast('CSV export downloaded successfully', 'success');
      });
    }

    // Tasks filters
    this.taskFilterTabs.forEach(tab => {
      tab.addEventListener('click', () => {
        this.taskFilterTabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
        this.currentTaskFilter = tab.getAttribute('data-filter');
        this.renderTasks();
      });
    });

    // Modals
    if (this.btnCloseScanReview) {
      this.btnCloseScanReview.addEventListener('click', () => this.closeModal('modalScanReview'));
    }
    if (this.btnCancelReview) {
      this.btnCancelReview.addEventListener('click', () => this.closeModal('modalScanReview'));
    }
    if (this.btnSaveReviewContact) {
      this.btnSaveReviewContact.addEventListener('click', () => this.handleSaveReviewContact());
    }

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

    // Event creation
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

    // Settings Handlers
    if (this.btnSaveSettings) {
      this.btnSaveSettings.addEventListener('click', () => this.handleSaveSettings());
    }
    if (this.btnTestGemini) {
      this.btnTestGemini.addEventListener('click', () => this.handleTestGeminiKey());
    }
    if (this.btnFetchModels) {
      this.btnFetchModels.addEventListener('click', () => this.handleFetchModels());
    }
    if (this.btnToggleApiKeyVisibility) {
      this.btnToggleApiKeyVisibility.addEventListener('click', () => {
        const isPw = this.settingGeminiApiKey.type === 'password';
        this.settingGeminiApiKey.type = isPw ? 'text' : 'password';
        this.btnToggleApiKeyVisibility.textContent = isPw ? '🔒' : '👁️';
      });
    }
    if (this.settingGeminiModel) {
      this.settingGeminiModel.addEventListener('change', () => {
        window.storageService.setSelectedModel(this.settingGeminiModel.value);
        if (this.modelStatusNotice) {
          this.modelStatusNotice.textContent = `⚡ Active Model: ${this.settingGeminiModel.value}`;
        }
        this.showToast(`Selected AI model: ${this.settingGeminiModel.value}`, 'info');
      });
    }
    if (this.btnExportAllJSON) {
      this.btnExportAllJSON.addEventListener('click', () => this.handleExportBackupJSON());
    }
    if (this.btnResetDefaultData) {
      this.btnResetDefaultData.addEventListener('click', () => this.handleResetData());
    }
  }

  // View Switcher
  switchView(viewId) {
    this.currentView = viewId;
    this.viewScreens.forEach(screen => {
      screen.classList.toggle('active', screen.id === viewId);
    });

    this.navBtns.forEach(btn => {
      btn.classList.toggle('active', btn.getAttribute('data-target') === viewId);
    });

    // Refresh view data when navigating
    if (viewId === 'viewContacts') {
      this.renderContacts();
      this.renderEventFilterPills();
    } else if (viewId === 'viewEvents') {
      this.renderEvents();
    } else if (viewId === 'viewTasks') {
      this.renderTasks();
    } else if (viewId === 'viewScanner') {
      this.updateHeaderActiveEvent();
    }
  }

  startClock() {
    const update = () => {
      const now = new Date();
      let hours = now.getHours();
      let minutes = now.getMinutes();
      minutes = minutes < 10 ? '0' + minutes : minutes;
      if (this.statusBarTime) {
        this.statusBarTime.textContent = `${hours}:${minutes}`;
      }
    };
    update();
    setInterval(update, 30000);
  }

  // --- CAMERA & CARD SCANNING ---
  async initCamera() {
    try {
      const res = await window.cameraService.startCamera(this.cameraVideo);
      if (!res.success) {
        console.log('Camera note: Live feed unavailable, fallback canvas ready for testing.');
      }
    } catch (e) {
      console.warn('Camera error:', e);
    }
  }

  async toggleTorch() {
    if (!window.cameraService.stream) {
      this.showToast('Torch only available during active camera stream', 'info');
      return;
    }
    const track = window.cameraService.stream.getVideoTracks()[0];
    if (track && track.getCapabilities && track.getCapabilities().torch) {
      const newState = !window.cameraService.isTorchOn;
      await track.applyConstraints({ advanced: [{ torch: newState }] });
      window.cameraService.isTorchOn = newState;
      this.showToast(newState ? 'Flashlight ON' : 'Flashlight OFF', 'info');
    } else {
      this.showToast('Torch hardware not supported on this device/browser', 'info');
    }
  }

  async handleCaptureCard() {
    let base64 = window.cameraService.captureFrame();

    // If camera wasn't streaming (e.g. Insecure HTTP Wi-Fi origin on mobile or desktop without webcam)
    if (!base64) {
      if (this.cameraPhotoInput && /Android|iPhone|iPad|Mobile/i.test(navigator.userAgent)) {
        this.cameraPhotoInput.click();
        return;
      }
      base64 = window.cameraService.generateSampleCardImage('tech');
      this.showToast('Camera frame simulated with high-res business card', 'info');
    }

    await this.processCardWithAI(base64);
  }

  async handleFileUpload(event) {
    const file = event.target.files[0];
    if (!file) return;

    try {
      this.showToast('Uploading card image...', 'info');
      const base64 = await window.cameraService.readFileAsBase64(file);
      await this.processCardWithAI(base64);
    } catch (err) {
      this.showToast('Failed to read image file: ' + err.message, 'error');
    } finally {
      if (this.fileCardInput) this.fileCardInput.value = '';
      if (this.cameraPhotoInput) this.cameraPhotoInput.value = '';
    }
  }

  async handleScanDemoCard(type) {
    const base64 = window.cameraService.generateSampleCardImage(type);
    await this.processCardWithAI(base64);
  }

  async processCardWithAI(imageBase64) {
    this.showAIProcessing(true, 'Reading Visiting Card...', 'Extracting name, title, company, contact details & logos');

    const descSteps = [
      'Extracting contact entities & phone numbers...',
      'Deep AI analysis: Querying LinkedIn profile & company profile...',
      'Synthesizing past work summary & sales conversation icebreakers...',
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
    }, 550);

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
      this.showToast('AI analysis failed: ' + err.message, 'error');
    }
  }

  showAIProcessing(show, title, desc) {
    if (!this.aiProcessingOverlay) return;
    if (show) {
      this.aiProcessingTitle.textContent = title || 'Analyzing Card...';
      this.aiProcessingDesc.textContent = desc || 'Gemini Vision AI is extracting contact details...';
      this.aiProcessingOverlay.classList.add('active');
    } else {
      this.aiProcessingOverlay.classList.remove('active');
    }
  }

  // --- MODAL: SCAN REVIEW ---
  openScanReviewModal(data) {
    this.populateEventSelect('reviewEventSelect', data.eventId || window.storageService.getActiveEventId());

    this.reviewCardImagePreview.src = data.cardImage || '';
    document.getElementById('reviewHonorific').value = data.honorific || '';
    document.getElementById('reviewName').value = data.name || '';
    document.getElementById('reviewTitle').value = data.title || '';
    document.getElementById('reviewCompany').value = data.company || '';
    document.getElementById('reviewIndustry').value = data.industry || '';
    document.getElementById('reviewPhone').value = data.phone || '';
    document.getElementById('reviewWhatsapp').value = data.whatsapp || data.phone || '';
    document.getElementById('reviewEmail').value = data.email || '';
    document.getElementById('reviewLinkedinUrl').value = data.linkedinUrl || '';
    document.getElementById('reviewPastWork').value = data.pastWorkSummary || '';
    document.getElementById('reviewCompanyOverview').value = data.companyOverview || '';
    document.getElementById('reviewNotes').value = data.notes || '';

    // Qualification fields
    if (this.reviewDecisionStatus) this.reviewDecisionStatus.value = data.decisionStatus || 'Decision Maker';
    if (this.reviewLeadScore) this.reviewLeadScore.value = data.leadScore || 'Hot';
    if (this.reviewBudget) this.reviewBudget.value = data.budget || '';
    if (this.reviewTimeline) this.reviewTimeline.value = data.timeline || 'Immediate (< 1 mo)';

    // Action items checkboxes
    const actionContainer = document.getElementById('reviewSuggestedActionItems');
    actionContainer.innerHTML = '';
    const suggested = data.suggestedActionItems || [
      'Send introduction email with product overview deck',
      'Connect on LinkedIn with personalized greeting',
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

    this.openModal('modalScanReview');
  }

  openManualContactModal() {
    this.tempScannedCard = {
      cardImage: '',
      suggestedActionItems: ['Send introduction email', 'Connect on LinkedIn']
    };
    this.openScanReviewModal(this.tempScannedCard);
  }

  handleSaveReviewContact() {
    const name = document.getElementById('reviewName').value.trim();
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

    const contactData = {
      name: name,
      honorific: document.getElementById('reviewHonorific').value.trim(),
      title: document.getElementById('reviewTitle').value.trim(),
      company: document.getElementById('reviewCompany').value.trim(),
      industry: document.getElementById('reviewIndustry').value.trim(),
      phone: document.getElementById('reviewPhone').value.trim(),
      whatsapp: document.getElementById('reviewWhatsapp').value.trim(),
      email: document.getElementById('reviewEmail').value.trim(),
      eventId: document.getElementById('reviewEventSelect').value,
      linkedinUrl: document.getElementById('reviewLinkedinUrl').value.trim(),
      pastWorkSummary: document.getElementById('reviewPastWork').value.trim(),
      companyOverview: document.getElementById('reviewCompanyOverview').value.trim(),
      notes: document.getElementById('reviewNotes').value.trim(),
      cardImage: this.tempScannedCard ? this.tempScannedCard.cardImage : '',
      actionItems: actionItems,
      decisionStatus: this.reviewDecisionStatus ? this.reviewDecisionStatus.value : 'Decision Maker',
      leadScore: this.reviewLeadScore ? this.reviewLeadScore.value : 'Hot',
      budget: this.reviewBudget ? this.reviewBudget.value.trim() : '',
      timeline: this.reviewTimeline ? this.reviewTimeline.value : 'Immediate (< 1 mo)'
    };

    const saved = window.storageService.addContact(contactData);
    this.closeModal('modalScanReview');
    this.showToast(`Contact ${saved.name} saved successfully!`, 'success');

    // Offer vCard download prompt
    setTimeout(() => {
      if (confirm(`Would you like to save ${saved.name} directly to your Android device contacts?`)) {
        window.exportService.downloadVCard(saved);
      }
    }, 300);

    this.renderContacts();
    this.renderEvents();
    this.renderTasks();
    this.updateHeaderActiveEvent();
  }

  // --- CONTACTS DIRECTORY VIEW ---
  renderContacts() {
    const allContacts = window.storageService.getContacts();
    const events = window.storageService.getEvents();

    // Stats
    const allTasks = window.storageService.getAllActionItems();
    const pendingTasks = allTasks.filter(t => !t.completed);

    if (this.statTotalContacts) this.statTotalContacts.textContent = allContacts.length;
    if (this.statActiveEvents) this.statActiveEvents.textContent = events.length;
    if (this.statPendingTasks) this.statPendingTasks.textContent = pendingTasks.length;

    // Filter contacts
    let filtered = allContacts;

    if (this.currentEventFilter !== 'all') {
      filtered = filtered.filter(c => c.eventId === this.currentEventFilter);
    }

    if (this.searchQuery) {
      filtered = filtered.filter(c => {
        const text = `${c.name} ${c.company} ${c.title} ${c.industry} ${c.email} ${c.notes}`.toLowerCase();
        return text.includes(this.searchQuery);
      });
    }

    if (this.contactsCountLabel) {
      this.contactsCountLabel.textContent = `Showing ${filtered.length} of ${allContacts.length} contacts`;
    }

    if (!this.contactsFeed) return;
    this.contactsFeed.innerHTML = '';

    if (filtered.length === 0) {
      this.contactsFeed.innerHTML = `
        <div style="text-align: center; padding: 40px 20px; color: var(--text-muted);">
          <div style="font-size: 36px; margin-bottom: 8px;">📇</div>
          <div style="font-size: 15px; font-weight: 700; color: #fff; margin-bottom: 4px;">No contacts found</div>
          <div style="font-size: 12px;">Try adjusting your search terms or scan a visiting card now.</div>
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

      const pendingTasksCount = (contact.actionItems || []).filter(a => !a.completed).length;

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
          </div>
        </div>

        ${contact.pastWorkSummary ? `
          <div class="contact-ai-preview">
            <strong>Past Work & Role:</strong> ${contact.pastWorkSummary}
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
            ${pendingTasksCount > 0 ? `
              <span class="contact-tasks-badge">
                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 11 12 14 22 4"></polyline></svg>
                ${pendingTasksCount} task${pendingTasksCount > 1 ? 's' : ''}
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

    // Bind click events
    this.contactsEventFilterPills.querySelectorAll('.filter-pill').forEach(pill => {
      pill.addEventListener('click', () => {
        this.contactsEventFilterPills.querySelectorAll('.filter-pill').forEach(p => p.classList.remove('active'));
        pill.classList.add('active');
        this.currentEventFilter = pill.getAttribute('data-event-id');
        this.renderContacts();
      });
    });
  }

  // --- MODAL: FULL CONTACT DETAILS ---
  openContactDetailModal(contactId) {
    this.activeContactDetailId = contactId;
    const contact = window.storageService.getContactById(contactId);
    if (!contact) return;

    const event = window.storageService.getEventById(contact.eventId);
    const eventName = event ? event.name : 'Unassigned';

    this.detailSheetContentBody.innerHTML = `
      <!-- Contact Hero Card -->
      <div class="contact-hero-card" style="background: ${contact.cardColorTheme || 'linear-gradient(135deg, #1e1b4b 0%, #312e81 100%)'};">
        <div class="contact-hero-details">
          <h2>${contact.honorific ? contact.honorific + ' ' : ''}${contact.name}</h2>
          <div class="hero-title-company">${contact.title || 'Executive'} at <strong>${contact.company || 'Enterprise'}</strong></div>
          <div style="font-size: 11px; margin-top: 6px; opacity: 0.8;">📍 Event: ${eventName} • ${contact.industry || 'Industry Leader'}</div>

          <!-- Executive Sales Badges -->
          <div style="display: flex; gap: 6px; flex-wrap: wrap; margin-top: 10px;">
            <span class="event-badge-tag" style="background: rgba(239, 68, 68, 0.3); border-color: #ef4444; color: #fca5a5; font-size: 11px;">
              ${contact.leadScore === 'Hot' ? '🔥 Hot Lead' : contact.leadScore === 'Warm' ? '⚡ Warm Lead' : '🌱 Nurture'}
            </span>
            <span class="event-badge-tag" style="background: rgba(16, 185, 129, 0.3); border-color: #10b981; color: #6ee7b7; font-size: 11px;">
              👔 ${contact.decisionStatus || 'Decision Maker'}
            </span>
            ${contact.budget ? `
              <span class="event-badge-tag" style="background: rgba(245, 158, 11, 0.3); border-color: #f59e0b; color: #fde68a; font-size: 11px;">
                💰 ${contact.budget}
              </span>
            ` : ''}
            ${contact.timeline ? `
              <span class="event-badge-tag" style="background: rgba(6, 182, 212, 0.3); border-color: #06b6d4; color: #a5f3fc; font-size: 11px;">
                ⏱️ ${contact.timeline}
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

      <!-- AI Enriched Intelligence: Past Work -->
      <div class="detail-section-block">
        <h3>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
          Past Work & Career History
        </h3>
        <p class="detail-text-p">${contact.pastWorkSummary || 'No career history recorded yet.'}</p>
      </div>

      <!-- AI Enriched Intelligence: Company Profile -->
      <div class="detail-section-block">
        <h3>
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>
          Company Overview
        </h3>
        <p class="detail-text-p">${contact.companyOverview || 'Enterprise solution provider.'}</p>
      </div>

      <!-- Sales Conversation Starters / Icebreakers -->
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

      <!-- Contact Action Items List & Inline Adder -->
      <div class="detail-section-block">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
          <h3 style="margin-bottom: 0;">Follow-Up Action Items</h3>
          <span style="font-size: 11px; color: var(--text-muted);" id="contactActionCountLabel">
            ${(contact.actionItems || []).filter(a => !a.completed).length} Pending
          </span>
        </div>

        <div id="contactActionItemsList" style="display: flex; flex-direction: column; gap: 8px; margin-bottom: 12px;">
          <!-- Dynamically populated below -->
        </div>

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
          priority: selectPriority.value,
          dueDate: new Date(Date.now() + 3 * 86400000).toISOString().split('T')[0]
        });

        inputActionTitle.value = '';
        const updatedContact = window.storageService.getContactById(contact.id);
        this.renderContactDetailActionItems(updatedContact);
        this.renderTasks();
        this.renderContacts();
        this.showToast('Action item added', 'success');
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

    if (confirm(`Are you sure you want to delete ${contact.name}?`)) {
      window.storageService.deleteContact(this.activeContactDetailId);
      this.closeModal('modalContactDetail');
      this.showToast('Contact deleted', 'info');
      this.renderContacts();
      this.renderEvents();
      this.renderTasks();
    }
  }

  downloadSingleVCard(contactId) {
    const contact = window.storageService.getContactById(contactId);
    if (contact) {
      window.exportService.downloadVCard(contact);
      this.showToast(`Saved ${contact.name} to Android vCard`, 'success');
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
    this.showToast('Active event updated for new card scans', 'success');
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
    const name = document.getElementById('newEventName').value.trim();
    const date = document.getElementById('newEventDate').value;
    const location = document.getElementById('newEventLocation').value.trim();
    const description = document.getElementById('newEventDescription').value.trim();
    const badgeColor = document.getElementById('newEventColor').value;

    if (!name || !date) {
      alert('Please fill in both Event Name and Date.');
      return;
    }

    const newEvt = window.storageService.addEvent({
      name,
      date,
      location,
      description,
      badgeColor
    });

    // Automatically set as active event
    window.storageService.setActiveEventId(newEvt.id);
    this.closeModal('modalAddEvent');
    this.showToast(`Event "${name}" created and set as active!`, 'success');

    // Reset inputs
    document.getElementById('newEventName').value = '';
    document.getElementById('newEventLocation').value = '';
    document.getElementById('newEventDescription').value = '';

    this.renderEvents();
    this.renderEventFilterPills();
    this.updateHeaderActiveEvent();
  }

  // --- TASKS / ACTION ITEMS VIEW ---
  renderTasks() {
    const allItems = window.storageService.getAllActionItems();
    const pendingItems = allItems.filter(t => !t.completed);
    const completedItems = allItems.filter(t => t.completed);

    if (this.taskCountAll) this.taskCountAll.textContent = allItems.length;
    if (this.taskCountPending) this.taskCountPending.textContent = pendingItems.length;
    if (this.taskCountCompleted) this.taskCountCompleted.textContent = completedItems.length;

    let itemsToDisplay = allItems;
    if (this.currentTaskFilter === 'pending') {
      itemsToDisplay = pendingItems;
    } else if (this.currentTaskFilter === 'completed') {
      itemsToDisplay = completedItems;
    }

    if (!this.tasksList) return;
    this.tasksList.innerHTML = '';

    if (itemsToDisplay.length === 0) {
      this.tasksList.innerHTML = `
        <div style="text-align: center; padding: 40px 20px; color: var(--text-muted);">
          <div style="font-size: 32px; margin-bottom: 8px;">✅</div>
          <div style="font-size: 15px; font-weight: 700; color: #fff; margin-bottom: 4px;">All caught up!</div>
          <div style="font-size: 12px;">No tasks matching this filter.</div>
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

      this.tasksList.appendChild(row);
    });
  }

  // --- SETTINGS VIEW ---
  loadSettings() {
    if (this.settingGeminiApiKey) {
      this.settingGeminiApiKey.value = window.storageService.getGeminiApiKey();
    }
    if (this.settingCustomPrompt) {
      this.settingCustomPrompt.value = window.storageService.getCustomPrompt();
    }
    this.populateModelsDropdown();
  }

  populateModelsDropdown() {
    if (!this.settingGeminiModel) return;
    const models = window.storageService.getAvailableModels();
    const currentModel = window.storageService.getSelectedModel();

    this.settingGeminiModel.innerHTML = '';
    models.forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.id;
      opt.textContent = m.name;
      if (m.id === currentModel) opt.selected = true;
      this.settingGeminiModel.appendChild(opt);
    });

    if (this.modelStatusNotice) {
      this.modelStatusNotice.textContent = `⚡ Active Model: ${currentModel}`;
    }
  }

  async handleFetchModels() {
    const key = (this.settingGeminiApiKey ? this.settingGeminiApiKey.value : '').trim();
    if (!key) {
      alert('Please enter your Google Gemini API key first to discover supported models.');
      return;
    }

    this.showToast('Connecting to Google Gemini API to query supported models...', 'info');
    try {
      const fetched = await window.geminiService.fetchAvailableModels(key);
      this.populateModelsDropdown();
      this.showToast(`✓ Retrieved ${fetched.length} active Gemini models!`, 'success');
    } catch (err) {
      this.showToast(`Fetch warning: ${err.message}`, 'error');
    }
  }

  handleSaveSettings() {
    const key = this.settingGeminiApiKey ? this.settingGeminiApiKey.value.trim() : '';
    window.storageService.setGeminiApiKey(key);
    if (this.settingGeminiModel) {
      window.storageService.setSelectedModel(this.settingGeminiModel.value);
    }
    if (this.settingCustomPrompt) {
      window.storageService.setCustomPrompt(this.settingCustomPrompt.value.trim());
    }
    this.showToast('AI Settings & Model preferences saved successfully!', 'success');
  }

  async handleTestGeminiKey() {
    const key = (this.settingGeminiApiKey ? this.settingGeminiApiKey.value : '').trim();
    if (!key) {
      alert('Built-in demo mode active! No key needed for intelligent testing.');
      return;
    }

    const modelId = (this.settingGeminiModel ? this.settingGeminiModel.value : 'gemini-1.5-flash').replace(/^models\//, '');
    this.showToast(`Verifying model [${modelId}] connection...`, 'info');
    try {
      const testUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelId}?key=${encodeURIComponent(key)}`;
      const res = await fetch(testUrl);
      if (res.ok) {
        const details = await res.json();
        this.showToast(`✓ Model "${details.displayName || modelId}" is verified & active!`, 'success');
      } else {
        const errorData = await res.json().catch(() => ({}));
        const errText = errorData.error ? errorData.error.message : `HTTP ${res.status}`;
        this.showToast(`Model Notice: ${errText}. Try clicking "Refresh Models" or select Gemini 2.0 Flash.`, 'error');
      }
    } catch (e) {
      this.showToast('Connection test error: ' + e.message, 'error');
    }
  }

  handleExportBackupJSON() {
    const backup = {
      contacts: window.storageService.getContacts(),
      events: window.storageService.getEvents(),
      settings: window.storageService.getSettings(),
      exportedAt: new Date().toISOString()
    };

    const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
    const filename = `LeadPulse_Backup_${new Date().toISOString().split('T')[0]}.json`;
    window.exportService._triggerDownload(blob, filename);
    this.showToast('JSON Backup downloaded', 'success');
  }

  handleResetData() {
    if (confirm('Reset all contacts and events to factory demo dataset?')) {
      localStorage.clear();
      location.reload();
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

// Instantiate global app controller
window.app = new AppController();
