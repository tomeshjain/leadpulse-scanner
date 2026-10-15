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
import android.os.Bundle;
import android.provider.MediaStore;
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

/**
 * Dedicated Contact Card Scanner Activity.
 * Captures or imports a business card image, extracts contact details using Gemini Vision AI,
 * and passes both the formatted text and image back to the keyboard or target messaging app.
 */
public class CardScannerActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA = 101;
    private static final int REQUEST_GALLERY = 102;
    private static final int PERMISSION_CAMERA_REQ = 201;

    private KeyboardPreferences prefs;
    private ImageView imgCardPreview;
    private LinearLayout progressContainer;
    private TextView tvStatusMessage;
    private EditText etExtractedText;
    private LinearLayout actionButtonsLayout;
    private Button btnSendBoth;
    private Button btnSendTextOnly;

    private Uri capturedPhotoUri = null;
    private File capturedPhotoFile = null;
    private Bitmap currentCardBitmap = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new KeyboardPreferences(this);

        // Build Modern Dark UI Programmatically
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#090d16"));
        scrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(24));

        // 1. Header with Close Button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("📇 Contact Card Scanner");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tvTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        tvTitle.setLayoutParams(titleParams);
        header.addView(tvTitle);

        Button btnClose = new Button(this);
        btnClose.setText("✕");
        btnClose.setTextSize(16);
        btnClose.setTextColor(Color.parseColor("#94a3b8"));
        btnClose.setBackgroundColor(Color.TRANSPARENT);
        btnClose.setOnClickListener(v -> finish());
        header.addView(btnClose);

        root.addView(header);

        // Subtitle
        TextView tvSub = new TextView(this);
        tvSub.setText("Capture or select a business card. AI extracts contact info into text and attaches the card image.");
        tvSub.setTextColor(Color.parseColor("#94a3b8"));
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvSub.setPadding(0, dpToPx(4), 0, dpToPx(16));
        root.addView(tvSub);

        // 2. Capture / Pick Trigger Buttons
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        Button btnCamera = createActionButton("📸 Take Photo", "#4338ca", "#6366f1");
        LinearLayout.LayoutParams pCam = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        pCam.setMargins(0, 0, dpToPx(6), 0);
        btnCamera.setLayoutParams(pCam);
        btnCamera.setOnClickListener(v -> checkCameraPermissionAndLaunch());
        btnRow.addView(btnCamera);

        Button btnGallery = createActionButton("🖼️ From Gallery", "#1e293b", "#334155");
        LinearLayout.LayoutParams pGal = new LinearLayout.LayoutParams(0, dpToPx(44), 1.0f);
        pGal.setMargins(dpToPx(6), 0, 0, 0);
        btnGallery.setLayoutParams(pGal);
        btnGallery.setOnClickListener(v -> launchGallery());
        btnRow.addView(btnGallery);

        root.addView(btnRow);

        // 3. Card Image Preview View
        imgCardPreview = new ImageView(this);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(190));
        imgParams.setMargins(0, dpToPx(16), 0, dpToPx(12));
        imgCardPreview.setLayoutParams(imgParams);
        imgCardPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgCardPreview.setBackground(createBoxDrawable("#131a29", "#1e293b", 12));
        imgCardPreview.setVisibility(View.GONE);
        root.addView(imgCardPreview);

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
        progParams.setMargins(0, dpToPx(8), 0, dpToPx(8));
        progressContainer.setLayoutParams(progParams);
        root.addView(progressContainer);

        // 5. Extracted Text Result Editor
        TextView tvEditLabel = new TextView(this);
        tvEditLabel.setText("Extracted Contact Information (Editable):");
        tvEditLabel.setTextColor(Color.parseColor("#cbd5e1"));
        tvEditLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvEditLabel.setPadding(0, dpToPx(12), 0, dpToPx(4));
        root.addView(tvEditLabel);

        etExtractedText = new EditText(this);
        etExtractedText.setTextColor(Color.WHITE);
        etExtractedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        etExtractedText.setLineSpacing(dpToPx(2), 1.15f);
        etExtractedText.setBackground(createBoxDrawable("#0f1624", "#1e293b", 8));
        etExtractedText.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        etExtractedText.setGravity(Gravity.TOP | Gravity.START);
        etExtractedText.setMinLines(6);
        etExtractedText.setHint("Extracted text will appear here. You can edit any details before sending.");
        etExtractedText.setHintTextColor(Color.parseColor("#475569"));
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etParams.setMargins(0, 0, 0, dpToPx(16));
        etExtractedText.setLayoutParams(etParams);
        root.addView(etExtractedText);

        // 6. Action Buttons: "Send Image & Text" + "Send Text Only"
        actionButtonsLayout = new LinearLayout(this);
        actionButtonsLayout.setOrientation(LinearLayout.VERTICAL);
        actionButtonsLayout.setVisibility(View.GONE);

        btnSendBoth = createActionButton("📤 Send Image & Text", "#15803d", "#22c55e");
        btnSendBoth.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48)));
        btnSendBoth.setOnClickListener(v -> handleSend(true));
        actionButtonsLayout.addView(btnSendBoth);

        btnSendTextOnly = createActionButton("📋 Send Text Only", "#334155", "#475569");
        LinearLayout.LayoutParams pTextOnly = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(42));
        pTextOnly.setMargins(0, dpToPx(8), 0, 0);
        btnSendTextOnly.setLayoutParams(pTextOnly);
        btnSendTextOnly.setOnClickListener(v -> handleSend(false));
        actionButtonsLayout.addView(btnSendTextOnly);

        root.addView(actionButtonsLayout);

        scrollView.addView(root);
        setContentView(scrollView);

        // Auto launch camera if requested
        if (getIntent() != null && getIntent().getBooleanExtra("auto_camera", false)) {
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

                // Save copy to our cache file
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
            imgCardPreview.setImageBitmap(bitmap);
            imgCardPreview.setVisibility(View.VISIBLE);
            runAiCardExtraction(bitmap);
        }
    }

    private void runAiCardExtraction(Bitmap bitmap) {
        progressContainer.setVisibility(View.VISIBLE);
        tvStatusMessage.setText("⚡ Analyzing contact card with Gemini AI...");
        actionButtonsLayout.setVisibility(View.GONE);

        // Convert Bitmap to JPEG byte array
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
                // Fallback template
                String fallback = "📇 CONTACT CARD\n" +
                        "👤 Name: [Scanned Contact]\n" +
                        "💼 Title: [Title]\n" +
                        "🏢 Company: [Company]\n" +
                        "📞 Phone: \n" +
                        "✉️ Email: \n" +
                        "🌐 Website: \n" +
                        "📍 Address: \n" +
                        "📝 Notes: (Set Gemini API Key in Settings for AI extraction)";
                etExtractedText.setText(fallback);
                Toast.makeText(CardScannerActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void handleSend(boolean sendImageAndText) {
        String cardText = etExtractedText.getText().toString().trim();
        if (cardText.isEmpty()) {
            Toast.makeText(this, "No contact card text to send", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri imageUriToSend = sendImageAndText ? capturedPhotoUri : null;

        // 1. Pass to Keyboard Bridge
        ContactCardTransfer.setPendingCard(cardText, imageUriToSend, "image/jpeg");

        // 2. Put onto System Clipboard
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                ClipData clip;
                if (imageUriToSend != null) {
                    clip = ClipData.newUri(getContentResolver(), "Scanned Contact Card", imageUriToSend);
                    clip.addItem(new ClipData.Item(cardText));
                } else {
                    clip = ClipData.newPlainText("Scanned Contact Card", cardText);
                }
                cm.setPrimaryClip(clip);
            }
        } catch (Exception ignored) {}

        Toast.makeText(this, "📇 Sending Contact Card...", Toast.LENGTH_SHORT).show();

        // Finish activity and return to target app
        finish();
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
