package com.sendw.mpos.shared.api;
import com.sendw.mpos.shared.domain.Validation;
import com.sendw.mpos.shared.domain.BusinessException;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(BusinessException.class)
    ResponseEntity<?> business(BusinessException e) {
        ErrorDiagnostics.record(e);
        int status = switch (e.kind()) { case INVALID -> 422; case NOT_FOUND -> 404; case CONFLICT -> 409; };
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        ErrorDiagnostics.record(e);
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Request failed" : e.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        ErrorDiagnostics.record(e);
        return ResponseEntity.unprocessableEntity().body(Map.of("message", "Validation failed", "errors",
                e.getBindingResult().getFieldErrors().stream().map(x -> Map.of("field",x.getField(),"message",x.getDefaultMessage())).toList()));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge() {return ResponseEntity.status(413).body(Map.of("message","Shipment files: maximum 10 MB; images elsewhere: 2 MB; request: 12 MB"));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException e) { ErrorDiagnostics.record(e); return ResponseEntity.status(409).body(Map.of("message","Duplicate value or related records prevent this operation")); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception e) { ErrorDiagnostics.record(e); return ResponseEntity.badRequest().body(Map.of("message","Malformed request")); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        ErrorDiagnostics.record(e);
        int status = e instanceof org.springframework.web.ErrorResponse response ? response.getStatusCode().value()
                : e instanceof org.springframework.security.access.AccessDeniedException ? 403
                : e instanceof org.springframework.security.core.AuthenticationException ? 401 : 500;
        return ResponseEntity.status(status).body(Map.of("message", status >= 500 ? "Unexpected server error" : "Request failed",
                "request_id", java.util.Objects.toString(org.slf4j.MDC.get("requestId"), "")));
    }
}
