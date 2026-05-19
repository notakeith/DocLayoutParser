package org.sparklingduo.repository;

import org.sparklingduo.domain.job.RecognitionJob;
import org.sparklingduo.domain.job.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RecognitionJobRepository extends JpaRepository<RecognitionJob, UUID> {
    List<RecognitionJob> findAllByOrderByCreatedAtDesc();
    List<RecognitionJob> findByTemplateIdOrderByCreatedAtDesc(UUID templateId);
    List<RecognitionJob> findByStatusOrderByCreatedAtAsc(JobStatus status);
    void deleteByTemplateId(UUID templateId);
}