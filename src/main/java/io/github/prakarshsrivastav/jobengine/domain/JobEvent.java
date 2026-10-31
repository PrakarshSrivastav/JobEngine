package io.github.prakarshsrivastav.jobengine.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", columnDefinition = "job_status")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    private JobStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, columnDefinition = "job_status")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.NAMED_ENUM)
    private JobStatus toStatus;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Builder.Default
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt = OffsetDateTime.now();
}