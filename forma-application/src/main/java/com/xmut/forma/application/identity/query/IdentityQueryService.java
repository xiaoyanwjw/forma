package com.xmut.forma.application.identity.query;

import com.xmut.forma.application.identity.dto.AccountProfileDTO;
import com.xmut.forma.application.identity.dto.MeDTO;
import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.domain.identity.model.User;
import com.xmut.forma.domain.identity.repository.UserRepository;
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
        User user = requireUser(userId);
        return new MeDTO(user.getId(), user.getUsername(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AccountProfileDTO findProfile(String userId) {
        User user = requireUser(userId);
        return new AccountProfileDTO(user.getId(), user.getUsername(), user.getEmail());
    }

    private User requireUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或未登录"));
    }
}
