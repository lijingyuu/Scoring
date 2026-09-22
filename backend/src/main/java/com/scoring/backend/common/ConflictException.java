package com.scoring.backend.common;

/**
 * 资源状态冲突（HTTP 409）。
 *
 * 当前场景：保存比赛事件时 (match_id, event_seq) 已被占用且 payload 不一致
 * （双设备执裁、清缓存/换设备后 eventSeq 从 1 重来）。此时整批拒绝、事务回滚，
 * message 里带上服务端当前最大序号，前端据此重排本地未同步事件序号后重试一次。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
