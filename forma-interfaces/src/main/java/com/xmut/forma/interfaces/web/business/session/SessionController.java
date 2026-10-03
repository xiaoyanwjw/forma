package com.xmut.forma.interfaces.web.business.session;

import com.xmut.forma.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.forma.application.business.session.dto.SessionSummaryDTO;
import com.xmut.forma.application.business.session.dto.SessionTurnDTO;
import com.xmut.forma.application.business.session.query.SessionLatestArtifactQuery;
import com.xmut.forma.application.business.session.query.SessionListQuery;
import com.xmut.forma.application.business.session.query.SessionQueryService;
import com.xmut.forma.application.business.session.query.SessionTurnPageQuery;
import com.xmut.forma.common.page.Page;
import com.xmut.forma.common.response.ApiResponse;
import com.xmut.forma.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SessionQuery 薄控制器：本人近 60 天会话与按逻辑 runId 聚合的回合回放。
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
                sessionQueryService.list(SessionListQuery.builder()
                        .userId(SecuritySupport.requireUserId())
                        .sceneCode(sceneCode)
                        .limit(limit)
                        .build()));
    }

    @GetMapping("/{sessionId}/messages")
    public ApiResponse<Page<SessionTurnDTO>> listMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "nextToken", required = false) String nextToken,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.success(
                sessionQueryService.pageTurns(SessionTurnPageQuery.builder()
                        .userId(SecuritySupport.requireUserId())
                        .sessionId(sessionId)
                        .nextToken(nextToken)
                        .limit(limit)
                        .build()));
    }

    @GetMapping("/{sessionId}/latest-artifact")
    public ApiResponse<HistoryArtifactDetailDTO> latestArtifact(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "artifactType", required = false) String artifactType) {
        return ApiResponse.success(
                sessionQueryService.getLatestArtifact(SessionLatestArtifactQuery.builder()
                                .userId(SecuritySupport.requireUserId())
                                .sessionId(sessionId)
                                .artifactType(artifactType)
                                .build())
                        .orElse(null));
    }
}
