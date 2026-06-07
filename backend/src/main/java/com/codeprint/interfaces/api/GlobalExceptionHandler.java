// 전역 예외를 잡아 일관된 JSON 오류 응답으로 변환하는 핸들러
package com.codeprint.interfaces.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ResponseStatusException을 JSON 오류 응답으로 변환
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        if (ex.getStatusCode().value() >= 500) {
            log.error("ResponseStatusException: {}", ex.getMessage(), ex);
        }
        return errorResponse(ex.getStatusCode().value(), ex.getReason());
    }

    // @Valid 유효성 검증 실패를 400 오류 응답으로 변환
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return errorResponse(HttpStatus.BAD_REQUEST.value(), message);
    }

    // 잘못된 인자 예외를 400 오류 응답으로 변환
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    // 상태 충돌 예외를 409 오류 응답으로 변환
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return errorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
    }

    // 404 — Spring 정적 리소스/핸들러 미발견 (500으로 오분류 방지)
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return errorResponse(HttpStatus.NOT_FOUND.value(), "Not found");
    }

    // 403 — Spring Security AccessDenied (필터 체인 밖에서 발생한 경우)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return errorResponse(HttpStatus.FORBIDDEN.value(), "Access denied");
    }

    // 처리되지 않은 예외를 500 오류 응답으로 변환 — 항상 스택트레이스 로깅
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("[500] {} — {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error");
    }

    // 상태 코드와 메시지로 표준 오류 응답 생성
    private ResponseEntity<Map<String, Object>> errorResponse(int status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status,
                "message", message != null ? message : "Unknown error",
                "timestamp", Instant.now().toString()
        ));
    }
}
