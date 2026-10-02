package com.xmut.ebus.interfaces.web.business.history;

import com.xmut.ebus.application.business.history.dto.HistoryArtifactDetailDTO;
import com.xmut.ebus.application.business.history.dto.HistoryArtifactSummaryDTO;
import com.xmut.ebus.application.business.history.query.HistoryArtifactQuery;
import com.xmut.ebus.application.business.history.query.HistoryListQuery;
import com.xmut.ebus.application.business.history.query.HistoryQueryService;
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
 * HistoryQuery 薄控制器：本人近 60 天成果只读。
 */
@RestController
@RequestMapping("/api/v1/history/artifacts")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryQueryService historyQueryService;

    @GetMapping
    public ApiResponse<List<HistoryArtifactSummaryDTO>> list(
            @RequestParam(value = "sceneCode", required = false) String sceneCode) {
        return ApiResponse.success(
                historyQueryService.list(HistoryListQuery.builder()
                        .userId(SecuritySupport.requireUserId())
                        .sceneCode(sceneCode)
                        .build()));
    }

    @GetMapping("/{id}")
    public ApiResponse<HistoryArtifactDetailDTO> getArtifact(@PathVariable("id") String id) {
        return ApiResponse.success(
                historyQueryService.findById(HistoryArtifactQuery.builder()
                        .userId(SecuritySupport.requireUserId())
                        .artifactId(id)
                        .build()));
    }
}
