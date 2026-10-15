package com.wisprflow.keyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Dedicated Contact Card Scanner Activity.
 * - Opens camera directly on launch (with subtle gallery option).
 * - Extracts contact details via Gemini Vision AI into structured, editable text.
 * - Allows adding voice notes to the contact card.
 * - Provides options to "Save to Phone Contacts & Send", "Send Card & Text", and "Save Contact Only".
 * - Returns directly to the screen where the keyboard was opened (no jump to Settings).
 */
public class CardScannerActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 101;
    private static final int REQUEST_GALLERY = 102;
    private static final int REQUEST_VOICE_NOTE = 103;
    private static final int PERMISSION_CAMERA_REQ = 201;
    private static final int PERMISSION_AUDIO_REQ = 202;

    private KeyboardPreferences prefs;
    private ImageView imgCardPreview;
    private LinearLayout cameraPlaceholderLayout;
    private LinearLayout progressContainer;
    private TextView tvStatusMessage;
    private EditText etExtractedText;
    private LinearLayout actionButtonsLayout;
    private Button btnRetake;
    private Button btnAddVoiceNote;

    private Uri capturedPhotoUri = null;
    private File capturedPhotoFile = null;
    private Bitmap currentCardBitmap = null;
    private boolean isInitialCameraTriggered = false;

    public static class ContactInfo {
        public String name = "";
        public String title = "";
        public String company = "";
        public String phone = "";
        public String email = "";
        public String website = "";
        public String address = "";
        public String notes = "";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new KeyboardPreferences(this);

        // Build Clean Modern Dark UI Programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#080b11"));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 1. Header with Gallery and Close Buttons
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📇 Contact Card Scanner");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        header.addView(tvTitle);

        // Subtle Gallery option in header
        Button btnGalleryHeader = new Button(this);
        btnGalleryHeader.setText("🖼️ Gallery");
        btnGalleryHeader.setTextSize(11);
        btnGalleryHeader.setTextColor(Color.parseColor("#a5b4fc"));
        btnGalleryHeader.setBackground(createBoxDrawable("#1e1b4b", "#4338ca", 8));
        btnGalleryHeader.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        LinearLayout.LayoutParams galParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32));
        galParams.setMargins(0, 0, dpToPx(8), 0);
        btnGalleryHeader.setLayoutParams(galParams);
        btnGalleryHeader.setOnClickListener(v -> launchGallery());
        header.addView(btnGalleryHeader);

        // Close Button
        Button btnClose = new Button(this);
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(Color.parseColor("#94a3b8"));
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> closeScanner());
        header.addView(btnClose);

        root.addView(header);

        // 2. Camera Placeholder (shown only before photo is taken / if cancelled)
        cameraPlaceholderLayout = new LinearLayout(this);
        cameraPlaceholderLayout.setOrientation(LinearLayout.VERTICAL);
        cameraPlaceholderLayout.setGravity(Gravity.CENTER);
        cameraPlaceholderLayout.setBackground(createBoxDrawable("#0f1624", "#1e293b", 12));
        cameraPlaceholderLayout.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24));
        LinearLayout.LayoutParams placeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(150));
        placeParams.setMargins(0, dpToPx(12), 0, dpToPx(12));
        cameraPlaceholderLayout.setLayoutParams(placeParams);

        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("📷 Ready to Scan Contact Card");
        tvPrompt.setTextColor(Color.WHITE);
        tvPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPrompt.setTypeface(null, Typeface.BOLD);
        cameraPlaceholderLayout.addView(tvPrompt);

        Button btnSnap = createActionButton("📸 Open Camera", "#4338ca", "#6366f1");
        LinearLayout.LayoutParams snapParams = new LinearLayout.LayoutParams(
                dpToPx(160), dpToPx(40));
        snapParams.setMargins(0, dpToPx(12), 0, 0);
        btnSnap.setLayoutParams(snapParams);
        btnSnap.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        cameraPlaceholderLayout.addView(btnSnap);

        root.addView(cameraPlaceholderLayout);

        // 3. Card Image Preview View
        imgCardPreview = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(190));
        imgParams.setMargins(0, dpToPx(10), 0, dpToPx(8));
        imgCardPreview.setLayoutParams(imgParams);
        imgCardPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgCardPreview.setBackground(createBoxDrawable("#0d131f", "#1e293b", 12));
        imgCardPreview.setVisibility(View.GONE);
        root.addView(imgCardPreview);

        // Retake Button (below preview)
        btnRetake = createActionButton("📸 Retake Photo", "#1e293b", "#334155");
        LinearLayout.LayoutParams retakeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(34));
        retakeParams.gravity = Gravity.CENTER_HORIZONTAL;
        retakeParams.setMargins(0, 0, 0, dpToPx(8));
        btnRetake.setLayoutParams(retakeParams);
        btnRetake.setVisibility(View.GONE);
        btnRetake.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        root.addView(btnRetake);

        // 4. Progress / Loading Indicator
        progressContainer = new LinearLayout(this);
        progressContainer.setOrientation(LinearLayout.HORIZONTAL);
        progressContainer.setGravity(Gravity.CENTER_VERTICAL);
        progressContainer.setBackground(createBoxDrawable("#1e1b4b", "#4338ca", 8));
        progressContainer.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        progressContainer.setVisibility(View.GONE);

        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
        spinParams.setMargins(0, 0, dpToPx(12), 0);
        spinner.setLayoutParams(spinParams);
        progressContainer.addView(spinner);

        tvStatusMessage = new TextView(this);
        tvStatusMessage.setText("✨ Scanning contact card with Gemini AI...");
        tvStatusMessage.setTextColor(Color.parseColor("#e0e7ff"));
        tvStatusMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        progressContainer.addView(tvStatusMessage);

        LinearLayout.LayoutParams progParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progParams.setMargins(0, dpToPx(4), 0, dpToPx(8));
        progressContainer.setLayoutParams(progParams);
        root.addView(progressContainer);

        // 5. Extracted Text Section Header with "Add Voice Note" Button
        LinearLayout textHeaderRow = new LinearLayout(this);
        textHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        textHeaderRow.setGravity(Gravity.CENTER_VERTICAL);
        textHeaderRow.setPadding(0, dpToPx(8), 0, dpToPx(4));

        TextView tvEditLabel = new TextView(this);
        tvEditLabel.setText("Extracted Contact Information:");
        tvEditLabel.setTextColor(Color.parseColor("#cbd5e1"));
        tvEditLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvEditLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lblParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvEditLabel.setLayoutParams(lblParams);
        textHeaderRow.addView(tvEditLabel);

        btnAddVoiceNote = new Button(this);
        btnAddVoiceNote.setText("🎙️ Add Voice Note");
        btnAddVoiceNote.setTextSize(11);
        btnAddVoiceNote.setTextColor(Color.WHITE);
        btnAddVoiceNote.setBackground(createBoxDrawable("#065f46", "#10b981", 8));
        btnAddVoiceNote.setPadding(dpToPx(10), 0, dpToPx(10), 0);
        LinearLayout.LayoutParams vnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32));
        btnAddVoiceNote.setLayoutParams(vnParams);
        btnAddVoiceNote.setOnClickListener(v -> startVoiceNoteRecording());
        textHeaderRow.addView(btnAddVoiceNote);

        root.addView(textHeaderRow);

        // Extracted Text Result Editor
        etExtractedText = new EditText(this);
        etExtractedText.setTextColor(Color.WHITE);
        etExtractedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        etExtractedText.setLineSpacing(dpToPx(2), 1.15f);
        etExtractedText.setBackground(createBoxDrawable("#0f1624", "#1e293b", 8));
        etExtractedText.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        etExtractedText.setGravity(Gravity.TOP | Gravity.START);
        etExtractedText.setMinLines(6);
        etExtractedText.setHint("Take photo of card. Extracted information will appear here.");
        etExtractedText.setHintTextColor(Color.parseColor("#475569"));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(0, dpToPx(4), 0, dpToPx(14));
        etExtractedText.setLayoutParams(etParams);
        root.addView(etExtractedText);

        // 6. Action Buttons:
        // A. 💾 Save to Contacts & Send Card
        // B. 📤 Send Card & Text
        // C. 💾 Save Contact Only
        actionButtonsLayout = new LinearLayout(this);
        actionButtonsLayout.setOrientation(LinearLayout.VERTICAL);
        actionButtonsLayout.setVisibility(View.GONE);

        etExtractedText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.toString().trim().length() > 0) {
                    actionButtonsLayout.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Option 1: Save Contact & Send
        Button btnSaveAndSend = createActionButton("💾 Save to Phone Contacts & Send", "#15803d", "#22c55e");
        btnSaveAndSend.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46)));
        btnSaveAndSend.setOnClickListener(v -> handleSaveAndSend(true));
        actionButtonsLayout.addView(btnSaveAndSend);

        // Option 2: Send Card & Text
        Button btnSendOnly = createActionButton("📤 Send Card & Text", "#4338ca", "#6366f1");
        LinearLayout.LayoutParams pSendOnly = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44));
        pSendOnly.setMargins(0, dpToPx(8), 0, 0);
        btnSendOnly.setLayoutParams(pSendOnly);
        btnSendOnly.setOnClickListener(v -> handleSaveAndSend(false));
        actionButtonsLayout.addView(btnSendOnly);

        // Option 3: Save Contact Only
        Button btnSaveOnly = createActionButton("💾 Save Contact to Phone Only", "#1e293b", "#334155");
        LinearLayout.LayoutParams pSaveOnly = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40));
        pSaveOnly.setMargins(0, dpToPx(8), 0, 0);
        btnSaveOnly.setLayoutParams(pSaveOnly);
        btnSaveOnly.setOnClickListener(v -> handleSaveOnly());
        actionButtonsLayout.addView(btnSaveOnly);

        root.addView(actionButtonsLayout);

        scrollView.addView(root);
        setContentView(scrollView);

        // Auto launch camera immediately on open so user doesn't have to pick
        if (savedInstanceState == null && !isInitialCameraTriggered) {
            isInitialCameraTriggered = true;
            checkCameraPermissionAndLaunch();
        }
    }

    private void checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA_REQ);
        } else {
            launchCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CAMERA_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                Toast.makeText(this, "Camera permission needed to snap business cards", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == PERMISSION_AUDIO_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceNoteRecording();
            } else {
                Toast.makeText(this, "Microphone permission needed for voice notes", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void launchCamera() {
        try {
            File imagesDir = new File(getCacheDir(), "images");
            if (!imagesDir.exists()) imagesDir.mkdirs();

            capturedPhotoFile = new File(imagesDir, "card_" + System.currentTimeMillis() + ".jpg");
            capturedPhotoUri = FileProvider.getUriForFile(this, "com.wisprflow.keyboard.fileprovider", capturedPhotoFile);

            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, capturedPhotoUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(cameraIntent, REQUEST_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening camera: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGallery() {
        try {
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, REQUEST_GALLERY);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(Intent.createChooser(intent, "Select Card Image"), REQUEST_GALLERY);
        }
    }

    private void startVoiceNoteRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO_REQ);
            return;
        }

        try {
            Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            speechIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak voice note to attach with contact card...");
            startActivityForResult(speechIntent, REQUEST_VOICE_NOTE);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition unavailable: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Voice Note Result
        if (requestCode == REQUEST_VOICE_NOTE) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (matches != null && !matches.isEmpty()) {
                    String spoken = matches.get(0).trim();
                    String current = etExtractedText.getText().toString().trim();
                    String updated = current + (current.isEmpty() ? "" : "\n") + "🎙️ Voice Note: " + spoken;
                    etExtractedText.setText(updated);
                    actionButtonsLayout.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "🎙️ Voice note added to card!", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }

        if (resultCode != RESULT_OK) return;

        Bitmap bitmap = null;
        try {
            if (requestCode == REQUEST_CAMERA) {
                if (capturedPhotoFile != null && capturedPhotoFile.exists()) {
                    bitmap = BitmapFactory.decodeFile(capturedPhotoFile.getAbsolutePath());
                } else if (data != null && data.getExtras() != null) {
                    bitmap = (Bitmap) data.getExtras().get("data");
                }
            } else if (requestCode == REQUEST_GALLERY && data != null && data.getData() != null) {
                Uri selectedUri = data.getData();
                InputStream is = getContentResolver().openInputStream(selectedUri);
                bitmap = BitmapFactory.decodeStream(is);
                if (is != null) is.close();

                File imagesDir = new File(getCacheDir(), "images");
                if (!imagesDir.exists()) imagesDir.mkdirs();
                capturedPhotoFile = new File(imagesDir, "card_gallery_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fos = new FileOutputStream(capturedPhotoFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.close();
                capturedPhotoUri = FileProvider.getUriForFile(this, "com.wisprflow.keyboard.fileprovider", capturedPhotoFile);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        if (bitmap != null) {
            currentCardBitmap = bitmap;
            cameraPlaceholderLayout.setVisibility(View.GONE);
            imgCardPreview.setImageBitmap(bitmap);
            imgCardPreview.setVisibility(View.VISIBLE);
            btnRetake.setVisibility(View.VISIBLE);
            runAiCardExtraction(bitmap);
        }
    }

    private void runAiCardExtraction(Bitmap bitmap) {
        progressContainer.setVisibility(View.VISIBLE);
        tvStatusMessage.setText("⚡ Extracting Contact Info with Gemini AI...");
        actionButtonsLayout.setVisibility(View.GONE);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        byte[] jpegBytes = baos.toByteArray();

        String apiKey = prefs.getApiKey();
        String model = prefs.getSelectedModel();

        GeminiFlowClient.scanContactCard(jpegBytes, apiKey, model, new GeminiFlowClient.CardScanCallback() {
            @Override
            public void onSuccess(String formattedContactCard, long latencyMs) {
                progressContainer.setVisibility(View.GONE);
                etExtractedText.setText(formattedContactCard);
                actionButtonsLayout.setVisibility(View.VISIBLE);
                Toast.makeText(CardScannerActivity.this, "Card Scanned (" + latencyMs + "ms)", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                progressContainer.setVisibility(View.GONE);
                actionButtonsLayout.setVisibility(View.VISIBLE);
                String fallback = "📇 CONTACT CARD\n" +
                        "👤 Name: [Scanned Contact]\n" +
                        "💼 Title: [Title]\n" +
                        "🏢 Company: [Company]\n" +
                        "📞 Phone: \n" +
                        "✉️ Email: \n" +
                        "🌐 Website: \n" +
                        "📍 Address: \n" +
                        "📝 Notes: (Configure Gemini API Key in Settings for AI OCR)";
                etExtractedText.setText(fallback);
                Toast.makeText(CardScannerActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private ContactInfo parseContactInfo(String text) {
        ContactInfo c = new ContactInfo();
        if (text == null) return c;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String l = line.trim();
            if (l.contains("Name:")) {
                c.name = cleanField(l, "Name:");
            } else if (l.contains("Title:")) {
                c.title = cleanField(l, "Title:");
            } else if (l.contains("Company:")) {
                c.company = cleanField(l, "Company:");
            } else if (l.contains("Phone:")) {
                c.phone = cleanField(l, "Phone:");
            } else if (l.contains("Email:")) {
                c.email = cleanField(l, "Email:");
            } else if (l.contains("Website:")) {
                c.website = cleanField(l, "Website:");
            } else if (l.contains("Address:")) {
                c.address = cleanField(l, "Address:");
            } else if (l.contains("Notes:") || l.contains("Voice Note:")) {
                c.notes += (c.notes.isEmpty() ? "" : "\n") + l;
            }
        }
        return c;
    }

    private String cleanField(String line, String prefix) {
        int idx = line.indexOf(prefix);
        if (idx >= 0) {
            String val = line.substring(idx + prefix.length()).trim();
            if (val.equalsIgnoreCase("N/A") || val.equalsIgnoreCase("None") || val.equalsIgnoreCase("null")) {
                return "";
            }
            if (val.startsWith("[") && val.endsWith("]")) {
                return "";
            }
            return val;
        }
        return "";
    }

    private void handleSaveAndSend(boolean saveContactFirst) {
        String cardText = etExtractedText.getText().toString().trim();
        if (cardText.isEmpty()) {
            Toast.makeText(this, "No contact card text to send", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Prepare Keyboard Transfer Payload & Clipboard
        ContactCardTransfer.setPendingCard(cardText, capturedPhotoUri, "image/jpeg");

        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip;
                if (capturedPhotoUri != null) {
                    clip = ClipData.newUri(getContentResolver(), "Scanned Contact Card", capturedPhotoUri);
                    clip.addItem(new ClipData.Item(cardText));
                } else {
                    clip = ClipData.newPlainText("Scanned Contact Card", cardText);
                }
                cm.setPrimaryClip(clip);
            }
        } catch (Exception ignored) {}

        // 2. If saving contact to phone:
        if (saveContactFirst) {
            ContactInfo info = parseContactInfo(cardText);
            try {
                Intent insertIntent = new Intent(Intent.ACTION_INSERT);
                insertIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
                if (!info.name.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.NAME, info.name);
                if (!info.phone.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.PHONE, info.phone);
                if (!info.email.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.EMAIL, info.email);
                if (!info.company.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.COMPANY, info.company);
                if (!info.title.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, info.title);
                if (!info.address.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.POSTAL, info.address);
                if (!info.notes.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.NOTES, info.notes);
                insertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(insertIntent);
            } catch (Exception ignored) {}
        }

        Toast.makeText(this, "📇 Sending Contact Card & Image...", Toast.LENGTH_SHORT).show();

        // 3. Close scanner and return directly to the caller screen
        closeScanner();
    }

    private void handleSaveOnly() {
        String cardText = etExtractedText.getText().toString().trim();
        if (cardText.isEmpty()) {
            Toast.makeText(this, "No contact details to save", Toast.LENGTH_SHORT).show();
            return;
        }

        ContactInfo info = parseContactInfo(cardText);
        try {
            Intent insertIntent = new Intent(Intent.ACTION_INSERT);
            insertIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
            if (!info.name.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.NAME, info.name);
            if (!info.phone.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.PHONE, info.phone);
            if (!info.email.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.EMAIL, info.email);
            if (!info.company.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.COMPANY, info.company);
            if (!info.title.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, info.title);
            if (!info.address.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.POSTAL, info.address);
            if (!info.notes.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.NOTES, info.notes);
            insertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(insertIntent);
            Toast.makeText(this, "💾 Saving contact to phone...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error saving contact: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        closeScanner();
    }

    private void closeScanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        closeScanner();
    }

    private Button createActionButton(String label, String bgColor, String strokeColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setAllCaps(false);
        btn.setBackground(createBoxDrawable(bgColor, strokeColor, 10));
        return btn;
    }

    private GradientDrawable createBoxDrawable(String bgColor, String strokeColor, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(Color.parseColor(bgColor));
        gd.setStroke(dpToPx(1), Color.parseColor(strokeColor));
        gd.setCornerRadius(dpToPx(radiusDp));
        return gd;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
