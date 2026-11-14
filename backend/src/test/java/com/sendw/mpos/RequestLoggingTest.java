package com.sendw.mpos;

import com.sendw.mpos.shared.api.ErrorDiagnostics;
import com.sendw.mpos.shared.infrastructure.RequestLoggingFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingTest {
    @Test void logsStatusAndPathWithoutRequestSecrets(CapturedOutput output) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/login");
        request.setQueryString("password=query-secret");
        request.setContent("{\"password\":\"body-secret\"}".getBytes());
        request.addHeader("Authorization", "Bearer header-secret");
        request.addHeader("X-Request-ID", "forged-id");
        var response = new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request, response, (req, res) -> response.setStatus(401));
        assertDoesNotThrow(() -> UUID.fromString(response.getHeader("X-Request-ID")));
        assertTrue(output.getAll().contains("path=/api/login status=401"));
        for (String secret : new String[]{"query-secret", "body-secret", "header-secret", "forged-id"}) assertFalse(output.getAll().contains(secret));
        assertNull(MDC.get("requestId"));
    }

    @Test void failingRequestRestoresThreadContext(CapturedOutput output) {
        MDC.put("requestId", "outer-context");
        try {
            assertThrows(IllegalStateException.class, () -> new RequestLoggingFilter().doFilter(
                    new MockHttpServletRequest("GET", "/api/failure"), new MockHttpServletResponse(),
                    (req, res) -> { throw new IllegalStateException("private-exception-value"); }));
            assertEquals("outer-context", MDC.get("requestId"));
            assertTrue(output.getAll().contains("status=500"));
            assertFalse(output.getAll().contains("private-exception-value"));
        } finally { MDC.remove("requestId"); }
    }

    @Test void exceptionDiagnosticsContainTypesButNotDatabaseValues(CapturedOutput output) {
        ErrorDiagnostics.record(new IllegalArgumentException("password=do-not-log-this",
                new RuntimeException("Key(email)=(private@example.com) already exists")));
        assertTrue(output.getAll().contains("type=IllegalArgumentException cause=RuntimeException"));
        assertFalse(output.getAll().contains("do-not-log-this"));
        assertFalse(output.getAll().contains("private@example.com"));
    }
}
