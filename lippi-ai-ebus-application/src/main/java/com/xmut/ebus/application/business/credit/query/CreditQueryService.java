package com.xmut.ebus.application.business.credit.query;

import com.xmut.ebus.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.domain.business.credit.model.CreditAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CreditLedger 读用例（含懒建账 / 懒月检副作用，经 ApplicationService）。
 */
@Service
@RequiredArgsConstructor
public class CreditQueryService {

    private final CreditApplicationService creditApplicationService;

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
}
