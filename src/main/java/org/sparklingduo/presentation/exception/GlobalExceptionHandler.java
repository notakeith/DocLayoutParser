package org.sparklingduo.presentation.exception;

import org.sparklingduo.domain.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TemplateNotFoundException ex) {
        return createResponse(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({
            InvalidTemplateException.class,
            UnsupportedImageFormatException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(BaseException ex) {
        return createResponse(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({
            ImageProcessingException.class,
            OcrException.class,
            OcrDataMissingException.class
    })
    public ResponseEntity<ErrorResponse> handleInternalError(BaseException ex) {
        return createResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Обработка стандартных ошибок Spring (валидация, битые JSON и т.д.)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return createResponse("An unexpected error occurred: " + ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ErrorResponse> createResponse(String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                LocalDateTime.now()
        );
        return new ResponseEntity<>(error, status);
    }

    // DTO для ответа об ошибке
    public record ErrorResponse(int status, String message, LocalDateTime timestamp) {}
}
