package com.xmut.forma.application.business.scene.pack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 扫描 classpath 下 {@code scenes/<sceneCode>/pack.yaml} 注册场景元数据。
 */
@Component
public class ClasspathSceneMetaCatalog implements SceneMetaCatalog {

    public static final String DEFAULT_PATTERN = "classpath*:scenes/*/pack.yaml";

    private static final Logger log = LoggerFactory.getLogger(ClasspathSceneMetaCatalog.class);

    private final Map<String, SceneMeta> bySceneCode;

    public ClasspathSceneMetaCatalog() {
        this(new PathMatchingResourcePatternResolver(), DEFAULT_PATTERN);
    }

    ClasspathSceneMetaCatalog(ResourcePatternResolver resolver, String pattern) {
        this.bySceneCode = Collections.unmodifiableMap(loadAll(resolver, pattern));
    }

    @Override
    public Optional<SceneMeta> find(String sceneCode) {
        if (!StringUtils.hasText(sceneCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySceneCode.get(sceneCode.trim()));
    }

    static Map<String, SceneMeta> loadAll(ResourcePatternResolver resolver, String pattern) {
        String scan = StringUtils.hasText(pattern) ? pattern.trim() : DEFAULT_PATTERN;
        Resource[] resources;
        try {
            resources = resolver.getResources(scan);
        } catch (Exception ex) {
            throw new IllegalStateException("SceneMeta: failed to scan " + scan, ex);
        }
        Map<String, SceneMeta> out = new LinkedHashMap<String, SceneMeta>();
        if (resources == null || resources.length == 0) {
            log.info("SceneMeta: no pack.yaml under {}", scan);
            return out;
        }
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }
            String sceneCode = sceneCodeFromPath(resource);
            if (!StringUtils.hasText(sceneCode)) {
                log.warn("SceneMeta: skip {}, cannot derive sceneCode", describe(resource));
                continue;
            }
            if (out.containsKey(sceneCode)) {
                log.warn("SceneMeta: duplicate sceneCode={} from {}, keep first",
                        sceneCode, describe(resource));
                continue;
            }
            try {
                out.put(sceneCode, parse(resource, sceneCode));
                log.info("SceneMeta: registered scene={} from {}", sceneCode, describe(resource));
            } catch (RuntimeException ex) {
                throw new IllegalStateException(
                        "SceneMeta: failed to load " + describe(resource), ex);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static SceneMeta parse(Resource resource, String sceneCode) {
        try (InputStream in = resource.getInputStream()) {
            Object loaded = new Yaml().load(in);
            if (loaded == null) {
                return new SceneMeta(sceneCode, Collections.<String>emptyList(), null);
            }
            if (!(loaded instanceof Map)) {
                throw new IllegalArgumentException("pack.yaml root must be a map");
            }
            Map<String, Object> doc = (Map<String, Object>) loaded;
            List<String> required = toStringList(doc.get("requiredSkills"));
            String defaultSkill = doc.get("defaultSkill") == null
                    ? null
                    : String.valueOf(doc.get("defaultSkill")).trim();
            return new SceneMeta(sceneCode, required, defaultSkill);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private static List<String> toStringList(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        if (!(raw instanceof List)) {
            throw new IllegalArgumentException("requiredSkills must be a list");
        }
        List<?> list = (List<?>) raw;
        List<String> out = new ArrayList<String>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item).trim());
            }
        }
        return out;
    }

    static String sceneCodeFromPath(Resource resource) {
        String relative = relativeClasspathPath(resource);
        if (!StringUtils.hasText(relative)) {
            return null;
        }
        String[] parts = relative.replace('\\', '/').split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("scenes".equals(parts[i]) && StringUtils.hasText(parts[i + 1])) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private static String relativeClasspathPath(Resource resource) {
        try {
            String desc = resource.getURI().toString().replace('\\', '/');
            int idx = desc.lastIndexOf("/scenes/");
            if (idx >= 0) {
                return desc.substring(idx + 1);
            }
            String filename = resource.getFilename();
            return filename;
        } catch (Exception ex) {
            return resource.getDescription();
        }
    }

    private static String describe(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (Exception ex) {
            return resource.getDescription();
        }
    }
}
