package com.scoring.backend.common;

import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;

/**
 * 唯一键冲突判定：用于把"先查后插"竞态下的唯一键兜底异常翻译成幂等语义，避免并发变成 500。
 *
 * <p>判定依据（沿 cause 链向上找）：
 * <ul>
 *   <li>cause 链上出现 Spring 的 {@link DuplicateKeyException}；
 *   <li>或出现 {@link SQLException}，其 SQLState 以 "23" 开头（完整性约束违规：H2 23505 / MySQL 23000）；
 *   <li>或 MySQL 驱动 errorCode == 1062（Duplicate entry）。
 * </ul>
 * 该谓词同时覆盖 H2 与 MySQL，不引入新依赖。
 */
public final class DuplicateKeySupport {

    private static final int MYSQL_DUPLICATE_ENTRY_CODE = 1062;
    private static final String SQLSTATE_INTEGRITY_CONSTRAINT_PREFIX = "23";
    private static final int MAX_CAUSE_DEPTH = 10;

    private DuplicateKeySupport() {
    }

    public static boolean isDuplicateKey(Throwable throwable) {
        Throwable cursor = throwable;
        for (int depth = 0; cursor != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cursor instanceof DuplicateKeyException) {
                return true;
            }
            if (cursor instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && sqlState.startsWith(SQLSTATE_INTEGRITY_CONSTRAINT_PREFIX)) {
                    return true;
                }
                if (sqlException.getErrorCode() == MYSQL_DUPLICATE_ENTRY_CODE) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
