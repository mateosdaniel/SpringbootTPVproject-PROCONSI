package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository for {@link ActivityLog} entities.
 * Provides audit trail tracking and log maintenance functionality.
 */
@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    /**
     * Retrieves the 50 most recent activity logs for general monitoring.
     * @return List of recent logs.
     */
    List<ActivityLog> findTop50ByOrderByTimestampDesc();

    /**
     * Retrieves a smaller subset of the 20 most recent logs for quick dashboard views.
     * @return List of recent logs.
     */
    List<ActivityLog> findTop20ByOrderByTimestampDesc();

    /**
     * Purges logs of specific action types that are older than a given timestamp.
     * Useful for cleaning up high-frequency but low-value logs.
     */
    void deleteByTimestampBeforeAndActionIn(LocalDateTime timestamp, Collection<String> actions);

    /**
     * Purges logs except for those of specific action types that are older than a given timestamp.
     * Useful for retaining critical logs while cleaning up the rest.
     */
    void deleteByTimestampBeforeAndActionNotIn(LocalDateTime timestamp, Collection<String> actions);
}
