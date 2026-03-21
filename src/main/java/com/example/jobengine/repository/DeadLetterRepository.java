package com.example.jobengine.repository;

import com.example.jobengine.domain.DeadLetterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntry, UUID> {
    boolean existsByJobId(UUID jobId);
}