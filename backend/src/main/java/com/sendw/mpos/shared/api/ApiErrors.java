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
        int status = switch (e.kind()) { case INVALID -> 422; case NOT_FOUND -> 404; case CONFLICT -> 409; };
        return ResponseEntity.status(status).body(Map.of("message", e.getMessage()));
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Request failed" : e.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.unprocessableEntity().body(Map.of("message", "Validation failed", "errors",
                e.getBindingResult().getFieldErrors().stream().map(x -> Map.of("field",x.getField(),"message",x.getDefaultMessage())).toList()));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge() {return ResponseEntity.status(413).body(Map.of("message","Images must be at most 2 MB each and 12 MB per request"));}
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message","Duplicate value or related records prevent this operation")); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","Malformed request")); }
}
