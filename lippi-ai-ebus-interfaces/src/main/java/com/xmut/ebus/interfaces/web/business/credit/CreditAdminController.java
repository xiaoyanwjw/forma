package com.xmut.ebus.interfaces.web.business.credit;

import com.xmut.ebus.application.business.credit.command.ChangeTierCommand;
import com.xmut.ebus.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.ebus.application.business.credit.service.CreditApplicationService;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.common.response.ApiResponse;
import com.xmut.ebus.domain.business.credit.constant.CreditTier;
import com.xmut.ebus.interfaces.security.SecuritySupport;
import com.xmut.ebus.interfaces.vo.business.credit.ChangeTierRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 受控手工改档（管理路径；白名单闸门在 Application；无支付）。
 */
@RestController
@RequestMapping("/api/v1/admin/credits")
@RequiredArgsConstructor
public class CreditAdminController {

    private final CreditApplicationService creditApplicationService;

    @PostMapping("/change-tier")
    public ApiResponse<CreditBalanceDTO> changeTier(@Valid @RequestBody ChangeTierRequest request) {
        CreditTier targetTier;
        try {
            targetTier = CreditTier.fromCode(request.getTargetTier());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.CREDIT_TIER_INVALID, "套餐档位无效");
        }
        ChangeTierCommand command = ChangeTierCommand.builder()
                .userId(SecuritySupport.requireUserId())
                .username(SecuritySupport.currentUsername())
                .targetUserId(request.getTargetUserId())
                .targetTier(targetTier)
                .build();
        return ApiResponse.success(creditApplicationService.changeTier(command));
    }
}
