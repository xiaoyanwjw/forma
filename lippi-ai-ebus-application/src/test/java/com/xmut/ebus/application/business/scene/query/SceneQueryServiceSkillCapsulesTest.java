package com.xmut.ebus.application.business.scene.query;

import com.xmut.ebus.application.business.scene.dto.SceneSkillCapsuleDTO;
import com.xmut.ebus.application.business.scene.dto.SceneSkillCapsuleItemDTO;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPack;
import com.xmut.ebus.application.business.scene.pack.SceneCapabilityPackLoader;
import com.xmut.ebus.application.business.scene.pack.SceneSkillCapsuleLoader;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.business.scene.repository.SceneRepository;
import com.xmut.lims.pi.agent.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SceneQueryServiceSkillCapsulesTest {

    @Test
    void listSkillCapsulesReturnsItemsFromLoader() {
        SceneRepository scenes = mock(SceneRepository.class);
        SceneCapabilityPackLoader packLoader = mock(SceneCapabilityPackLoader.class);
        SceneSkillCapsuleLoader capsuleLoader = mock(SceneSkillCapsuleLoader.class);
        Skill skill = Skill.builder().id("ecommerce-picklist").description("d").build();
        when(packLoader.load("ecommerce"))
                .thenReturn(new SceneCapabilityPack("ecommerce", Collections.singletonList(skill)));
        when(capsuleLoader.loadFor(anyList())).thenReturn(Collections.singletonList(
                new SceneSkillCapsuleItemDTO("ecommerce-picklist", "选品清单", "示例", 1)));

        SceneQueryService service = new SceneQueryService(scenes, packLoader, capsuleLoader);
        SceneSkillCapsuleDTO dto = service.listSkillCapsules("ecommerce");

        assertThat(dto.getSceneCode()).isEqualTo("ecommerce");
        assertThat(dto.getSkills()).hasSize(1);
        assertThat(dto.getSkills().get(0).getLabel()).isEqualTo("选品清单");
    }

    @Test
    void listSkillCapsulesReturnsEmptyWhenPackUnavailable() {
        SceneRepository scenes = mock(SceneRepository.class);
        SceneCapabilityPackLoader packLoader = mock(SceneCapabilityPackLoader.class);
        SceneSkillCapsuleLoader capsuleLoader = mock(SceneSkillCapsuleLoader.class);
        when(packLoader.load(eq("short_video")))
                .thenThrow(new BusinessException(ErrorCode.PARAM_INVALID, "场景能力暂不可用"));

        SceneQueryService service = new SceneQueryService(scenes, packLoader, capsuleLoader);
        SceneSkillCapsuleDTO dto = service.listSkillCapsules("short_video");

        assertThat(dto.getSceneCode()).isEqualTo("short_video");
        assertThat(dto.getSkills()).isEmpty();
    }
}
