package com.example.fairnesstracker.audit;

import com.example.fairnesstracker.common.web.RequestIdFilter;
import com.example.fairnesstracker.security.CurrentUser;
import com.example.fairnesstracker.user.AppUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Records who did what. Joins the caller's transaction, so an action that rolls back leaves no entry
 * (failed sign-ins are kept because that transaction commits on purpose). The client IP and request id come
 * from the current request; scheduled work records neither.
 */
@Service
public class AuditService {

    private static final int MAX_EMAIL_LENGTH = 255;

    private final AuditLogRepository repository;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** What the signed-in user did. */
    @Transactional
    public void record(AuditAction action, String entityType, Object entityId, Map<String, ?> details) {
        Long userId = CurrentUser.id().orElse(null);
        String email = CurrentUser.email().orElseGet(() -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication != null && authentication.isAuthenticated() ? authentication.getName() : null;
        });
        save(userId, email, action, entityType, entityId, details);
    }

    /** What a given user did, or had done to their account, outside a signed-in request (sign-in itself). */
    @Transactional
    public void recordFor(AppUser user, AuditAction action, Map<String, ?> details) {
        save(user.getId(), user.getEmail(), action, "USER", user.getId(), details);
    }

    /** Something attempted with an email that matches no user, e.g. a failed sign-in. */
    @Transactional
    public void recordUnknownUser(String attemptedEmail, AuditAction action, Map<String, ?> details) {
        String email = attemptedEmail != null && attemptedEmail.length() > MAX_EMAIL_LENGTH
                ? attemptedEmail.substring(0, MAX_EMAIL_LENGTH) : attemptedEmail;
        save(null, email, action, null, null, details);
    }

    /** Done by the application itself, e.g. creating the first admin at startup. */
    @Transactional
    public void recordSystem(AuditAction action, String entityType, Object entityId, Map<String, ?> details) {
        save(null, null, action, entityType, entityId, details);
    }

    private void save(Long userId, String email, AuditAction action, String entityType, Object entityId,
                      Map<String, ?> details) {
        HttpServletRequest request = currentRequest();
        repository.save(new AuditLog(clock.instant(), userId, email, action, entityType,
                entityId != null ? entityId.toString() : null,
                details == null || details.isEmpty() ? null : new LinkedHashMap<>(details),
                request != null ? request.getRemoteAddr() : null,
                request != null ? (String) request.getAttribute(RequestIdFilter.ATTRIBUTE) : null));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }
}
