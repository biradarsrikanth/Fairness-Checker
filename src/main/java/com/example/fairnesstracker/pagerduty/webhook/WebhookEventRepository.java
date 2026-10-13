package com.example.fairnesstracker.pagerduty.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long>,
        JpaSpecificationExecutor<WebhookEvent> {

    Optional<WebhookEvent> findByPagerdutyEventId(String pagerdutyEventId);

    /**
     * Takes the event for (re)processing if it's still in one of {@code from}: an atomic compare-and-set, so
     * two deliveries of the same event can't both apply it. Returns 1 if this caller got it.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE WebhookEvent e SET e.status = 'RECEIVED', e.error = null, e.processedAt = null
            WHERE e.id = :id AND e.status IN :from""")
    int claim(Long id, Collection<WebhookEvent.Status> from);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WebhookEvent e SET e.status = :status, e.processedAt = :at, e.error = :error WHERE e.id = :id")
    int finish(Long id, WebhookEvent.Status status, Instant at, String error);
}
