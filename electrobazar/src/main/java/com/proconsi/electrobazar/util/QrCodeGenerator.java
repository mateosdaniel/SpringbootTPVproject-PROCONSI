package com.proconsi.electrobazar.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for generating QR codes.
 */
public class QrCodeGenerator {

    /**
     * Generates a QR code image as a Base64 encoded string.
     *
     * @param text   The content to encode in the QR code.
     * @param width  Width in pixels.
     * @param height Height in pixels.
     * @return Base64 encoded PNG image.
     */
    public static String generateQrBase64(String text, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            byte[] pngData = outputStream.toByteArray();
            return Base64.getEncoder().encodeToString(pngData);

        } catch (Exception e) {
            throw new RuntimeException("Error generating QR code", e);
        }
    }
}
