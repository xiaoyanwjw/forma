package com.xmut.ebus.infrastructure.persistence.mybatis.mapper;

import com.xmut.ebus.infrastructure.persistence.mybatis.po.PiResumeIdempotencyPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

@Mapper
public interface PiResumeIdempotencyMapper {

    int insert(PiResumeIdempotencyPO row);

    int upsert(PiResumeIdempotencyPO row);

    PiResumeIdempotencyPO selectByKey(@Param("runId") String runId,
                                      @Param("confirmId") String confirmId);

    int deleteByKey(@Param("runId") String runId,
                    @Param("confirmId") String confirmId);

    /** 仅删仍过期的行（expires_at &lt; now 或 null），避免与 concurrent complete 竞态误删。 */
    int deleteByKeyIfExpired(@Param("runId") String runId,
                             @Param("confirmId") String confirmId,
                             @Param("now") Instant now);

    int deleteByRunId(@Param("runId") String runId);
}
