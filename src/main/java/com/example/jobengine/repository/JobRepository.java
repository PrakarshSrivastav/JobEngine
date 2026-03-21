package com.example.jobengine.repository;

import com.example.jobengine.domain.Job;
import com.example.jobengine.domain.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<Job, UUID> {

    // Find jobs by status ordered by priority and age
    @Query(value = """
            SELECT * FROM jobs
            WHERE status = :status
            ORDER BY
                CASE priority
                    WHEN 'CRITICAL' THEN 1
                    WHEN 'HIGH' THEN 2
                    WHEN 'NORMAL' THEN 3
                    WHEN 'LOW' THEN 4
                END,
                created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Job> findJobsByStatusOrderedByPriority(
            @Param("status") String status,
            @Param("limit") int limit
    );

    // Try to claim a job using PostgreSQL advisory lock
    @Query(value = """
        SELECT * FROM jobs
        WHERE (status = 'PENDING' OR (status = 'RETRYING' AND scheduled_at <= now()))
        AND pg_try_advisory_xact_lock(('x' || substr(id::text, 1, 8))::bit(32)::bigint)
        ORDER BY
            CASE priority
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'NORMAL' THEN 3
                WHEN 'LOW' THEN 4
            END,
            created_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Job> findAndLockNextPendingJob();

    // Find all jobs by status
    List<Job> findByStatus(JobStatus status);

    // Find jobs by worker
    List<Job> findByWorkerId(String workerId);

    // Count jobs by status
    long countByStatus(JobStatus status);

    // Check if job exists by id and status
    boolean existsByIdAndStatus(UUID id, JobStatus status);
}