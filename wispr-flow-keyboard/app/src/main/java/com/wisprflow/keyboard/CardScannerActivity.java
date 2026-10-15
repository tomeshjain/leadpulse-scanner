package com.wisprflow.keyboard;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentProviderOperation;
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
import android.util.Log;
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

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

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
    private static final int PERMISSION_WRITE_CONTACTS_REQ = 203;

    private KeyboardPreferences prefs;
    private ImageView imgCardPreview;
    private LinearLayout cameraPlaceholderLayout;
    private LinearLayout progressContainer;
    private TextView tvStatusMessage;
    private TextView tvEngineIndicator;
    private EditText etExtractedText;
    private LinearLayout actionButtonsLayout;
    private Button btnRetake;
    private Button btnAddVoiceNote;

    private Uri capturedPhotoUri = null;
    private File capturedPhotoFile = null;
    private Bitmap currentCardBitmap = null;
    private boolean isInitialCameraTriggered = false;
    private ContactInfo pendingSaveContactInfo = null;
    private boolean pendingCloseAfterSave = false;

    public static class ContactInfo {
        public String name = "";
        public String designation = "";
        public String organization = "";
        public String email = "";
        public String phone = "";
        public String city = "";
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new KeyboardPreferences(this);
        KeyboardTheme activeTheme = KeyboardTheme.resolveTheme(this, prefs.getThemeId());
        boolean isLight = activeTheme.isLight;

        // Build Adaptive Clean UI Programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(isLight ? Color.parseColor("#f8fafc") : Color.parseColor("#080b11"));
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
        tvTitle.setText("📇 LeadPulse Smart Scanner");
        tvTitle.setTextColor(isLight ? Color.parseColor("#0f172a") : Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        header.addView(tvTitle);

        // Subtle Gallery option in header
        Button btnGalleryHeader = new Button(this);
        btnGalleryHeader.setText("🖼️ Gallery");
        btnGalleryHeader.setTextSize(11);
        btnGalleryHeader.setTextColor(isLight ? Color.parseColor("#1d4ed8") : Color.parseColor("#a5b4fc"));
        btnGalleryHeader.setBackground(createBoxDrawable(isLight ? "#e2e8f0" : "#1e1b4b", isLight ? "#cbd5e1" : "#4338ca", 8));
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
        btnClose.setTextColor(isLight ? Color.parseColor("#64748b") : Color.parseColor("#94a3b8"));
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> closeScanner());
        header.addView(btnClose);

        root.addView(header);

        // 2. Camera Placeholder (shown only before photo is taken / if cancelled)
        cameraPlaceholderLayout = new LinearLayout(this);
        cameraPlaceholderLayout.setOrientation(LinearLayout.VERTICAL);
        cameraPlaceholderLayout.setGravity(Gravity.CENTER);
        cameraPlaceholderLayout.setBackground(createBoxDrawable(isLight ? "#ffffff" : "#0f1624", isLight ? "#cbd5e1" : "#1e293b", 12));
        cameraPlaceholderLayout.setPadding(dpToPx(16), dpToPx(24), dpToPx(16), dpToPx(24));
        LinearLayout.LayoutParams placeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(150));
        placeParams.setMargins(0, dpToPx(12), 0, dpToPx(12));
        cameraPlaceholderLayout.setLayoutParams(placeParams);

        TextView tvPrompt = new TextView(this);
        tvPrompt.setText("📷 Ready to Scan Contact Card");
        tvPrompt.setTextColor(isLight ? Color.parseColor("#0f172a") : Color.WHITE);
        tvPrompt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvPrompt.setTypeface(null, Typeface.BOLD);
        cameraPlaceholderLayout.addView(tvPrompt);

        Button btnSnap = createActionButton("📸 Open Camera", isLight ? "#2563eb" : "#4338ca", isLight ? "#1d4ed8" : "#6366f1");
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
        imgCardPreview.setBackground(createBoxDrawable(isLight ? "#ffffff" : "#0d131f", isLight ? "#cbd5e1" : "#1e293b", 12));
        imgCardPreview.setVisibility(View.GONE);
        root.addView(imgCardPreview);

        // Retake Button (below preview)
        btnRetake = createActionButton("📸 Retake Photo", isLight ? "#f1f5f9" : "#1e293b", isLight ? "#cbd5e1" : "#334155");
        btnRetake.setTextColor(isLight ? Color.parseColor("#0f172a") : Color.WHITE);
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
        progressContainer.setBackground(createBoxDrawable(isLight ? "#eff6ff" : "#1e1b4b", isLight ? "#3b82f6" : "#4338ca", 8));
        progressContainer.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
        progressContainer.setVisibility(View.GONE);

        ProgressBar spinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinParams = new LinearLayout.LayoutParams(dpToPx(24), dpToPx(24));
        spinParams.setMargins(0, 0, dpToPx(12), 0);
        spinner.setLayoutParams(spinParams);
        progressContainer.addView(spinner);

        tvStatusMessage = new TextView(this);
        tvStatusMessage.setText("✨ Scanning contact card with Gemini AI...");
        tvStatusMessage.setTextColor(isLight ? Color.parseColor("#1e40af") : Color.parseColor("#e0e7ff"));
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
        tvEditLabel.setTextColor(isLight ? Color.parseColor("#334155") : Color.parseColor("#cbd5e1"));
        tvEditLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvEditLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lblParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvEditLabel.setLayoutParams(lblParams);
        textHeaderRow.addView(tvEditLabel);

        btnAddVoiceNote = new Button(this);
        btnAddVoiceNote.setText("🎙️ Add Voice Note");
        btnAddVoiceNote.setTextSize(11);
        btnAddVoiceNote.setTextColor(Color.WHITE);
        btnAddVoiceNote.setBackground(createBoxDrawable("#059669", "#10b981", 8));
        btnAddVoiceNote.setPadding(dpToPx(10), 0, dpToPx(10), 0);
        LinearLayout.LayoutParams vnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dpToPx(32));
        btnAddVoiceNote.setLayoutParams(vnParams);
        btnAddVoiceNote.setOnClickListener(v -> startVoiceNoteRecording());
        textHeaderRow.addView(btnAddVoiceNote);

        root.addView(textHeaderRow);

        // Transparent Engine & Model Indicator Badge
        tvEngineIndicator = new TextView(this);
        tvEngineIndicator.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvEngineIndicator.setTypeface(null, Typeface.BOLD);
        tvEngineIndicator.setPadding(dpToPx(10), dpToPx(5), dpToPx(10), dpToPx(5));
        tvEngineIndicator.setVisibility(View.GONE);
        LinearLayout.LayoutParams indParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        indParams.setMargins(0, 0, 0, dpToPx(6));
        tvEngineIndicator.setLayoutParams(indParams);
        root.addView(tvEngineIndicator);

        // Extracted Text Result Editor
        etExtractedText = new EditText(this);
        etExtractedText.setTextColor(isLight ? Color.parseColor("#0f172a") : Color.WHITE);
        etExtractedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        etExtractedText.setLineSpacing(dpToPx(2), 1.15f);
        etExtractedText.setBackground(createBoxDrawable(isLight ? "#ffffff" : "#0f1624", isLight ? "#cbd5e1" : "#1e293b", 8));
        etExtractedText.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        etExtractedText.setGravity(Gravity.TOP | Gravity.START);
        etExtractedText.setMinLines(6);
        etExtractedText.setHint("Take photo of card. Extracted information will appear here.");
        etExtractedText.setHintTextColor(isLight ? Color.parseColor("#94a3b8") : Color.parseColor("#475569"));
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
        Button btnSaveAndSend = createActionButton("💾 Save to Phone Contacts & Send", "#16a34a", "#22c55e");
        btnSaveAndSend.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(46)));
        btnSaveAndSend.setOnClickListener(v -> handleSaveAndSend(true));
        actionButtonsLayout.addView(btnSaveAndSend);

        // Option 2: Send Card & Text
        Button btnSendOnly = createActionButton("📤 Send Card & Text", isLight ? "#2563eb" : "#4338ca", isLight ? "#3b82f6" : "#6366f1");
        LinearLayout.LayoutParams pSendOnly = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(44));
        pSendOnly.setMargins(0, dpToPx(8), 0, 0);
        btnSendOnly.setLayoutParams(pSendOnly);
        btnSendOnly.setOnClickListener(v -> handleSaveAndSend(false));
        actionButtonsLayout.addView(btnSendOnly);

        // Option 3: Save Contact Only
        Button btnSaveOnly = createActionButton("💾 Save Contact to Phone Only", isLight ? "#f1f5f9" : "#1e293b", isLight ? "#cbd5e1" : "#334155");
        btnSaveOnly.setTextColor(isLight ? Color.parseColor("#1e293b") : Color.WHITE);
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
        } else if (requestCode == PERMISSION_WRITE_CONTACTS_REQ) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingSaveContactInfo != null) {
                    executeDirectContactSave(pendingSaveContactInfo);
                    pendingSaveContactInfo = null;
                }
            } else {
                Toast.makeText(this, "Contacts permission required to save contacts automatically", Toast.LENGTH_SHORT).show();
            }
            if (pendingCloseAfterSave) {
                closeScanner();
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

        GeminiFlowClient.scanContactCard(jpegBytes, apiKey, model, new GeminiFlowClient.CardScanResultCallback() {
            @Override
            public void onSuccess(String formattedContactCard, long latencyMs, String modelUsed, boolean isFallback) {
                progressContainer.setVisibility(View.GONE);
                etExtractedText.setText(formattedContactCard);
                actionButtonsLayout.setVisibility(View.VISIBLE);
                showEngineIndicator(modelUsed, isFallback, latencyMs);
            }

            @Override
            public void onError(String errorMessage) {
                // If Gemini Multimodal Vision API is busy/offline, fall back to on-device ML Kit OCR!
                runLocalOcrFallback(bitmap);
            }
        });
    }

    private void runLocalOcrFallback(Bitmap bitmap) {
        if (bitmap == null) {
            fallbackToRuleBasedExtraction("No image");
            return;
        }

        tvStatusMessage.setText("📷 AI busy/offline - Reading card with on-device local OCR...");
        progressContainer.setVisibility(View.VISIBLE);

        try {
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String rawOcrText = visionText.getText();
                        if (rawOcrText != null && !rawOcrText.trim().isEmpty()) {
                            tvStatusMessage.setText("⚡ Structuring with Gemini AI (Unlimited Fast Tier)...");
                            String apiKey = prefs.getApiKey();
                            String model = prefs.getSelectedModel();

                            GeminiFlowClient.formatExtractedCardText(rawOcrText, apiKey, model, new GeminiFlowClient.CardScanResultCallback() {
                                @Override
                                public void onSuccess(String formattedCard, long latencyMs, String modelUsed, boolean isFallback) {
                                    progressContainer.setVisibility(View.GONE);
                                    etExtractedText.setText(formattedCard);
                                    actionButtonsLayout.setVisibility(View.VISIBLE);
                                    showEngineIndicator(modelUsed, isFallback, latencyMs);
                                }

                                @Override
                                public void onError(String error) {
                                    progressContainer.setVisibility(View.GONE);
                                    String localParsed = GeminiFlowClient.cleanCardOutput(GeminiFlowClient.parseRawOcrLocal(rawOcrText));
                                    etExtractedText.setText(localParsed);
                                    actionButtonsLayout.setVisibility(View.VISIBLE);
                                    showEngineIndicator("Offline Local OCR", false, 0);
                                }
                            });
                        } else {
                            fallbackToRuleBasedExtraction("No text found on card");
                        }
                    })
                    .addOnFailureListener(e -> {
                        fallbackToRuleBasedExtraction(e.getMessage());
                    });
        } catch (Exception e) {
            fallbackToRuleBasedExtraction(e.getMessage());
        }
    }

    private void fallbackToRuleBasedExtraction(String errorReason) {
        progressContainer.setVisibility(View.GONE);
        actionButtonsLayout.setVisibility(View.VISIBLE);
        String fallback = GeminiFlowClient.cleanCardOutput(GeminiFlowClient.parseRawOcrLocal(""));
        etExtractedText.setText(fallback);
        showEngineIndicator("Offline Local Rules", false, 0);
    }

    private void showEngineIndicator(String modelUsed, boolean isFallback, long latencyMs) {
        if (tvEngineIndicator == null) return;
        tvEngineIndicator.setVisibility(View.VISIBLE);
        if (modelUsed != null && (modelUsed.toLowerCase().contains("offline") || modelUsed.contains("Local"))) {
            tvEngineIndicator.setText("⚪ Offline: Local Engine (" + latencyMs + "ms)");
            tvEngineIndicator.setTextColor(Color.parseColor("#475569"));
            tvEngineIndicator.setBackground(createBoxDrawable("#f1f5f9", "#cbd5e1", 6));
        } else if (isFallback) {
            tvEngineIndicator.setText("🟡 Fallback AI: " + modelUsed + " (" + latencyMs + "ms)");
            tvEngineIndicator.setTextColor(Color.parseColor("#b45309"));
            tvEngineIndicator.setBackground(createBoxDrawable("#fef3c7", "#f59e0b", 6));
        } else {
            tvEngineIndicator.setText("🟢 Pure AI: " + modelUsed + " (" + latencyMs + "ms)");
            tvEngineIndicator.setTextColor(Color.parseColor("#15803d"));
            tvEngineIndicator.setBackground(createBoxDrawable("#dcfce7", "#22c55e", 6));
        }
    }

    private ContactInfo parseContactInfo(String text) {
        ContactInfo c = new ContactInfo();
        if (text == null) return c;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String l = line.trim();
            String lower = l.toLowerCase(Locale.US);
            if (lower.startsWith("name:")) {
                c.name = cleanField(l, "Name:");
            } else if (lower.startsWith("designation:") || lower.startsWith("title:") || lower.startsWith("job title:")) {
                c.designation = cleanField(l, l.substring(0, l.indexOf(':') + 1));
            } else if (lower.startsWith("organization:") || lower.startsWith("company:")) {
                c.organization = cleanField(l, l.substring(0, l.indexOf(':') + 1));
            } else if (lower.startsWith("phone number:") || lower.startsWith("phone:") || lower.startsWith("mobile:")) {
                c.phone = cleanField(l, l.substring(0, l.indexOf(':') + 1));
            } else if (lower.startsWith("email:")) {
                c.email = cleanField(l, "Email:");
            } else if (lower.startsWith("city:") || lower.startsWith("location:")) {
                c.city = cleanField(l, l.substring(0, l.indexOf(':') + 1));
            }
        }
        return c;
    }

    private String cleanField(String line, String prefix) {
        int idx = line.indexOf(prefix);
        if (idx >= 0) {
            String val = line.substring(idx + prefix.length()).trim();
            String lower = val.toLowerCase(Locale.US);
            if (lower.equals("n/a") || lower.equals("na") || lower.equals("none") ||
                    lower.equals("null") || lower.equals("not available") || lower.equals("unknown")) {
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

        // 1. Prepare Keyboard Transfer Payload & System Clipboard
        ContactCardTransfer.setPendingCard(cardText, capturedPhotoUri, "image/jpeg");

        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip;
                if (capturedPhotoUri != null) {
                    clip = ClipData.newUri(getContentResolver(), "LeadPulse Contact Card", capturedPhotoUri);
                    clip.addItem(new ClipData.Item(cardText));
                } else {
                    clip = ClipData.newPlainText("LeadPulse Contact Card", cardText);
                }
                cm.setPrimaryClip(clip);
            }
        } catch (Exception ignored) {}

        // 2. Automated Direct Contact Insertion (Zero Human Intervention)
        if (saveContactFirst) {
            ContactInfo info = parseContactInfo(cardText);
            saveContactDirectly(info, true);
        } else {
            Toast.makeText(this, "📇 Sending Contact Card & Image...", Toast.LENGTH_SHORT).show();
            closeScanner();
        }
    }

    private void handleSaveOnly() {
        String cardText = etExtractedText.getText().toString().trim();
        if (cardText.isEmpty()) {
            Toast.makeText(this, "No contact details to save", Toast.LENGTH_SHORT).show();
            return;
        }

        ContactInfo info = parseContactInfo(cardText);
        saveContactDirectly(info, true);
    }

    /**
     * Programmatically inserts contact details directly into Android's native contacts provider.
     * ZERO HUMAN INTERVENTION REQUIRED: No manual confirmation dialog or editor activity shown.
     */
    private void saveContactDirectly(ContactInfo info, boolean closeAfter) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingSaveContactInfo = info;
            pendingCloseAfterSave = closeAfter;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CONTACTS}, PERMISSION_WRITE_CONTACTS_REQ);
            return;
        }

        executeDirectContactSave(info);
        if (closeAfter) {
            closeScanner();
        }
    }

    private void executeDirectContactSave(ContactInfo info) {
        if (info == null) return;
        try {
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            int rawContactInsertIndex = ops.size();

            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build());

            // Name
            if (!info.name.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, info.name)
                        .build());
            }

            // Phone
            if (!info.phone.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, info.phone)
                        .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
                        .build());
            }

            // Email
            if (!info.email.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Email.DATA, info.email)
                        .withValue(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
                        .build());
            }

            // Organization & Designation
            if (!info.organization.isEmpty() || !info.designation.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Organization.COMPANY, info.organization)
                        .withValue(ContactsContract.CommonDataKinds.Organization.TITLE, info.designation)
                        .withValue(ContactsContract.CommonDataKinds.Organization.TYPE, ContactsContract.CommonDataKinds.Organization.TYPE_WORK)
                        .build());
            }

            // City
            if (!info.city.isEmpty()) {
                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.CITY, info.city)
                        .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK)
                        .build());
            }

            getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            String savedLabel = !info.name.isEmpty() ? info.name : "Contact";
            Toast.makeText(this, "✓ " + savedLabel + " automatically saved to contacts!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("CardScanner", "Direct contact insertion fallback", e);
            // Fallback to intent if unexpected permission issue occurs
            try {
                Intent insertIntent = new Intent(Intent.ACTION_INSERT);
                insertIntent.setType(ContactsContract.Contacts.CONTENT_TYPE);
                if (!info.name.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.NAME, info.name);
                if (!info.phone.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.PHONE, info.phone);
                if (!info.email.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.EMAIL, info.email);
                if (!info.organization.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.COMPANY, info.organization);
                if (!info.designation.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, info.designation);
                if (!info.city.isEmpty()) insertIntent.putExtra(ContactsContract.Intents.Insert.POSTAL, info.city);
                insertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(insertIntent);
            } catch (Exception ignored) {}
        }
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
