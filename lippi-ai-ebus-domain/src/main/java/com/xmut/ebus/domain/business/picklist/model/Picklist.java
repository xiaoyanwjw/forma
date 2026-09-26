package com.xmut.ebus.domain.business.picklist.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 选品清单聚合根（PicklistArtifact）：必含 templateId + 候选条目。
 */
public class Picklist {

    private String id;
    private String userId;
    private String runId;
    private String templateId;
    private String disclaimer;
    private String assumptions;
    private List<PicklistItem> items = new ArrayList<PicklistItem>();
    private Instant createdAt;
    private Instant updatedAt;

    public static Picklist create(String id,
                                  String userId,
                                  String runId,
                                  String templateId,
                                  String disclaimer,
                                  String assumptions,
                                  List<PicklistItem> items,
                                  Instant now) {
        Picklist picklist = new Picklist();
        picklist.id = id;
        picklist.userId = userId;
        picklist.runId = runId;
        picklist.templateId = templateId;
        picklist.disclaimer = disclaimer;
        picklist.assumptions = assumptions;
        picklist.items = items == null
                ? new ArrayList<PicklistItem>()
                : new ArrayList<PicklistItem>(items);
        picklist.createdAt = now;
        picklist.updatedAt = now;
        return picklist;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }

    public String getAssumptions() {
        return assumptions;
    }

    public void setAssumptions(String assumptions) {
        this.assumptions = assumptions;
    }

    public List<PicklistItem> getItems() {
        return items == null ? Collections.<PicklistItem>emptyList() : items;
    }

    public void setItems(List<PicklistItem> items) {
        this.items = items == null ? new ArrayList<PicklistItem>() : new ArrayList<PicklistItem>(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
