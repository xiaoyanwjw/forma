package com.xmut.forma.extension.tool.view;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

/**
 * 聚合各场景模块注册的 {@link ViewEnricher}；按 skillId 分发。
 */
public final class ViewEnricherComposite {

    private final Map<String, ViewEnricher> bySkillId;

    public ViewEnricherComposite(List<ViewEnricher> enrichers) {
        Map<String, ViewEnricher> map = new LinkedHashMap<String, ViewEnricher>();
        if (enrichers != null) {
            for (ViewEnricher enricher : enrichers) {
                if (enricher == null) {
                    continue;
                }
                String id = enricher.skillId();
                if (!StringUtils.hasText(id)) {
                    throw new IllegalArgumentException("ViewEnricher.skillId required");
                }
                String key = id.trim();
                ViewEnricher previous = map.put(key, enricher);
                if (previous != null) {
                    throw new IllegalStateException("duplicate ViewEnricher for skillId=" + key);
                }
            }
        }
        this.bySkillId = Collections.unmodifiableMap(map);
    }

    public static ViewEnricherComposite empty() {
        return new ViewEnricherComposite(Collections.<ViewEnricher>emptyList());
    }

    public static ViewEnricherComposite of(ViewEnricher... enrichers) {
        Objects.requireNonNull(enrichers, "enrichers");
        return new ViewEnricherComposite(java.util.Arrays.asList(enrichers));
    }

    /**
     * @return 无 enricher 时原样返回 {@code artifact}（null 则空 map）；有则深拷贝后再 enrich
     */
    public Map<String, Object> enrich(String skillId, Map<String, Object> artifact) {
        Map<String, Object> source = artifact == null
                ? new LinkedHashMap<String, Object>()
                : artifact;
        if (!StringUtils.hasText(skillId)) {
            return source;
        }
        ViewEnricher enricher = bySkillId.get(skillId.trim());
        if (enricher == null) {
            return source;
        }
        return enricher.enrich(ViewRenderHelpers.deepCopy(source));
    }

    /** 已注册 skillId 数（测试 / 诊断）。 */
    public int size() {
        return bySkillId.size();
    }
}
