package com.xmut.ebus.application.business.feedback.service;

import com.xmut.ebus.application.business.feedback.command.SubmitFeedbackCommand;
import com.xmut.ebus.application.business.feedback.dto.FeedbackDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.ebus.domain.business.feedback.model.Feedback;
import com.xmut.ebus.domain.business.feedback.repository.FeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedbackApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-28T04:00:00Z");
    private static final String USER = "user-1";
    private static final String ARTIFACT_ID = "art-1";

    private FeedbackRepository feedbackRepository;
    private ArtifactRepository artifactRepository;
    private FeedbackApplicationService service;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(FeedbackRepository.class);
        artifactRepository = mock(ArtifactRepository.class);
        service = new FeedbackApplicationService(
                feedbackRepository,
                artifactRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitGoodQualityUpsertsSameArtifact() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(ownedPicklist()));
        when(feedbackRepository.findByUserAndArtifact(USER, ARTIFACT_ID)).thenReturn(Optional.empty());
        service.submit(cmd(USER, ARTIFACT_ID, "质量好", null));
        verify(feedbackRepository).save(argThat(f -> "质量好".equals(f.getTag())));

        Feedback existing = Feedback.create("fb1", USER, ARTIFACT_ID, "质量好", null, Instant.parse("2026-09-01T00:00:00Z"));
        when(feedbackRepository.findByUserAndArtifact(USER, ARTIFACT_ID)).thenReturn(Optional.of(existing));
        service.submit(cmd(USER, ARTIFACT_ID, "质量差", "偏水"));
        verify(feedbackRepository, atLeastOnce()).save(argThat(f ->
                "fb1".equals(f.getId()) && "质量差".equals(f.getTag()) && "偏水".equals(f.getCommentText())));
    }

    @Test
    void submitPersistsPoorQualityWithoutTouchingCredits() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(ownedPicklist()));

        FeedbackDTO dto = service.submit(SubmitFeedbackCommand.builder()
                .userId(USER)
                .username("u")
                .artifactId(ARTIFACT_ID)
                .tag(FeedbackApplicationService.TAG_POOR_QUALITY)
                .commentText("  文案太水  ")
                .build());

        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        Feedback saved = captor.getValue();
        assertEquals(USER, saved.getUserId());
        assertEquals(ARTIFACT_ID, saved.getArtifactId());
        assertEquals(FeedbackApplicationService.TAG_POOR_QUALITY, saved.getTag());
        assertEquals("文案太水", saved.getCommentText());
        assertEquals(NOW, saved.getCreatedAt());
        assertEquals(saved.getId(), dto.getId());
        assertEquals("文案太水", dto.getCommentText());
    }

    @Test
    void submitRejectsOtherUsersArtifact() {
        Artifact other = ownedPicklist();
        other.setUserId("other");
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(SubmitFeedbackCommand.builder()
                        .userId(USER)
                        .artifactId(ARTIFACT_ID)
                        .tag(FeedbackApplicationService.TAG_POOR_QUALITY)
                        .build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitRejectsMissingArtifact() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(SubmitFeedbackCommand.builder()
                        .userId(USER)
                        .artifactId(ARTIFACT_ID)
                        .tag(FeedbackApplicationService.TAG_POOR_QUALITY)
                        .build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void findByIdReturnsOwnFeedback() {
        String id = UUID.randomUUID().toString();
        Feedback feedback = Feedback.create(id, USER, ARTIFACT_ID,
                FeedbackApplicationService.TAG_POOR_QUALITY, null, NOW);
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(feedback));

        FeedbackDTO dto = service.findById(USER, id);
        assertEquals(id, dto.getId());
        assertNull(dto.getCommentText());
    }

    @Test
    void findByIdRejectsOtherUsersFeedback() {
        String id = UUID.randomUUID().toString();
        Feedback feedback = Feedback.create(id, "other", ARTIFACT_ID,
                FeedbackApplicationService.TAG_POOR_QUALITY, null, NOW);
        when(feedbackRepository.findById(id)).thenReturn(Optional.of(feedback));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.findById(USER, id));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void findByArtifactReturnsOwnFeedback() {
        Feedback feedback = Feedback.create("fb1", USER, ARTIFACT_ID,
                FeedbackApplicationService.TAG_GOOD_QUALITY, null, NOW);
        when(feedbackRepository.findByUserAndArtifact(USER, ARTIFACT_ID)).thenReturn(Optional.of(feedback));

        Optional<FeedbackDTO> dto = service.findByArtifact(USER, ARTIFACT_ID);
        assertTrue(dto.isPresent());
        assertEquals("fb1", dto.get().getId());
        assertEquals(FeedbackApplicationService.TAG_GOOD_QUALITY, dto.get().getTag());
    }

    @Test
    void findByArtifactEmptyWhenNone() {
        when(feedbackRepository.findByUserAndArtifact(USER, ARTIFACT_ID)).thenReturn(Optional.empty());
        assertFalse(service.findByArtifact(USER, ARTIFACT_ID).isPresent());
    }

    @Test
    void submitRejectsNonPoorQualityTag() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(ownedPicklist()));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(SubmitFeedbackCommand.builder()
                        .userId(USER)
                        .artifactId(ARTIFACT_ID)
                        .tag("一般")
                        .build()));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitRejectsChatArtifact() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(
                Artifact.create(ARTIFACT_ID, USER, "run-c", ArtifactType.CHAT, "ecommerce",
                        null, "聊", "{}", NOW)));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(SubmitFeedbackCommand.builder()
                        .userId(USER)
                        .artifactId(ARTIFACT_ID)
                        .tag(FeedbackApplicationService.TAG_POOR_QUALITY)
                        .build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitRejectsListingPlanArtifact() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(
                Artifact.create(ARTIFACT_ID, USER, "run-p", ArtifactType.LISTING_PLAN, "ecommerce",
                        null, "策划", "{}", NOW)));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(SubmitFeedbackCommand.builder()
                        .userId(USER)
                        .artifactId(ARTIFACT_ID)
                        .tag(FeedbackApplicationService.TAG_POOR_QUALITY)
                        .build()));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void submitRejectsCommentOver512() {
        when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(ownedPicklist()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 513; i++) {
            sb.append('x');
        }

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.submit(SubmitFeedbackCommand.builder()
                        .userId(USER)
                        .artifactId(ARTIFACT_ID)
                        .tag(FeedbackApplicationService.TAG_POOR_QUALITY)
                        .commentText(sb.toString())
                        .build()));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(feedbackRepository, never()).save(any());
    }

    private static Artifact ownedPicklist() {
        return Artifact.create(
                ARTIFACT_ID, USER, "run-1", ArtifactType.PICKLIST, "ecommerce",
                null, "选品", "{}", NOW);
    }

    private static SubmitFeedbackCommand cmd(String userId, String artifactId, String tag, String commentText) {
        return SubmitFeedbackCommand.builder()
                .userId(userId)
                .artifactId(artifactId)
                .tag(tag)
                .commentText(commentText)
                .build();
    }
}
