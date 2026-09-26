package graduation_project.Dailic.controller;

import graduation_project.Dailic.controller.DTO.ApiResponse;
import graduation_project.Dailic.service.AIServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AIController.class)
public class AIExceptionHandler {
    @ExceptionHandler(AIServiceException.class)
    public ResponseEntity<ApiResponse<Void>> aiFailure(AIServiceException error) {
        return failure(error.getStatus(), error.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> invalidRequest(Exception error) {
        return failure(HttpStatus.BAD_REQUEST,
                "질문(1~4000자), 사용자·문제 번호, 자격증 선택 정보를 확인해 주세요.");
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(status.value(), message, null));
    }
}
