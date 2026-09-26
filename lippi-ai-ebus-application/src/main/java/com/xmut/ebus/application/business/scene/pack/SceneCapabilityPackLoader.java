package com.xmut.ebus.application.business.scene.pack;

import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.lims.pi.agent.skill.SkillManifest;
import com.xmut.lims.pi.agent.skill.SkillManifestJsonLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 按 sceneCode 从 {@code classpath*:scenes/{sceneCode}/*.skill.json} 加载能力包。
 * <p>
 * 单一加载入口；缺包 / 未知码抛人话 {@link #MSG_PACK_UNAVAILABLE}，不调用模型。
 */
@Slf4j
@Component
public class SceneCapabilityPackLoader {

    public static final String MSG_PACK_UNAVAILABLE = "场景能力暂不可用";

    /** 空跑默认注入的 skill（3.4/3.6 再按意图选）。 */
    public static final String DEFAULT_EMPTY_RUN_SKILL_ID = "ecommerce.picklist";

    public static final String SKILL_PICKLIST = "ecommerce.picklist";
    public static final String SKILL_SKULIST = "ecommerce.skulist";

    static final String SCENE_ECOMMERCE = "ecommerce";

    private static final Set<String> ECOMMERCE_REQUIRED_SKILLS =
            Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
                    SKILL_PICKLIST, SKILL_SKULIST)));

    private final ResourcePatternResolver resolver;

    public SceneCapabilityPackLoader() {
        this(new PathMatchingResourcePatternResolver());
    }

    public SceneCapabilityPackLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver != null ? resolver : new PathMatchingResourcePatternResolver();
    }

    /**
     * @throws BusinessException 未知码、缺资源或电商包缺双 skill
     */
    public SceneCapabilityPack load(String sceneCode) {
        String code = StringUtils.requireHasText(sceneCode, MSG_PACK_UNAVAILABLE).trim();
        String pattern = "classpath*:scenes/" + code + "/*.skill.json";
        Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (Exception ex) {
            log.warn("SceneCapabilityPackLoader: scan failed sceneCode={} pattern={}", code, pattern, ex);
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
        }

        List<SkillManifest> skills = new ArrayList<SkillManifest>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                skills.add(SkillManifestJsonLoader.load(in));
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                log.warn("SceneCapabilityPackLoader: failed to parse {} for sceneCode={}",
                        describe(resource), code, ex);
                throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
            }
        }

        if (skills.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
        }

        SceneCapabilityPack pack = new SceneCapabilityPack(code, skills);
        validatePack(pack);
        return pack;
    }

    private static void validatePack(SceneCapabilityPack pack) {
        if (SCENE_ECOMMERCE.equals(pack.getSceneCode())) {
            for (String required : ECOMMERCE_REQUIRED_SKILLS) {
                if (!pack.hasSkill(required)) {
                    throw new BusinessException(ErrorCode.PARAM_INVALID, MSG_PACK_UNAVAILABLE);
                }
            }
        }
    }

    private static String describe(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (Exception ignored) {
            String name = resource.getFilename();
            return name != null ? name : resource.toString();
        }
    }
}
