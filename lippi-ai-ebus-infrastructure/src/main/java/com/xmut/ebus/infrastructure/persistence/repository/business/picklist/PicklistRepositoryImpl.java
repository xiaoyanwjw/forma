package com.xmut.ebus.infrastructure.persistence.repository.business.picklist;

import com.xmut.ebus.domain.business.picklist.model.Picklist;
import com.xmut.ebus.domain.business.picklist.model.PicklistItem;
import com.xmut.ebus.domain.business.picklist.repository.PicklistRepository;
import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.PicklistMapper;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PicklistItemPO;
import com.xmut.ebus.infrastructure.persistence.mybatis.po.PicklistPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PicklistRepositoryImpl implements PicklistRepository {

    private final PicklistMapper picklistMapper;

    @Override
    public void save(Picklist picklist) {
        picklistMapper.insert(toPo(picklist));
        List<PicklistItem> items = picklist.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PicklistItem item : items) {
            picklistMapper.insertItem(toItemPo(item));
        }
    }

    @Override
    public Optional<Picklist> findById(String id) {
        if (!StringUtils.hasText(id)) {
            return Optional.empty();
        }
        PicklistPO po = picklistMapper.selectByBizId(id.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    @Override
    public Optional<Picklist> findByRunId(String runId) {
        if (!StringUtils.hasText(runId)) {
            return Optional.empty();
        }
        PicklistPO po = picklistMapper.selectByRunId(runId.trim());
        if (po == null) {
            return Optional.empty();
        }
        return Optional.of(toDomain(po));
    }

    private Picklist toDomain(PicklistPO po) {
        List<PicklistItemPO> itemRows = picklistMapper.selectItemsByPicklistId(po.getBizId());
        List<PicklistItem> items = new ArrayList<PicklistItem>();
        if (itemRows != null) {
            for (PicklistItemPO row : itemRows) {
                items.add(toItemDomain(row));
            }
        }
        Picklist picklist = new Picklist();
        picklist.setId(po.getBizId());
        picklist.setUserId(po.getUserId());
        picklist.setRunId(po.getRunId());
        picklist.setTemplateId(po.getTemplateId());
        picklist.setDisclaimer(po.getDisclaimer());
        picklist.setAssumptions(po.getAssumptions());
        picklist.setItems(items);
        picklist.setCreatedAt(po.getCreatedAt());
        picklist.setUpdatedAt(po.getUpdatedAt());
        return picklist;
    }

    private static PicklistPO toPo(Picklist picklist) {
        PicklistPO po = new PicklistPO();
        po.setBizId(picklist.getId());
        po.setUserId(picklist.getUserId());
        po.setRunId(picklist.getRunId());
        po.setTemplateId(picklist.getTemplateId());
        po.setDisclaimer(picklist.getDisclaimer());
        po.setAssumptions(picklist.getAssumptions());
        po.setCreatedAt(picklist.getCreatedAt());
        po.setUpdatedAt(picklist.getUpdatedAt());
        return po;
    }

    private static PicklistItemPO toItemPo(PicklistItem item) {
        PicklistItemPO po = new PicklistItemPO();
        po.setBizId(item.getId());
        po.setPicklistId(item.getPicklistId());
        po.setSortOrder(item.getSortOrder());
        po.setTitle(item.getTitle());
        po.setPriceBand(item.getPriceBand());
        po.setReason(item.getReason());
        po.setDifferentiation(item.getDifferentiation());
        po.setDemand(item.getDemand());
        po.setCompetition(item.getCompetition());
        po.setMargin(item.getMargin());
        po.setRisk(item.getRisk());
        po.setCreatedAt(item.getCreatedAt());
        return po;
    }

    private static PicklistItem toItemDomain(PicklistItemPO po) {
        PicklistItem item = new PicklistItem();
        item.setId(po.getBizId());
        item.setPicklistId(po.getPicklistId());
        item.setSortOrder(po.getSortOrder() == null ? 0 : po.getSortOrder());
        item.setTitle(po.getTitle());
        item.setPriceBand(po.getPriceBand());
        item.setReason(po.getReason());
        item.setDifferentiation(po.getDifferentiation());
        item.setDemand(po.getDemand());
        item.setCompetition(po.getCompetition());
        item.setMargin(po.getMargin());
        item.setRisk(po.getRisk());
        item.setCreatedAt(po.getCreatedAt());
        return item;
    }
}
