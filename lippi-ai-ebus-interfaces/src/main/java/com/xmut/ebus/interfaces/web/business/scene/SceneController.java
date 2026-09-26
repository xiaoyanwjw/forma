package com.xmut.ebus.interfaces.web.business.scene;

import com.xmut.ebus.application.business.scene.dto.SceneDTO;
import com.xmut.ebus.application.business.scene.query.SceneQueryService;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景画廊列表（仅 GET；近端无公开写 REST）。
 */
@RestController
@RequestMapping("/api/v1/scenes")
@RequiredArgsConstructor
public class SceneController {

    private final SceneQueryService sceneQueryService;

    @GetMapping
    public ApiResponse<List<SceneDTO>> getScenes() {
        SecuritySupport.requireUserId();
        return ApiResponse.success(sceneQueryService.list());
    }
}
