package com.xmut.lims.pi.agent.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.InputStream;

/**
 * 启动扫描：预设目录 {@code *.skill.json} → {@link SkillConfig#registerBootstrap}。
 *
 * <p>默认 pattern：{@code classpath*:skills/*.skill.json}（含 hermes JAR 内置 Skill，
 * 如同名 id+version 多份则跳过重复）。
 * 不参与 Turn；运行时只读 Config + 极薄 bind。
 */
public final class ClasspathSkillBootstrap {

    public static final String DEFAULT_PATTERN = "classpath*:skills/*.skill.json";

    private static final Logger log = LoggerFactory.getLogger(ClasspathSkillBootstrap.class);

    private ClasspathSkillBootstrap() {}

    /** 使用默认 ClassLoader resolver + {@link #DEFAULT_PATTERN}。 */
    public static int load(SkillConfig skillConfig) {
        return load(skillConfig, new PathMatchingResourcePatternResolver(), DEFAULT_PATTERN);
    }

    public static int load(SkillConfig skillConfig, ResourcePatternResolver resolver) {
        return load(skillConfig, resolver, DEFAULT_PATTERN);
    }

    /**
     * @return 成功 registerBootstrap 的数量
     */
    public static int load(SkillConfig skillConfig,
                           ResourcePatternResolver resolver,
                           String pattern) {
        if (skillConfig == null) {
            throw new IllegalArgumentException("skillConfig is null");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("resolver is null");
        }
        String scan = StringUtils.hasText(pattern) ? pattern.trim() : DEFAULT_PATTERN;
        Resource[] resources;
        try {
            resources = resolver.getResources(scan);
        } catch (Exception e) {
            throw new SkillValidationException(
                    "ClasspathSkillBootstrap: failed to scan pattern=" + scan, e);
        }
        if (resources.length == 0) {
            log.info("ClasspathSkillBootstrap: no skill json under {}", scan);
            return 0;
        }
        int count = 0;
        java.util.Set<String> seenKeys = new java.util.LinkedHashSet<>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String desc = describe(resource);
            try (InputStream in = resource.getInputStream()) {
                SkillManifest manifest = SkillManifestJsonLoader.load(in);
                String key = manifest.getId() + "@" + manifest.getVersion();
                if (!seenKeys.add(key)) {
                    log.warn("ClasspathSkillBootstrap: skip duplicate id+version {} from {}", key, desc);
                    continue;
                }

                skillConfig.registerBootstrap(manifest);
                count++;
                log.info("ClasspathSkillBootstrap: registered id={} version={} from {}", manifest.getId(), manifest.getVersion(), desc);
            } catch (SkillValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new SkillValidationException(
                        "ClasspathSkillBootstrap: failed to load " + desc, e);
            }
        }
        return count;
    }

    private static String describe(Resource resource) {
        try {
            String url = resource.getURL().toString();
            if (StringUtils.hasText(url)) {
                return url;
            }
        } catch (Exception ignored) {
            // fall through
        }
        String filename = resource.getFilename();
        return StringUtils.hasText(filename) ? filename : resource.toString();
    }
}
