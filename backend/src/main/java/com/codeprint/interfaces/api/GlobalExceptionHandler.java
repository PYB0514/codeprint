// 전역 예외를 잡아 일관된 JSON 오류 응답으로 변환하는 핸들러
package com.codeprint.interfaces.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ResponseStatusException을 JSON 오류 응답으로 변환
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return errorResponse(ex.getStatusCode().value(), ex.getReason());
    }

    // 잘못된 인자 예외를 400 오류 응답으로 변환
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    // 상태 충돌 예외를 409 오류 응답으로 변환
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return errorResponse(HttpStatus.CONFLICT.value(), ex.getMessage());
    }

    // 처리되지 않은 예외를 500 오류 응답으로 변환
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
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
