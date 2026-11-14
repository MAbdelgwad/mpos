package com.sendw.mpos.shared.api;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Records exception types and code locations, never request data or database error values. */
public final class ErrorDiagnostics {
    private static final Logger log = LoggerFactory.getLogger(ErrorDiagnostics.class);
    private ErrorDiagnostics() {}

    public static void record(Throwable error) {
        Throwable cause = error;
        for (int i = 0; i < 8 && cause.getCause() != null && cause.getCause() != cause; i++) cause = cause.getCause();
        String frames = Arrays.stream(error.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.sendw.mpos."))
                .limit(8).map(StackTraceElement::toString).collect(Collectors.joining(" | "));
        log.warn("api_error type={} cause={} locations={}", error.getClass().getSimpleName(), cause.getClass().getSimpleName(), frames);
    }
}
