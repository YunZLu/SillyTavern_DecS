package async.exceptions;

public class ConfigLoadingException extends RuntimeException {
    public ConfigLoadingException(String message, Throwable cause) {
        super(message, cause);
    }
}
