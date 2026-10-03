package com.xmut.forma.application.business.agent.support;

import com.xmut.forma.application.business.agent.dto.GenerationRunContext;
import com.xmut.lims.pi.ai.message.Message;
import com.xmut.lims.pi.agent.session.TurnResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@code streamBilledRun} / resume 管道可变上下文：前置/后置处理器可读写 view 与业务载荷。
 */
public final class BilledRunContext {

    private final GenerationRunContext run;
    private Map<String, Object> projectedView = Collections.emptyMap();
    private Map<String, Object> businessPayload = Collections.emptyMap();
    /** ask_human 续跑选项；非 resume 路径为 null。 */
    private String resumeOptionId;
    /** 落库类型；默认取自 profile，Interceptor 可覆盖。 */
    private String persistAs;
    /** 流式 MESSAGE_UPDATE 累加缓冲（碎片拼成完整助手文本）。 */
    private final StringBuilder assistantTextBuffer = new StringBuilder();
    /** 已校验可用的 assistant 文本候选（如 Listing 策划稿）。 */
    private String assistantTextCandidate;
    /** 最近一次 human_input_required 的 toolCallId。 */
    private String pendingToolCallId;
    /** true = resumeBilledRun 路径。 */
    private boolean afterResume;
    /** handler 已落策划成果，待 {@code settleOnSuspended} 结算当前 hold。 */
    private boolean pendingSettleOnSuspend;
    /** 本轮 Turn 终稿（SUSPENDED/OK 后由管线挂上，供 handler 补捕获）。 */
    private String turnFinalResponse;
    /** 本轮 messages 投影；可空。 */
    private List<Message> turnMessages = Collections.emptyList();

    public BilledRunContext(GenerationRunContext run) {
        this.run = run;
        this.persistAs = run.getProfile().getPersistAs();
    }

    /** 挂上本轮 Turn 结果（通用字段；业务解释留给 Interceptor）。 */
    public void bindTurnResult(TurnResult result) {
        if (result == null) {
            this.turnFinalResponse = null;
            this.turnMessages = Collections.emptyList();
            return;
        }
        this.turnFinalResponse = result.getFinalResponse();
        this.turnMessages = result.getMessages() != null
                ? result.getMessages()
                : Collections.<Message>emptyList();
    }

    public GenerationRunContext getRun() {
        return run;
    }

    public SkillRunProfile getProfile() {
        return run.getProfile();
    }

    public String getResumeOptionId() {
        return resumeOptionId;
    }

    public void setResumeOptionId(String resumeOptionId) {
        this.resumeOptionId = resumeOptionId;
    }

    public String getPersistAs() {
        return persistAs;
    }

    public void setPersistAs(String persistAs) {
        this.persistAs = persistAs;
    }

    public String getAssistantTextBuffer() {
        return assistantTextBuffer.toString();
    }

    public void appendAssistantDelta(String delta) {
        if (delta != null) {
            assistantTextBuffer.append(delta);
        }
    }

    public String getAssistantTextCandidate() {
        return assistantTextCandidate;
    }

    public void setAssistantTextCandidate(String assistantTextCandidate) {
        this.assistantTextCandidate = assistantTextCandidate;
    }

    public String getPendingToolCallId() {
        return pendingToolCallId;
    }

    public void setPendingToolCallId(String pendingToolCallId) {
        this.pendingToolCallId = pendingToolCallId;
    }

    public boolean isAfterResume() {
        return afterResume;
    }

    public void setAfterResume(boolean afterResume) {
        this.afterResume = afterResume;
    }

    public boolean isPendingSettleOnSuspend() {
        return pendingSettleOnSuspend;
    }

    public void setPendingSettleOnSuspend(boolean pendingSettleOnSuspend) {
        this.pendingSettleOnSuspend = pendingSettleOnSuspend;
    }

    public Map<String, Object> getProjectedView() {
        return projectedView;
    }

    public void setProjectedView(Map<String, Object> projectedView) {
        this.projectedView = projectedView != null
                ? projectedView
                : Collections.<String, Object>emptyMap();
    }

    public Map<String, Object> getBusinessPayload() {
        return businessPayload;
    }

    public void setBusinessPayload(Map<String, Object> businessPayload) {
        this.businessPayload = businessPayload != null
                ? businessPayload
                : Collections.<String, Object>emptyMap();
    }

    public String getTurnFinalResponse() {
        return turnFinalResponse;
    }

    public List<Message> getTurnMessages() {
        return turnMessages;
    }
}
