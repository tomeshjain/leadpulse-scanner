package com.wisprflow.keyboard;

import android.net.Uri;

/**
 * Thread-safe transfer bridge for scanned contact card payloads between
 * CardScannerActivity and WisprKeyboardService.
 */
public class ContactCardTransfer {

    public static class CardPayload {
        public final String text;
        public final Uri imageUri;
        public final String mimeType;

        public CardPayload(String text, Uri imageUri, String mimeType) {
            this.text = text;
            this.imageUri = imageUri;
            this.mimeType = mimeType != null ? mimeType : "image/jpeg";
        }
    }

    private static volatile CardPayload pendingPayload = null;

    public static synchronized void setPendingCard(String text, Uri imageUri, String mimeType) {
        pendingPayload = new CardPayload(text, imageUri, mimeType);
    }

    public static synchronized boolean hasPendingCard() {
        return pendingPayload != null;
    }

    public static synchronized CardPayload consumePendingCard() {
        CardPayload p = pendingPayload;
        pendingPayload = null;
        return p;
    }
}
