package com.xmut.ebus.application.identity.query;

import com.xmut.ebus.application.identity.dto.MeDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.common.exception.ErrorCode;
import com.xmut.ebus.domain.identity.model.User;
import com.xmut.ebus.domain.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity 读用例。
 */
@Service
@RequiredArgsConstructor
public class IdentityQueryService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public MeDTO findMe(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或未登录"));
        return new MeDTO(user.getId(), user.getUsername(), user.getEmail());
    }
}
