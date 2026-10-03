package com.xmut.forma.interfaces.web.business.credit;

import com.xmut.forma.application.business.credit.command.ChangeTierCommand;
import com.xmut.forma.application.business.credit.dto.CreditBalanceDTO;
import com.xmut.forma.application.business.credit.service.CreditApplicationService;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.response.ApiResponse;
import com.xmut.forma.domain.business.credit.constant.CreditTier;
import com.xmut.forma.interfaces.security.SecuritySupport;
import com.xmut.forma.interfaces.vo.business.credit.ChangeTierRequest;
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
