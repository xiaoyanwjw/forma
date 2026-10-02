package com.xmut.ebus.application.business.session.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话回放的一轮任务（按逻辑 runId 聚合；含 listing 策划+执行）。
 */
public class SessionTurnDTO {

    /** 逻辑 runId（已去掉 :suspend / :resume）；无 runId 聚类时可为 null */
    private String runId;
    /** 本轮展示时间（优先用户消息，否则末条） */
    private Instant at;
    /** 首条非 HITL 用户文案；确认回执不算 */
    private String userPrompt;
    /** 本轮全部回放行（含 tool / HITL 回执），按 seq 升序 */
    private List<SessionMessageDTO> messages = Collections.emptyList();

    public SessionTurnDTO() {
    }

    public SessionTurnDTO(String runId, Instant at, String userPrompt, List<SessionMessageDTO> messages) {
        this.runId = runId;
        this.at = at;
        this.userPrompt = userPrompt;
        setMessages(messages);
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public Instant getAt() {
        return at;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }

    public List<SessionMessageDTO> getMessages() {
        return messages;
    }

    public void setMessages(List<SessionMessageDTO> messages) {
        this.messages = messages == null || messages.isEmpty()
                ? Collections.<SessionMessageDTO>emptyList()
                : Collections.unmodifiableList(new ArrayList<SessionMessageDTO>(messages));
    }
}
