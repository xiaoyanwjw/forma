package com.xmut.ebus.domain.business.picklist.model;

import java.time.Instant;

/**
 * 选品清单候选条目（子实体）。
 */
public class PicklistItem {

    private String id;
    private String picklistId;
    private int sortOrder;
    private String title;
    private String priceBand;
    private String reason;
    private String differentiation;
    private String demand;
    private String competition;
    private String margin;
    private String risk;
    private Instant createdAt;

    public static PicklistItem of(String id,
                                  String picklistId,
                                  int sortOrder,
                                  String title,
                                  String priceBand,
                                  String reason,
                                  String differentiation,
                                  String demand,
                                  String competition,
                                  String margin,
                                  String risk,
                                  Instant createdAt) {
        PicklistItem item = new PicklistItem();
        item.id = id;
        item.picklistId = picklistId;
        item.sortOrder = sortOrder;
        item.title = title;
        item.priceBand = priceBand;
        item.reason = reason;
        item.differentiation = differentiation;
        item.demand = demand;
        item.competition = competition;
        item.margin = margin;
        item.risk = risk;
        item.createdAt = createdAt;
        return item;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPicklistId() {
        return picklistId;
    }

    public void setPicklistId(String picklistId) {
        this.picklistId = picklistId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPriceBand() {
        return priceBand;
    }

    public void setPriceBand(String priceBand) {
        this.priceBand = priceBand;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getDifferentiation() {
        return differentiation;
    }

    public void setDifferentiation(String differentiation) {
        this.differentiation = differentiation;
    }

    public String getDemand() {
        return demand;
    }

    public void setDemand(String demand) {
        this.demand = demand;
    }

    public String getCompetition() {
        return competition;
    }

    public void setCompetition(String competition) {
        this.competition = competition;
    }

    public String getMargin() {
        return margin;
    }

    public void setMargin(String margin) {
        this.margin = margin;
    }

    public String getRisk() {
        return risk;
    }

    public void setRisk(String risk) {
        this.risk = risk;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
