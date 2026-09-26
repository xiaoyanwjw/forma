package com.xmut.lims.pi.agent.tool;

import com.xmut.lims.pi.agent.graph.node.ToolHandler;
import com.xmut.lims.pi.ai.model.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ToolCatalog 默认实现。
 * 功能描述：维护 {@link ToolDefinition} 目录与 {@link ToolBinding}；未登记工具不可执行。
 */
public final class InMemoryToolCatalog implements ToolCatalog {

    private static final Logger log = LoggerFactory.getLogger(InMemoryToolCatalog.class);

    private final Map<String, ToolBinding> byName;

    public InMemoryToolCatalog(List<Tool> tools) {
        Map<String, ToolBinding> map = new LinkedHashMap<>();
        if (tools != null) {
            for (Tool tool : tools) {
                if (tool == null) {
                    continue;
                }
                putUnique(map, tool.getBinding());
            }
        }
        this.byName = Collections.unmodifiableMap(map);
    }

    public static InMemoryToolCatalog of(List<Tool> tools) {
        return new InMemoryToolCatalog(tools);
    }

    public static InMemoryToolCatalog ofBindings(List<ToolBinding> bindings) {
        Map<String, ToolBinding> map = new LinkedHashMap<>();
        if (bindings != null) {
            for (ToolBinding b : bindings) {
                if (b == null) {
                    continue;
                }
                putUnique(map, b);
            }
        }
        return new InMemoryToolCatalog(map);
    }

    private InMemoryToolCatalog(Map<String, ToolBinding> byName) {
        this.byName = byName;
    }

    public static InMemoryToolCatalog empty() {
        return new InMemoryToolCatalog(Collections.<Tool>emptyList());
    }

    /**
     * Manifest 扫盘 + 代码 Binding 合并。
     *
     * <ul>
     *   <li>coded：Handler 来源（含 {@link ToolHandlerAutoBinder} 产物）；同 id 重复拒绝</li>
     *   <li>scanned：覆盖同 id 的 Manifest，保留已有 Handler；无 coded 则 Handler=null</li>
     * </ul>
     */
    public static InMemoryToolCatalog merge(List<ToolDefinition> scanned, List<ToolBinding> coded) {
        Map<String, ToolBinding> map = new LinkedHashMap<>();
        if (coded != null) {
            for (ToolBinding b : coded) {
                if (b == null) {
                    continue;
                }
                putUnique(map, b);
            }
        }
        if (scanned != null) {
            for (ToolDefinition m : scanned) {
                if (m == null) {
                    continue;
                }
                ToolBinding existing = map.get(m.getId());
                if (existing != null) {
                    map.put(m.getId(), ToolBinding.of(m, existing.getHandler()));
                } else {
                    map.put(m.getId(), ToolBinding.of(m, null));
                }
            }
        }
        for (ToolBinding b : map.values()) {
            if (b.getHandler() != null && scannedManifestMissing(scanned, b.getId())) {
                log.debug("ToolCatalog: handler id={} has no scanned ToolDefinition; using stub",
                        b.getId());
            }
        }
        return new InMemoryToolCatalog(Collections.unmodifiableMap(map));
    }

    private static boolean scannedManifestMissing(List<ToolDefinition> scanned, String id) {
        if (scanned == null || id == null) {
            return true;
        }
        for (ToolDefinition m : scanned) {
            if (m != null && id.equals(m.getId())) {
                return false;
            }
        }
        return true;
    }

    private static void putUnique(Map<String, ToolBinding> map, ToolBinding binding) {
        String id = binding.getId();
        if (map.containsKey(id)) {
            throw new IllegalArgumentException("duplicate tool registration: " + id);
        }
        map.put(id, binding);
    }

    @Override
    public Optional<ToolDefinition> get(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        ToolBinding b = byName.get(id.trim());
        return b != null ? Optional.of(b.getDefinition()) : Optional.empty();
    }

    @Override
    public Optional<ToolDefinition> resolve(String id) {
        return get(id);
    }

    @Override
    public List<ToolDefinition> all() {
        List<ToolDefinition> out = new ArrayList<>(byName.size());
        for (ToolBinding b : byName.values()) {
            out.add(b.getDefinition());
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public boolean isRegistered(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return false;
        }
        return byName.containsKey(toolName.trim());
    }

    @Override
    public List<ToolSchema> schemasForModel() {
        return schemasForModel(null);
    }

    @Override
    public List<ToolSchema> schemasForModel(Collection<String> whitelist) {
        if (whitelist != null && whitelist.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.Set<String> allow = null;
        if (whitelist != null) {
            allow = new java.util.LinkedHashSet<>();
            for (String name : whitelist) {
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                String trimmed = name.trim();
                allow.add(trimmed);
                if (!byName.containsKey(trimmed)) {
                    log.debug("ToolCatalog: whitelist name not registered, ignored: {}", trimmed);
                }
            }
            if (allow.isEmpty()) {
                return Collections.emptyList();
            }
        }
        List<ToolSchema> schemas = new ArrayList<>();
        for (ToolBinding b : byName.values()) {
            if (allow != null && !allow.contains(b.getId())) {
                continue;
            }
            schemas.add(b.getDefinition().schemaOrDefault());
        }
        return Collections.unmodifiableList(schemas);
    }

    @Override
    public String textForModel() {
        return textForModel(null);
    }

    @Override
    public String textForModel(Collection<String> whitelist) {
        if (whitelist != null && whitelist.isEmpty()) {
            return null;
        }
        java.util.Set<String> allow = null;
        if (whitelist != null) {
            allow = new java.util.LinkedHashSet<>();
            for (String name : whitelist) {
                if (StringUtils.hasText(name)) {
                    allow.add(name.trim());
                }
            }
            if (allow.isEmpty()) {
                return null;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (ToolBinding b : byName.values()) {
            if (allow != null && !allow.contains(b.getId())) {
                continue;
            }
            String t = b.getDefinition().getText();
            if (!StringUtils.hasText(t)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(t.trim());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    @Override
    public Optional<ToolHandler> handlerOf(String toolName) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return Optional.empty();
        }
        ToolBinding b = byName.get(toolName.trim());
        if (b == null || b.getHandler() == null) {
            return Optional.empty();
        }
        return Optional.of(b.getHandler());
    }

    /** 供 {@link com.xmut.lims.pi.agent.graph.node.ToolNode} 装配。 */
    public Map<String, ToolHandler> handlers() {
        Map<String, ToolHandler> handlers = new LinkedHashMap<>();
        for (ToolBinding b : byName.values()) {
            if (b.getHandler() != null) {
                handlers.put(b.getId(), b.getHandler());
            }
        }
        return Collections.unmodifiableMap(handlers);
    }
}
