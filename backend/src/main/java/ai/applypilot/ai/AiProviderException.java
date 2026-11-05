package ai.applypilot.ai;

public class AiProviderException extends RuntimeException {
    public enum Kind { DISABLED, UNAVAILABLE, INVALID_RESPONSE }

    private final Kind kind;

    public AiProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() { return kind; }
}
