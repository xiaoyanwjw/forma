package com.xmut.ebus.interfaces.web.business.session;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.session.dto.SessionMessageDTO;
import com.xmut.ebus.application.business.session.dto.SessionSummaryDTO;
import com.xmut.ebus.application.business.session.query.SessionQueryService;
import com.xmut.ebus.common.page.Page;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SessionQuery 薄控制器：本人近 60 天会话与 R1 消息。
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionQueryService sessionQueryService;

    @GetMapping
    public ApiResponse<List<SessionSummaryDTO>> list(
            @RequestParam(value = "sceneCode", required = false) String sceneCode,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.success(
                sessionQueryService.list(SecuritySupport.requireUserId(), sceneCode, limit));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<Page<SessionMessageDTO>> listMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "nextToken", required = false) String nextToken,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.success(
                sessionQueryService.getMessageList(
                        SecuritySupport.requireUserId(), sessionId, nextToken, limit));
    }

    @GetMapping("/{sessionId}/latest-artifact")
    public ApiResponse<HistoryArtifactDetailDTO> latestArtifact(
            @PathVariable("sessionId") String sessionId) {
        return ApiResponse.success(
                sessionQueryService.latestArtifact(SecuritySupport.requireUserId(), sessionId)
                        .orElse(null));
    }
}
