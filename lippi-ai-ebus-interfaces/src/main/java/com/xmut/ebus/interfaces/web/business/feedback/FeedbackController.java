package com.xmut.ebus.interfaces.web.business.feedback;

import com.xmut.ebus.application.business.feedback.command.SubmitFeedbackCommand;
import com.xmut.ebus.application.business.feedback.dto.FeedbackDTO;
import com.xmut.ebus.application.business.feedback.service.FeedbackApplicationService;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import com.xmut.ebus.interfaces.vo.business.feedback.SubmitFeedbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成果质量反馈（JWT；零积分）。
 */
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackApplicationService feedbackApplicationService;

    @PostMapping
    public ApiResponse<FeedbackDTO> submit(@RequestBody(required = false) SubmitFeedbackRequest request) {
        SubmitFeedbackRequest body = request != null ? request : new SubmitFeedbackRequest();
        SubmitFeedbackCommand command = SubmitFeedbackCommand.builder()
                .userId(SecuritySupport.requireUserId())
                .username(SecuritySupport.currentUsername())
                .artifactId(body.getArtifactId())
                .tag(body.getTag())
                .commentText(body.getCommentText())
                .build();
        return ApiResponse.success(feedbackApplicationService.submit(command));
    }

    @GetMapping("/{id}")
    public ApiResponse<FeedbackDTO> getFeedback(@PathVariable("id") String id) {
        return ApiResponse.success(
                feedbackApplicationService.findById(SecuritySupport.requireUserId(), id));
    }
}
