/**
 * LeadPulse Camera & Card Capture Service
 * Controls Android camera stream, photo capture, file upload, and sample card generator.
 */

class CameraService {
  constructor() {
    this.stream = null;
    this.videoElement = null;
    this.canvasElement = document.createElement('canvas');
    this.isTorchOn = false;
  }

  /**
   * Initializes camera video stream
   */
  async startCamera(videoElement) {
    this.videoElement = videoElement;

    const constraints = {
      video: {
        facingMode: { ideal: 'environment' }, // Rear camera on Android phones
        width: { ideal: 1920, min: 1280 },
        height: { ideal: 1080, min: 720 }
      },
      audio: false
    };

    try {
      this.stream = await navigator.mediaDevices.getUserMedia(constraints);
      this.videoElement.srcObject = this.stream;
      await this.videoElement.play();
      return { success: true };
    } catch (err) {
      console.warn('Camera access could not be established:', err);
      return { success: false, error: err.name || 'CameraUnavailable' };
    }
  }

  /**
   * Stops active camera stream
   */
  stopCamera() {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop());
      this.stream = null;
    }
    if (this.videoElement) {
      this.videoElement.srcObject = null;
    }
    this.isTorchOn = false;
  }

  /**
   * Captures still frame from live video feed
   */
  captureFrame() {
    if (!this.videoElement || !this.stream) return null;

    const video = this.videoElement;
    const canvas = this.canvasElement;
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;

    const ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    return canvas.toDataURL('image/jpeg', 0.92);
  }

  /**
   * Reads an uploaded image file as Base64
   */
  readFileAsBase64(file) {
    return new Promise((resolve, reject) => {
      if (!file) return reject(new Error('No file selected'));
      if (!file.type.startsWith('image/')) return reject(new Error('Selected file is not an image'));

      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target.result);
      reader.onerror = (e) => reject(e);
      reader.readAsDataURL(file);
    });
  }

  /**
   * Generates a high-resolution realistic business card image on canvas
   * Allows the sales team or user to test scanning immediately without needing physical cards!
   */
  generateSampleCardImage(type = 'tech') {
    const canvas = document.createElement('canvas');
    canvas.width = 1050; // standard 3.5" x 2" at 300dpi ratio
    canvas.height = 600;
    const ctx = canvas.getContext('2d');

    const cardStyles = {
      tech: {
        bg: '#0f172a',
        accent: '#6366f1',
        secondaryAccent: '#38bdf8',
        name: 'Siddharth V. Rao',
        title: 'Chief Technology Officer & Co-Founder',
        company: 'VELOCE DATA PLATFORMS',
        tagline: 'High-Throughput Financial Stream Infrastructure',
        phone: '+91 99804 77219',
        email: 'siddharth@velocedata.com',
        web: 'www.velocedata.com',
        addr: 'One BKC, G-Block, Bandra Kurla Complex, Mumbai 400051',
        theme: 'dark'
      },
      health: {
        bg: '#ffffff',
        accent: '#0d9488',
        secondaryAccent: '#14b8a6',
        name: 'Dr. Ananya Deshmukh',
        title: 'Chief Product Officer & VP AI',
        company: 'AURAHEALTH INTELLIGENCE',
        tagline: 'Next-Gen Clinical Decision Support Systems',
        phone: '+91 91673 89210',
        email: 'ananya.d@aurahealth.ai',
        web: 'www.aurahealth.ai',
        addr: '100ft Road, Indiranagar, Bengaluru, KA 560038',
        theme: 'light'
      },
      energy: {
        bg: '#064e3b',
        accent: '#10b981',
        secondaryAccent: '#a7f3d0',
        name: 'Priya Sundaram',
        title: 'Head of Strategic Alliances',
        company: 'GREENGRID ENERGY CORP',
        tagline: 'Accelerating Clean Grid Decarbonization',
        phone: '+91 98450 62118',
        email: 'priya.sundaram@greengrid.in',
        web: 'www.greengrid.in',
        addr: 'Tidel Park, Rajiv Gandhi Salai, Chennai, TN 600113',
        theme: 'dark'
      }
    };

    const c = cardStyles[type] || cardStyles.tech;

    // Background
    ctx.fillStyle = c.bg;
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    // Decorative geometric accents
    ctx.fillStyle = c.accent;
    ctx.beginPath();
    ctx.arc(canvas.width - 60, 60, 180, 0, Math.PI * 2);
    ctx.globalAlpha = 0.15;
    ctx.fill();
    ctx.globalAlpha = 1.0;

    // Card accent border strip
    ctx.fillStyle = c.accent;
    ctx.fillRect(0, 0, 14, canvas.height);

    // Modern brand logo mark
    ctx.save();
    ctx.fillStyle = c.accent;
    ctx.beginPath();
    ctx.roundRect(80, 75, 48, 48, 12);
    ctx.fill();
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 24px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText('VP', 104, 99);
    ctx.restore();

    // Company Name & Tagline
    ctx.fillStyle = c.theme === 'dark' ? '#f8fafc' : '#0f172a';
    ctx.font = '800 28px Inter, sans-serif';
    ctx.textAlign = 'left';
    ctx.fillText(c.company, 145, 95);

    ctx.fillStyle = c.secondaryAccent;
    ctx.font = '500 14px Inter, sans-serif';
    ctx.fillText(c.tagline.toUpperCase(), 145, 118);

    // Divider
    ctx.strokeStyle = c.theme === 'dark' ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.08)';
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    ctx.moveTo(80, 160);
    ctx.lineTo(canvas.width - 80, 160);
    ctx.stroke();

    // Name & Title
    ctx.fillStyle = c.theme === 'dark' ? '#ffffff' : '#0f172a';
    ctx.font = 'bold 36px Inter, sans-serif';
    ctx.fillText(c.name, 80, 240);

    ctx.fillStyle = c.accent;
    ctx.font = '600 20px Inter, sans-serif';
    ctx.fillText(c.title, 80, 275);

    // Contact details block
    const isDark = c.theme === 'dark';
    ctx.fillStyle = isDark ? '#cbd5e1' : '#334155';
    ctx.font = '500 18px Inter, sans-serif';

    const startY = 360;
    const lineHeight = 38;

    // Phone
    ctx.fillText(`📞  ${c.phone}`, 80, startY);
    // Email
    ctx.fillText(`✉️  ${c.email}`, 80, startY + lineHeight);
    // Website
    ctx.fillText(`🌐  ${c.web}`, 80, startY + (lineHeight * 2));
    // Address
    ctx.fillText(`📍  ${c.addr}`, 80, startY + (lineHeight * 3));

    // QR code mockup placeholder on right
    const qrSize = 130;
    const qrX = canvas.width - qrSize - 90;
    const qrY = canvas.height - qrSize - 90;

    ctx.fillStyle = isDark ? '#1e293b' : '#f1f5f9';
    ctx.beginPath();
    ctx.roundRect(qrX, qrY, qrSize, qrSize, 10);
    ctx.fill();

    ctx.fillStyle = isDark ? '#64748b' : '#94a3b8';
    ctx.font = '12px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('SCAN VCARD', qrX + (qrSize / 2), qrY + (qrSize / 2) + 5);

    return canvas.toDataURL('image/jpeg', 0.95);
  }
}

window.cameraService = new CameraService();
