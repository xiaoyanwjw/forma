package com.xmut.forma.domain.business.feedback.repository;

import com.xmut.forma.domain.business.feedback.model.Feedback;

import java.util.Optional;

/**
 * 反馈仓储（物理表 {@code forma_feedback}）。
 */
public interface FeedbackRepository {

    void save(Feedback feedback);

    Optional<Feedback> findById(String id);

    Optional<Feedback> findByUserAndArtifact(String userId, String artifactId);
}
