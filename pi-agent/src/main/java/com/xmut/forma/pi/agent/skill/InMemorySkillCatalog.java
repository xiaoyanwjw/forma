package com.xmut.forma.pi.agent.skill;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内 SkillCatalog 实现。
 * 功能描述：按 id 保存 Skill 目录并提供 resolve/register。
 * 关键设计：resolve 取该 id 最近入册指针；启动用 registerBootstrap。
 */
public final class InMemorySkillCatalog implements SkillCatalog {

    private final SkillCatalogProperties properties;
    /** id → 当前指针（最近入册） */
    private final ConcurrentHashMap<String, Skill> currentById = new ConcurrentHashMap<>();
    /** 注册序快照源，key = id */
    private final Object lock = new Object();
    private final LinkedHashMap<String, Skill> insertionOrder = new LinkedHashMap<>();
    /** 启动窗关闭后拒绝 registerBootstrap */
    private volatile boolean bootstrapSealed = false;

    public InMemorySkillCatalog() {
        this(SkillCatalogProperties.defaults());
    }

    public InMemorySkillCatalog(SkillCatalogProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public SkillCatalogProperties properties() {
        return properties;
    }

    @Override
    public void register(Skill skill) {
        assertRuntimeMutationAllowed("register");
        doRegister(skill, false);
    }

    @Override
    public void registerBootstrap(Skill skill) {
        if (bootstrapSealed) {
            throw new SkillValidationException(
                    "skill bootstrap window closed; use register with allow-runtime-mutation=true");
        }
        doRegister(skill, false);
    }

    @Override
    public void sealBootstrap() {
        bootstrapSealed = true;
    }

    @Override
    public void replace(Skill skill) {
        assertRuntimeMutationAllowed("replace");
        doRegister(skill, true);
    }

    @Override
    public void unregister(String id) {
        assertRuntimeMutationAllowed("unregister");
        if (!StringUtils.hasText(id)) {
            throw new SkillValidationException("unregister requires non-blank id");
        }
        String key = id.trim();
        synchronized (lock) {
            insertionOrder.remove(key);
            currentById.remove(key);
        }
    }

    @Override
    public Optional<Skill> get(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentById.get(id.trim()));
    }

    @Override
    public Optional<Skill> resolve(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        return Optional.ofNullable(currentById.get(id.trim()));
    }

    @Override
    public List<Skill> all() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(insertionOrder.values()));
        }
    }

    @Override
    public List<Skill> listByScene(String sceneCode) {
        if (!StringUtils.hasText(sceneCode)) {
            return Collections.emptyList();
        }
        String code = sceneCode.trim();
        List<Skill> out = new ArrayList<Skill>();
        for (Skill m : all()) {
            if (m != null && code.equals(m.getSceneCode())) {
                out.add(m);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private void doRegister(Skill skill, boolean allowReplace) {
        validate(skill);
        String id = skill.getId().trim();
        Skill normalized = skill.toBuilder()
                .id(id)
                .allowedTools(normalizeAllowedTools(skill.getAllowedTools()))
                .build();

        synchronized (lock) {
            if (!allowReplace && currentById.containsKey(id)) {
                throw new SkillValidationException("duplicate skill id refused: " + id);
            }
            insertionOrder.put(id, normalized);
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

    static void validate(Skill skill) {
        if (skill == null) {
            throw new SkillValidationException("skill required");
        }
        String id = skill.getId();
        if (!StringUtils.hasText(id) || !id.equals(id.trim()) || containsWhitespace(id)) {
            throw new SkillValidationException("id required and must not contain whitespace");
        }
        if (!StringUtils.hasText(skill.getDescription())) {
            throw new SkillValidationException("description required");
        }
        if (!StringUtils.hasText(skill.getPromptRef())) {
            throw new SkillValidationException("promptRef required");
        }

        if (skill.getAllowedTools() == null) {
            throw new SkillValidationException("allowedTools required (may be empty)");
        }
        for (String name : skill.getAllowedTools()) {
            if (!StringUtils.hasText(name) || !StringUtils.hasText(name.trim())) {
                throw new SkillValidationException("allowedTools entries must be non-blank");
            }
        }
    }

    private static List<String> normalizeAllowedTools(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String n : raw) {
            out.add(n.trim());
        }
        return Collections.unmodifiableList(out);
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
