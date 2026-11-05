package ai.applypilot.certifications;

import java.nio.charset.StandardCharsets;

public final class CertificatePdfValidator {
    private CertificatePdfValidator() {}

    public static boolean isValid(byte[] bytes, long maxBytes) {
        if (bytes == null || bytes.length < 12 || bytes.length > maxBytes) return false;
        String head = new String(bytes, 0, Math.min(8, bytes.length), StandardCharsets.US_ASCII);
        String tail = new String(bytes, Math.max(0, bytes.length - 2048), Math.min(2048, bytes.length), StandardCharsets.US_ASCII);
        return head.startsWith("%PDF-") && tail.contains("%%EOF");
    }
}
