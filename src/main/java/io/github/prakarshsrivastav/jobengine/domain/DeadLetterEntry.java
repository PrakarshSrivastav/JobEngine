package io.github.prakarshsrivastav.jobengine.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeadLetterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "final_error", columnDefinition = "TEXT")
    private String finalError;

    @Builder.Default
    @Column(name = "moved_at", nullable = false)
    private OffsetDateTime movedAt = OffsetDateTime.now();
}