package com.xmut.ebus.domain.business.feedback.repository;

import com.xmut.ebus.domain.business.feedback.model.Feedback;

import java.util.Optional;

/**
 * 反馈仓储（物理表 {@code ebus_feedback}）。
 */
public interface FeedbackRepository {

    void save(Feedback feedback);

    Optional<Feedback> findById(String id);

    Optional<Feedback> findByUserAndArtifact(String userId, String artifactId);
}
