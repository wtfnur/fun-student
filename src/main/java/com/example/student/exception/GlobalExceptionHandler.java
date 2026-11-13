package com.example.student.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler
    public ResponseEntity<AppError> catchEntityNotFoundException(EntityNotFoundException e) {
        log.error(e.getMessage(), e);
        return new ResponseEntity<>(new AppError(HttpStatus.NOT_FOUND.value(), e.getMessage()), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<AppError> duplicateEmail(EmailAlreadyExistsException e) {
        return ResponseEntity.status(409).body(new AppError(409, e.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<AppError> authentication(AuthenticationException e) {
        return ResponseEntity.status(401).body(new AppError(401, "Неверный email или пароль"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<AppError> forbidden(AccessDeniedException e) {
        return ResponseEntity.status(403).body(new AppError(403, "Недостаточно прав"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppError> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage()).distinct()
                .collect(java.util.stream.Collectors.joining(". "));
        return ResponseEntity.badRequest().body(new AppError(400, message));
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<AppError> invalidInput(Exception e) {
        return ResponseEntity.badRequest().body(new AppError(400, "Проверьте введённые данные"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<AppError> databaseConflict(DataIntegrityViolationException e) {
        return ResponseEntity.status(409).body(new AppError(409, "Конфликт данных: проверьте email и связанные записи"));
    }
}
