package com.xmut.lims.pi.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tool 启动扫描器。
 * 功能描述：扫描 *.tool.json 得到 ToolManifest 列表。
 */
public final class ClasspathToolBootstrap {

    public static final String DEFAULT_PATTERN = "classpath*:tools/*.tool.json";

    private static final Logger log = LoggerFactory.getLogger(ClasspathToolBootstrap.class);

    private ClasspathToolBootstrap() {}

    public static List<ToolManifest> load() {
        return load(new PathMatchingResourcePatternResolver(), DEFAULT_PATTERN);
    }

    public static List<ToolManifest> load(ResourcePatternResolver resolver) {
        return load(resolver, DEFAULT_PATTERN);
    }

    /**
     * @return 去重后的 Manifest 快照（按 id；同 id 后者跳过）
     */
    public static List<ToolManifest> load(ResourcePatternResolver resolver, String pattern) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver is null");
        }
        String scan = StringUtils.hasText(pattern) ? pattern.trim() : DEFAULT_PATTERN;
        Resource[] resources;
        try {
            resources = resolver.getResources(scan);
        } catch (Exception e) {
            throw new ToolValidationException(
                    "ClasspathToolBootstrap: failed to scan pattern=" + scan, e);
        }
        if (resources == null || resources.length == 0) {
            log.info("ClasspathToolBootstrap: no tool json under {}", scan);
            return Collections.emptyList();
        }
        List<ToolManifest> out = new ArrayList<>();
        Set<String> seenIds = new LinkedHashSet<>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String desc = describe(resource);
            try (InputStream in = resource.getInputStream()) {
                ToolManifest manifest = ToolManifestJsonLoader.load(in);
                if (!seenIds.add(manifest.getId())) {
                    log.warn("ClasspathToolBootstrap: skip duplicate id={} from {}",
                            manifest.getId(), desc);
                    continue;
                }
                out.add(manifest);
                log.info("ClasspathToolBootstrap: loaded id={} level={} from {}",
                        manifest.getId(), manifest.getLevel(), desc);
            } catch (ToolValidationException e) {
                throw e;
            } catch (Exception e) {
                throw new ToolValidationException(
                        "ClasspathToolBootstrap: failed to load " + desc, e);
            }
        }
        return Collections.unmodifiableList(out);
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
