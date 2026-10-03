package com.xmut.forma.application.business.credit.query;

import com.xmut.forma.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.forma.application.business.credit.dto.CreditUsageDTO;
import com.xmut.forma.application.business.credit.dto.CreditUsageDTO.CreditUsageEntryDTO;
import com.xmut.forma.application.business.credit.service.CreditApplicationService;
import com.xmut.forma.domain.business.credit.model.CreditAccount;
import com.xmut.forma.domain.business.credit.model.CreditHold;
import com.xmut.forma.domain.business.credit.repository.CreditHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * CreditLedger 读用例（含懒建账 / 懒月检副作用，经 ApplicationService）。
 */
@Service
@RequiredArgsConstructor
public class CreditQueryService {

    /** 账户用量流水上限（近 N 条 SETTLED）。 */
    public static final int USAGE_ENTRY_LIMIT = 50;

    private final CreditApplicationService creditApplicationService;
    private final CreditHoldRepository creditHoldRepository;

    @Transactional(rollbackFor = Exception.class)
    public CreditBalanceDTO findByUserId(String userId) {
        CreditAccount account = creditApplicationService.ensureReady(userId);
        return new CreditBalanceDTO(
                account.getTier().name(),
                account.available(),
                account.getBalance(),
                account.getReserved(),
                account.getNextResetAt(),
                account.getPeriodAnchorAt());
    }

    /**
     * 账户页只读用量：摘要与 CreditLedger 一致；流水仅 SETTLED，标题固定「已扣分」。
     */
    @Transactional(rollbackFor = Exception.class)
    public CreditUsageDTO findUsage(String userId) {
        CreditAccount account = creditApplicationService.ensureReady(userId);
        int monthlyQuota = account.getTier().getMonthlyQuota();
        int available = account.available();
        int used = monthlyQuota - available;

        List<CreditHold> holds = creditHoldRepository.listSettledByUserId(userId, USAGE_ENTRY_LIMIT);
        List<CreditUsageEntryDTO> entries = new ArrayList<>(holds.size());
        for (CreditHold hold : holds) {
            int amount = hold.getAmount();
            entries.add(new CreditUsageEntryDTO(
                    hold.getId(),
                    CreditUsageDTO.ENTRY_TITLE_SETTLED,
                    amount,
                    -amount,
                    hold.getUpdatedAt()));
        }

        return new CreditUsageDTO(
                account.getTier().name(),
                available,
                monthlyQuota,
                used,
                account.getNextResetAt(),
                entries);
    }
}
