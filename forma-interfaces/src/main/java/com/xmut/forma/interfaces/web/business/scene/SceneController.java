package com.xmut.forma.interfaces.web.business.scene;

import com.xmut.forma.application.business.scene.dto.SceneDTO;
import com.xmut.forma.application.business.scene.dto.SceneSkillCapsuleDTO;
import com.xmut.forma.application.business.scene.query.SceneQueryService;
import com.xmut.forma.common.response.ApiResponse;
import com.xmut.forma.interfaces.security.SecuritySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 场景画廊与工作台快捷栏（仅 GET；近端无公开写 REST）。
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

    /**
     * 工作台胶囊：label + examplePrompt + skillId（不含 skill 正文）。
     */
    @GetMapping("/{sceneCode}/skills")
    public ApiResponse<SceneSkillCapsuleDTO> getSceneSkillCapsules(@PathVariable("sceneCode") String sceneCode) {
        SecuritySupport.requireUserId();
        return ApiResponse.success(sceneQueryService.listSkillCapsules(sceneCode));
    }
}
