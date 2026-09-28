package com.xmut.ebus.infrastructure.persistence.repository.business.feedback;

import com.xmut.ebus.domain.business.feedback.model.Feedback;
import com.xmut.ebus.domain.business.feedback.repository.FeedbackRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.FeedbackMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.FeedbackPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class FeedbackRepositoryImpl implements FeedbackRepository {

    private final FeedbackMapper feedbackMapper;

    @Override
    public void save(Feedback feedback) {
        FeedbackPO po = toPo(feedback);
        if (StringUtils.hasText(feedback.getId())) {
            FeedbackPO existing = feedbackMapper.selectByBizId(feedback.getId().trim());
            if (existing != null) {
                feedbackMapper.updateByBizId(po);
                return;
            }
        }
        feedbackMapper.insert(po);
    }

    @Override
    public Optional<Feedback> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        FeedbackPO po = feedbackMapper.selectByBizId(id.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public Optional<Feedback> findByUserAndArtifact(String userId, String artifactId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(artifactId)) {
            return Optional.empty();
        }
        FeedbackPO po = feedbackMapper.selectByUserAndArtifact(userId.trim(), artifactId.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    private static Feedback toDomain(FeedbackPO po) {
        Feedback feedback = new Feedback();
        feedback.setId(po.getBizId());
        feedback.setUserId(po.getUserId());
        feedback.setArtifactId(po.getArtifactId());
        feedback.setTag(po.getTag());
        feedback.setCommentText(po.getCommentText());
        feedback.setCreatedAt(po.getCreatedAt());
        feedback.setUpdatedAt(po.getUpdatedAt());
        return feedback;
    }

    private static FeedbackPO toPo(Feedback feedback) {
        FeedbackPO po = new FeedbackPO();
        po.setBizId(feedback.getId());
        po.setUserId(feedback.getUserId());
        po.setArtifactId(feedback.getArtifactId());
        po.setTag(feedback.getTag());
        po.setCommentText(feedback.getCommentText());
        po.setCreatedAt(feedback.getCreatedAt());
        po.setUpdatedAt(feedback.getUpdatedAt());
        return po;
    }
}
