package org.sparklingduo.domain.job;

import jakarta.persistence.*;
import lombok.*;
import org.sparklingduo.domain.template.Template;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "recognition_jobs")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class RecognitionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Setter
    private JobStatus status;

    private String sourceFileName;

    @Setter
    @Column(name = "source_storage_key")
    private String sourceStorageKey;

    @Column(nullable = false, updatable = false, name="created_ad")
    private Instant createdAt;

    @Setter
    @Column(name="finished_at")
    private Instant finishedAt;

    @Column(columnDefinition = "TEXT", name="error_message")
    @Setter
    private String errorMessage;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "job")
    @Builder.Default
    private List<RecognitionResult> results = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = JobStatus.PENDING;
    }

    public void markProcessing() {
        this.status = JobStatus.PROCESSING;
    }

    public void markDone(List<RecognitionResult> results) {
        this.status = JobStatus.DONE;
        this.finishedAt = Instant.now();
        this.results.clear();
        this.results.addAll(results);
        results.forEach(r -> r.setJob(this));
    }

    public void markFailed(String error) {
        this.status = JobStatus.FAILED;
        this.finishedAt = Instant.now();
        this.errorMessage = error;
    }
}