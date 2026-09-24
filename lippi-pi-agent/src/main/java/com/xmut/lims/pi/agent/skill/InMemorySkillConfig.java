package com.xmut.lims.pi.agent.skill;

import com.xmut.lims.pi.agent.tool.ToolLevel;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 {@link SkillConfig}（Story 51-9）。
 *
 * <p>{@link #resolve(String)}：取该 id 最近一次成功入册更新的「当前」指针（注册序覆盖，非 semver 比较）。
 *
 * <p>生产默认 {@code allowRuntimeMutation=false}：对外 {@link #register}/{@link #unregister}/
 * {@link #replace} 拒绝；{@link #registerBootstrap} 供启动装载。
 */
public final class InMemorySkillConfig implements SkillConfig {

    private final SkillConfigProperties properties;
    /** key = id + '\0' + version */
    private final ConcurrentHashMap<String, SkillManifest> byIdVersion = new ConcurrentHashMap<>();
    /** id → 当前指针（最近入册） */
    private final ConcurrentHashMap<String, SkillManifest> currentById = new ConcurrentHashMap<>();
    /** 注册序快照源 */
    private final Object lock = new Object();
    private final LinkedHashMap<String, SkillManifest> insertionOrder = new LinkedHashMap<>();
    /** 启动窗关闭后拒绝 registerBootstrap */
    private volatile boolean bootstrapSealed = false;

    public InMemorySkillConfig() {
        this(SkillConfigProperties.defaults());
    }

    public InMemorySkillConfig(SkillConfigProperties properties) {
        this.properties = properties != null ? properties : SkillConfigProperties.defaults();
    }

    public SkillConfigProperties properties() {
        return properties;
    }

    @Override
    public void register(SkillManifest manifest) {
        assertRuntimeMutationAllowed("register");
        doRegister(manifest, false);
    }

    @Override
    public void registerBootstrap(SkillManifest manifest) {
        if (bootstrapSealed) {
            throw new SkillValidationException(
                    "skill bootstrap window closed; use register with allow-runtime-mutation=true");
        }
        doRegister(manifest, false);
    }

    @Override
    public void sealBootstrap() {
        bootstrapSealed = true;
    }

    @Override
    public void replace(SkillManifest manifest) {
        assertRuntimeMutationAllowed("replace");
        doRegister(manifest, true);
    }

    @Override
    public void unregister(String id, String version) {
        assertRuntimeMutationAllowed("unregister");
        if (!StringUtils.hasText(id) || !StringUtils.hasText(version)) {
            throw new SkillValidationException("unregister requires non-blank id and version");
        }
        String key = versionKey(id.trim(), version.trim());
        synchronized (lock) {
            SkillManifest removed = byIdVersion.remove(key);
            if (removed == null) {
                return;
            }
            insertionOrder.remove(key);
            SkillManifest current = currentById.get(removed.getId());
            if (current != null && key.equals(versionKey(current.getId(), current.getVersion()))) {
                SkillManifest fallback = null;
                for (SkillManifest m : insertionOrder.values()) {
                    if (removed.getId().equals(m.getId())) {
                        fallback = m;
                    }
                }
                if (fallback != null) {
                    currentById.put(removed.getId(), fallback);
                } else {
                    currentById.remove(removed.getId());
                }
            }
        }
    }

    @Override
    public Optional<SkillManifest> get(String id, String version) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(version)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byIdVersion.get(versionKey(id.trim(), version.trim())));
    }

    @Override
    public Optional<SkillManifest> resolve(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentById.get(id.trim()));
    }

    @Override
    public List<SkillManifest> manifests() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(insertionOrder.values()));
        }
    }

    private void doRegister(SkillManifest manifest, boolean allowReplace) {
        validate(manifest);
        String id = manifest.getId().trim();
        String version = manifest.getVersion().trim();
        String key = versionKey(id, version);
        SkillManifest normalized = manifest.toBuilder()
                .id(id)
                .version(version)
                .toolWhitelist(normalizeWhitelist(manifest.getToolWhitelist()))
                .build();

        synchronized (lock) {
            if (!allowReplace && byIdVersion.containsKey(key)) {
                throw new SkillValidationException(
                        "duplicate skill id+version refused: " + id + "@" + version);
            }
            byIdVersion.put(key, normalized);
            insertionOrder.put(key, normalized);
            currentById.put(id, normalized);
        }
    }

    private void assertRuntimeMutationAllowed(String op) {
        if (!properties.isAllowRuntimeMutation()) {
            throw new SkillValidationException(
                    "runtime skill mutation disabled (" + op + "); "
                            + "set pi.skills.allow-runtime-mutation=true or use registerBootstrap");
        }
    }

    static void validate(SkillManifest manifest) {
        if (manifest == null) {
            throw new SkillValidationException("manifest required");
        }
        String id = manifest.getId();
        if (!StringUtils.hasText(id) || !id.equals(id.trim()) || containsWhitespace(id)) {
            throw new SkillValidationException("id required and must not contain whitespace");
        }
        String version = manifest.getVersion();
        if (!StringUtils.hasText(version) || !StringUtils.hasText(version.trim())) {
            throw new SkillValidationException("version required");
        }

        boolean hasPrompt = StringUtils.hasText(manifest.getSkillsPrompt());
        boolean hasRef = StringUtils.hasText(manifest.getPromptRef());
        if (!hasPrompt && !hasRef) {
            throw new SkillValidationException("skillsPrompt or promptRef required");
        }

        if (manifest.getToolWhitelist() == null) {
            throw new SkillValidationException("toolWhitelist required (may be empty)");
        }
        for (String name : manifest.getToolWhitelist()) {
            if (!StringUtils.hasText(name) || !StringUtils.hasText(name.trim())) {
                throw new SkillValidationException("toolWhitelist entries must be non-blank");
            }
        }

        if (manifest.getMaxToolLevel() == null) {
            throw new SkillValidationException("maxToolLevel required");
        }
        if (manifest.getMaxToolLevel() == ToolLevel.FORBIDDEN) {
            throw new SkillValidationException("maxToolLevel FORBIDDEN is not allowed for a skill");
        }

        if (manifest.getGraphTopology() == null) {
            throw new SkillValidationException("graphTopology required");
        }
    }

    private static List<String> normalizeWhitelist(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String n : raw) {
            out.add(n.trim());
        }
        return Collections.unmodifiableList(out);
    }

    private static String versionKey(String id, String version) {
        return id + '\0' + version;
    }

    private static boolean containsWhitespace(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
