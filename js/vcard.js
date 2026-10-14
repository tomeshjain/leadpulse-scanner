/**
 * LeadPulse vCard & CSV Export Utility
 * Facilitates direct 1-tap save into Android Google Contacts (.vcf)
 * and CSV/Excel exports for CRM pipelines.
 */

class ExportService {
  /**
   * Generates standard vCard 3.0 string for native Android Contacts import
   */
  generateVCard(contact) {
    const lines = [
      'BEGIN:VCARD',
      'VERSION:3.0',
      `FN:${contact.name || ''}`,
      `N:${(contact.name || '').split(' ').reverse().join(';')};;;`,
      `ORG:${contact.company || ''}`,
      `TITLE:${contact.title || ''}`,
      `TEL;TYPE=CELL,VOICE:${contact.phone || ''}`,
      `EMAIL;TYPE=WORK,INTERNET:${contact.email || ''}`,
      `URL:${contact.website || contact.linkedinUrl || ''}`,
      `ADR;TYPE=WORK:;;${contact.address || ''};;;;`,
      `NOTE:Saved via LeadPulse AI Scanner. LinkedIn: ${contact.linkedinUrl || 'N/A'}. Event ID: ${contact.eventId || 'N/A'}. Notes: ${contact.notes || ''}`,
      'END:VCARD'
    ];

    return lines.join('\r\n');
  }

  /**
   * Triggers download of single .vcf file (opens directly in Android Contacts app)
   */
  downloadVCard(contact) {
    const vcardContent = this.generateVCard(contact);
    const blob = new Blob([vcardContent], { type: 'text/vcard;charset=utf-8;' });
    const filename = `${(contact.name || 'contact').replace(/[^a-z0-9]/gi, '_')}.vcf`;

    this._triggerDownload(blob, filename);
  }

  /**
   * Exports an array of contacts to CSV format
   */
  exportContactsToCSV(contacts, filename = 'LeadPulse_Contacts_Export.csv') {
    if (!contacts || contacts.length === 0) {
      alert('No contacts available to export.');
      return;
    }

    const headers = [
      'ID',
      'Full Name',
      'Honorific',
      'Designation / Title',
      'Company',
      'Industry',
      'Email',
      'Phone',
      'WhatsApp',
      'Website',
      'Address',
      'Event Name',
      'Past Organizations / Employers',
      'Alumni Institutions',
      'LinkedIn Profile',
      'Past Work Summary',
      'Lead Score',
      'Decision Status',
      'Deal Budget',
      'Buying Timeline',
      'Scanned By Rep Name',
      'Scanned By Employee ID',
      'Pending Action Items Count',
      'Created Date'
    ];

    const rows = contacts.map(c => {
      const event = window.storageService ? window.storageService.getEventById(c.eventId) : null;
      const eventName = event ? event.name : 'Unassigned';
      const pendingTasks = (c.actionItems || []).filter(a => !a.completed).length;
      const pastOrgs = (c.pastCompaniesList || []).join('; ');
      const alumni = (c.alumniInstitutions || []).join('; ');
      const rep = c.scannedBy || {};

      return [
        c.id,
        c.name,
        c.honorific || '',
        c.title || '',
        c.company || '',
        c.industry || '',
        c.email || '',
        c.phone || '',
        c.whatsapp || '',
        c.website || '',
        c.address || '',
        eventName,
        pastOrgs,
        alumni,
        c.linkedinUrl || '',
        c.pastWorkSummary || '',
        c.leadScore || 'Hot',
        c.decisionStatus || 'Decision Maker',
        c.dealBudget || '',
        c.buyingTimeline || '',
        rep.name || '',
        rep.employeeId || '',
        pendingTasks,
        c.createdAt || ''
      ].map(val => `"${String(val || '').replace(/"/g, '""')}"`);
    });

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map(r => r.join(','))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    this._triggerDownload(blob, filename);
  }

  _triggerDownload(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.setAttribute('download', filename);
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}

window.exportService = new ExportService();
