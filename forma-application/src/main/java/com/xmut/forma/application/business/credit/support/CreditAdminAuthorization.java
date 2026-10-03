package com.xmut.forma.application.business.credit.support;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import com.xmut.forma.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 手工改档白名单闸门（环境变量 {@code CREDIT_ADMIN_USER_IDS}；名单空则全部拒绝）。
 */
@Component
public class CreditAdminAuthorization {

    private volatile Set<String> allowedUserIds;

    public CreditAdminAuthorization(@Value("${credit.admin.user-ids:}") String csv) {
        this.allowedUserIds = parse(csv);
    }

    public void requireOperatorAllowed(String operatorUserId) {
        StringUtils.requireHasText(operatorUserId, ErrorCode.UNAUTHORIZED);
        if (!allowedUserIds.contains(operatorUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权改档，仅白名单管理员可操作");
        }
    }

    /**
     * 测试或热替换白名单（生产由环境变量初始化；空集=全部 403）。
     */
    public void replaceAllowedUserIds(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            this.allowedUserIds = Collections.emptySet();
            return;
        }
        this.allowedUserIds = Collections.unmodifiableSet(new HashSet<String>(userIds));
    }

    private static Set<String> parse(String csv) {
        if (csv == null || csv.trim().isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<String>();
        for (String part : Arrays.asList(csv.split(","))) {
            String id = part.trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return Collections.unmodifiableSet(ids);
    }
}
