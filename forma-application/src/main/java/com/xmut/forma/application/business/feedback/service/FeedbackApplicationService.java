package com.xmut.forma.application.business.feedback.service;

import com.xmut.forma.application.business.feedback.command.SubmitFeedbackCommand;
import com.xmut.forma.application.business.feedback.dto.FeedbackDTO;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.logging.LoggerUtils;
import com.xmut.forma.common.logging.NameValue;
import com.xmut.forma.common.util.ObjectUtils;
import com.xmut.forma.common.util.StringUtils;
import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;
import com.xmut.forma.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.forma.domain.business.feedback.model.Feedback;
import com.xmut.forma.domain.business.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Feedback 写用例：本人成果、标签「质量好/质量差」、可选短文；零积分路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackApplicationService {

    public static final String TAG_POOR_QUALITY = "质量差";
    public static final String TAG_GOOD_QUALITY = "质量好";
    public static final String MSG_ARTIFACT_UNAVAILABLE = "成果不存在或无权操作";
    private static final int COMMENT_MAX_LEN = 512;

    private final FeedbackRepository feedbackRepository;
    private final ArtifactRepository artifactRepository;
    private final Clock clock;

    @Transactional(rollbackFor = Exception.class)
    public FeedbackDTO submit(SubmitFeedbackCommand command) {
        ObjectUtils.requireNonNull(command, "command required");
        String userId = StringUtils.requireHasText(command.getUserId(), "userId required");
        String artifactId = StringUtils.requireHasText(command.getArtifactId(), "请选择要反馈的成果");
        String tag = StringUtils.requireHasText(command.getTag(), "请选择反馈标签").trim();
        if (!TAG_POOR_QUALITY.equals(tag) && !TAG_GOOD_QUALITY.equals(tag)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "仅支持「质量好」或「质量差」反馈");
        }
        String comment = normalizeComment(command.getCommentText());

        Artifact artifact = requireOwnedUsableArtifact(userId, artifactId);
        Instant now = Instant.now(clock);
        Optional<Feedback> existing = feedbackRepository.findByUserAndArtifact(userId, artifact.getId());
        if (existing.isPresent()) {
            Feedback f = existing.get();
            f.setTag(tag);
            f.setCommentText(comment);
            f.setUpdatedAt(now);
            feedbackRepository.save(f);
            logSubmit(userId, f.getId(), artifact.getId(), tag);
            return toDto(f);
        }
        String id = UUID.randomUUID().toString();
        Feedback feedback = Feedback.create(id, userId, artifact.getId(), tag, comment, now);
        feedbackRepository.save(feedback);
        logSubmit(userId, id, artifact.getId(), tag);
        return toDto(feedback);
    }

    @Transactional(readOnly = true)
    public FeedbackDTO findById(String userId, String feedbackId) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String id = StringUtils.requireHasText(feedbackId, "feedbackId required");
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_ARTIFACT_UNAVAILABLE));
        if (!uid.equals(feedback.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_ARTIFACT_UNAVAILABLE);
        }
        return toDto(feedback);
    }

    @Transactional(readOnly = true)
    public Optional<FeedbackDTO> findByArtifact(String userId, String artifactId) {
        String uid = StringUtils.requireHasText(userId, "userId required");
        String aid = StringUtils.requireHasText(artifactId, "请选择要查看的成果");
        Optional<Feedback> found = feedbackRepository.findByUserAndArtifact(uid, aid);
        if (!found.isPresent()) {
            return Optional.empty();
        }
        return Optional.of(toDto(found.get()));
    }

    private Artifact requireOwnedUsableArtifact(String userId, String artifactId) {
        Optional<Artifact> found = artifactRepository.findById(artifactId);
        if (!found.isPresent()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_ARTIFACT_UNAVAILABLE);
        }
        Artifact artifact = found.get();
        if (!userId.equals(artifact.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_ARTIFACT_UNAVAILABLE);
        }
        ArtifactType type = artifact.getType();
        if (type != ArtifactType.PICKLIST && type != ArtifactType.SKU) {
            throw new BusinessException(ErrorCode.FORBIDDEN, MSG_ARTIFACT_UNAVAILABLE);
        }
        return artifact;
    }

    private static String normalizeComment(String commentText) {
        if (!StringUtils.hasText(commentText)) {
            return null;
        }
        String trimmed = commentText.trim();
        if (trimmed.length() > COMMENT_MAX_LEN) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "反馈短文过长");
        }
        return trimmed;
    }

    private void logSubmit(String userId, String feedbackId, String artifactId, String tag) {
        LoggerUtils.success(log, FeedbackApplicationService.class, "submit",
                NameValue.create("userId", userId),
                NameValue.create("feedbackId", feedbackId),
                NameValue.create("artifactId", artifactId),
                NameValue.create("tag", tag));
    }

    private static FeedbackDTO toDto(Feedback feedback) {
        return new FeedbackDTO(
                feedback.getId(),
                feedback.getArtifactId(),
                feedback.getTag(),
                feedback.getCommentText(),
                feedback.getCreatedAt());
    }
}
