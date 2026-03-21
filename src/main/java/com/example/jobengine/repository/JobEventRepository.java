package com.example.jobengine.repository;

import com.example.jobengine.domain.JobEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobEventRepository extends JpaRepository<JobEvent, UUID> {

    List<JobEvent> findByJobIdOrderByOccurredAtAsc(UUID jobId);

    long countByJobId(UUID jobId);
}