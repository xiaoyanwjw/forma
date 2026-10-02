package com.xmut.ebus.application.business.session.support;

import com.xmut.ebus.application.business.session.dto.SessionMessageDTO;
import com.xmut.ebus.application.business.session.dto.SessionToolCallDTO;
import com.xmut.ebus.application.business.session.dto.SessionTurnDTO;
import com.xmut.ebus.common.util.StringUtils;
import com.xmut.ebus.domain.business.agent.model.PiMessageDTO;
import com.xmut.ebus.domain.business.agent.model.PiToolCallRef;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把扁平 session entry 按逻辑 runId 聚成 {@link SessionTurnDTO}。
 * <p>
 * HITL 落库键为 {@code runId:suspend} / {@code runId:resume}，去后缀后同一逻辑 run。
 * 无 runId 时按「非 HITL user」边界切分。
 */
public final class SessionTurnAssembler {

    private static final Pattern HITL_OPTION = Pattern.compile(
            "\"optionId\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private SessionTurnAssembler() {
    }

    /** 去掉 :suspend / :resume 后缀。 */
    public static String logicalRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return null;
        }
        String raw = runId.trim();
        if (raw.regionMatches(true, raw.length() - 8, ":suspend", 0, 8)) {
            return raw.substring(0, raw.length() - 8);
        }
        if (raw.regionMatches(true, raw.length() - 7, ":resume", 0, 7)) {
            return raw.substring(0, raw.length() - 7);
        }
        return raw;
    }

    public static boolean isHitlOptionUserContent(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String body = unwrapJsonCandidate(content.trim());
        if (!body.startsWith("{")) {
            return false;
        }
        Matcher m = HITL_OPTION.matcher(body.length() > 240 ? body.substring(0, 240) : body);
        return m.find() && StringUtils.hasText(m.group(1));
    }

    /**
     * @param messages 已按 seq 升序的回放行
     * @return 按时间升序的 turns
     */
    public static List<SessionTurnDTO> assemble(List<PiMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClusterRow> rows = new ArrayList<ClusterRow>();
        int anon = 0;
        String lastCluster = null;
        for (PiMessageDTO message : messages) {
            if (message == null || !StringUtils.hasText(message.getRole())) {
                continue;
            }
            String role = message.getRole().toLowerCase(Locale.ROOT);
            String content = message.getContent() == null ? "" : message.getContent().trim();
            String logic = logicalRunId(message.getRunId());
            String clusterKey;
            if (StringUtils.hasText(logic)) {
                clusterKey = "run:" + logic;
                lastCluster = clusterKey;
            } else if ("user".equals(role) && !isHitlOptionUserContent(content)) {
                anon++;
                clusterKey = "user:" + anon;
                lastCluster = clusterKey;
            } else if (StringUtils.hasText(lastCluster)) {
                clusterKey = lastCluster;
            } else {
                anon++;
                clusterKey = "user:" + anon;
                lastCluster = clusterKey;
            }
            rows.add(new ClusterRow(clusterKey, logic, toSessionMessage(message)));
        }

        List<SessionTurnDTO> turns = new ArrayList<SessionTurnDTO>();
        int i = 0;
        while (i < rows.size()) {
            String key = rows.get(i).clusterKey;
            List<SessionMessageDTO> bucket = new ArrayList<SessionMessageDTO>();
            String runId = null;
            while (i < rows.size() && key.equals(rows.get(i).clusterKey)) {
                ClusterRow row = rows.get(i);
                bucket.add(row.message);
                if (runId == null && StringUtils.hasText(row.logicalRunId)) {
                    runId = row.logicalRunId;
                }
                i++;
            }
            turns.add(toTurn(runId, bucket));
        }
        return turns;
    }

    private static SessionTurnDTO toTurn(String runId, List<SessionMessageDTO> messages) {
        String userPrompt = null;
        Instant at = null;
        for (SessionMessageDTO message : messages) {
            if (message == null) {
                continue;
            }
            if ("user".equalsIgnoreCase(message.getRole())
                    && !isHitlOptionUserContent(message.getContent())) {
                userPrompt = message.getContent();
                at = message.getCreatedAt();
                break;
            }
        }
        if (at == null && !messages.isEmpty()) {
            SessionMessageDTO last = messages.get(messages.size() - 1);
            if (last != null) {
                at = last.getCreatedAt();
            }
        }
        return new SessionTurnDTO(runId, at, userPrompt, messages);
    }

    private static SessionMessageDTO toSessionMessage(PiMessageDTO message) {
        String role = message.getRole().toLowerCase(Locale.ROOT);
        List<SessionToolCallDTO> toolCalls = Collections.emptyList();
        if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
            List<SessionToolCallDTO> mapped = new ArrayList<SessionToolCallDTO>();
            for (PiToolCallRef ref : message.getToolCalls()) {
                if (ref == null) {
                    continue;
                }
                mapped.add(new SessionToolCallDTO(ref.getId(), ref.getToolName()));
            }
            toolCalls = mapped;
        }
        return new SessionMessageDTO(
                role,
                message.getContent(),
                message.getCreatedAt(),
                Long.valueOf(message.getSeq()),
                message.getToolCallId(),
                toolCalls,
                message.getRunId());
    }

    private static String unwrapJsonCandidate(String content) {
        String t = content.trim();
        if (t.regionMatches(true, 0, "```json", 0, 7)) {
            t = t.substring(7).trim();
        } else if (t.startsWith("```")) {
            t = t.substring(3).trim();
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3).trim();
        }
        return t;
    }

    private static final class ClusterRow {
        private final String clusterKey;
        private final String logicalRunId;
        private final SessionMessageDTO message;

        private ClusterRow(String clusterKey, String logicalRunId, SessionMessageDTO message) {
            this.clusterKey = clusterKey;
            this.logicalRunId = logicalRunId;
            this.message = message;
        }
    }
}
