package graduation_project.Dailic.service;

import org.springframework.http.HttpStatus;

/** An AI failure whose message is safe to return to the mobile client. */
public class AIServiceException extends RuntimeException {
    private final HttpStatus status;

    public AIServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
