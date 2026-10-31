package io.github.prakarshsrivastav.jobengine.repository;

import io.github.prakarshsrivastav.jobengine.domain.DeadLetterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeadLetterRepository extends JpaRepository<DeadLetterEntry, UUID> {
    boolean existsByJobId(UUID jobId);

    Optional<DeadLetterEntry> findByJobId(UUID jobId);
}
