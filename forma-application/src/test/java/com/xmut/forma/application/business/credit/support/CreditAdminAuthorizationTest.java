package com.xmut.forma.application.business.credit.support;

import com.xmut.forma.common.exception.BusinessException;
import com.xmut.forma.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 覆盖构造 CSV（与 {@code @Value("${credit.admin.user-ids:}")} 等价）允许/拒绝，不经 replaceAllowedUserIds。
 */
class CreditAdminAuthorizationTest {

    private static final String ADMIN_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String ADMIN_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Test
    void constructorCsvAllowsListedOperator() {
        CreditAdminAuthorization auth = new CreditAdminAuthorization(
                ADMIN_A + " , " + ADMIN_B);
        assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                auth.requireOperatorAllowed(ADMIN_A);
            }
        });
        assertDoesNotThrow(new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                auth.requireOperatorAllowed(ADMIN_B);
            }
        });
    }

    @Test
    void constructorCsvRejectsUnlistedAndEmpty() {
        CreditAdminAuthorization listed = new CreditAdminAuthorization(ADMIN_A);
        BusinessException denied = assertThrows(BusinessException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        listed.requireOperatorAllowed(ADMIN_B);
                    }
                });
        assertEquals(ErrorCode.FORBIDDEN, denied.getErrorCode());

        CreditAdminAuthorization empty = new CreditAdminAuthorization("");
        BusinessException emptyDenied = assertThrows(BusinessException.class,
                new org.junit.jupiter.api.function.Executable() {
                    @Override
                    public void execute() {
                        empty.requireOperatorAllowed(ADMIN_A);
                    }
                });
        assertEquals(ErrorCode.FORBIDDEN, emptyDenied.getErrorCode());
    }
}
