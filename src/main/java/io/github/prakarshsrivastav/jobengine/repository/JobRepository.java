package io.github.prakarshsrivastav.jobengine.repository;

import io.github.prakarshsrivastav.jobengine.domain.Job;
import io.github.prakarshsrivastav.jobengine.domain.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    // Claim the next runnable job. SKIP LOCKED lets concurrent workers (threads or nodes)
    // each grab a different row without blocking; the row lock lasts until the claim commits.
    // priority DESC works because the enum is declared LOW < NORMAL < HIGH < CRITICAL.
    @Query(value = """
            SELECT * FROM jobs
            WHERE status IN ('PENDING', 'RETRYING')
              AND (scheduled_at IS NULL OR scheduled_at <= now())
            ORDER BY priority DESC, created_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Job> findAndLockNextRunnable();

    // In-flight jobs whose lease ran out: their worker died or stalled.
    @Query(value = """
            SELECT * FROM jobs
            WHERE status IN ('QUEUED', 'RUNNING')
              AND heartbeat_at < :cutoff
            ORDER BY heartbeat_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Job> findAndLockExpiredLeases(@Param("cutoff") OffsetDateTime cutoff, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM Job j WHERE j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query(value = """
            UPDATE jobs SET heartbeat_at = now()
            WHERE worker_id = :workerId
              AND id IN (:ids)
              AND status IN ('QUEUED', 'RUNNING')
            """, nativeQuery = true)
    int heartbeat(@Param("workerId") String workerId, @Param("ids") Collection<UUID> ids);

    @Query(value = "SELECT status::text AS status, count(*) AS total FROM jobs GROUP BY status", nativeQuery = true)
    List<Object[]> countGroupedByStatus();

    Page<Job> findByStatus(JobStatus status, Pageable pageable);

    Page<Job> findByType(String type, Pageable pageable);

    Page<Job> findByStatusAndType(JobStatus status, String type, Pageable pageable);
}
