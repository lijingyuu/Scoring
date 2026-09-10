package com.scoring.backend.service.auth;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.entity.WebLoginSession;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.domain.vo.PcLoginStatusVO;
import com.scoring.backend.domain.vo.PcQrCodeVO;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.mapper.WebLoginSessionMapper;
import com.scoring.backend.service.AuthService;
import com.scoring.backend.service.wechat.WechatQrCodeClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * PC 网页扫码登录会话状态机。
 *
 * 票据流转：CREATED（出码）→ SCANNED（小程序上报扫码）→ CONFIRMED（确认授权）
 *           → CONSUMED（PC 首次轮询取走 token，一次性下发防重放）。
 * EXPIRED 为虚拟态：所有读写先判 NOW() > expire_time，无需定时任务。
 *
 * 状态转移全部使用「条件 UPDATE + 影响行数」原子完成（与 MatchLockService 同风格），
 * 并发扫码/确认/取 token 时天然只有一个赢家。
 */
@Service
public class PcLoginService {

    /** ticket 即小程序码 scene，32 位十六进制（去横杠 UUID） */
    private static final String TICKET_PATTERN = "^[0-9a-f]{32}$";
    private static final int TICKET_EXPIRE_SECONDS = 180;
    /** 过期票据保留时长（懒清理，防止表膨胀） */
    private static final int EXPIRED_RETENTION_HOURS = 24;

    private final WebLoginSessionMapper webLoginSessionMapper;
    private final UserMapper userMapper;
    private final WechatQrCodeClient wechatQrCodeClient;
    private final AuthService authService;

    public PcLoginService(WebLoginSessionMapper webLoginSessionMapper,
                          UserMapper userMapper,
                          WechatQrCodeClient wechatQrCodeClient,
                          AuthService authService) {
        this.webLoginSessionMapper = webLoginSessionMapper;
        this.userMapper = userMapper;
        this.wechatQrCodeClient = wechatQrCodeClient;
        this.authService = authService;
    }

    /**
     * PC 端申请登录二维码：生成 ticket、落库、调微信换取小程序码。
     * 微信调用不包在事务里（避免网络 I/O 占用数据库连接）；
     * 若微信调用失败，已插入的 CREATED 票据 3 分钟后自然过期，无副作用。
     */
    public PcQrCodeVO createQrCode() {
        LocalDateTime now = LocalDateTime.now();
        String ticket = UUID.randomUUID().toString().replace("-", "");

        webLoginSessionMapper.delete(new LambdaQueryWrapper<WebLoginSession>()
                .lt(WebLoginSession::getExpireTime, now.minusHours(EXPIRED_RETENTION_HOURS)));

        WebLoginSession session = new WebLoginSession();
        session.setTicket(ticket);
        session.setStatus(WebLoginSession.STATUS_CREATED);
        session.setExpireTime(now.plusSeconds(TICKET_EXPIRE_SECONDS));
        webLoginSessionMapper.insert(session);

        byte[] image = wechatQrCodeClient.fetchLoginQrCode(ticket);
        return new PcQrCodeVO(
                ticket,
                "data:image/png;base64," + Base64.getEncoder().encodeToString(image),
                TICKET_EXPIRE_SECONDS);
    }

    /**
     * 小程序落地页上报扫码：CREATED → SCANNED（幂等，重复/他人已扫时静默成功）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reportScan(String ticket, String userId) {
        if (invalidTicket(ticket)) {
            return;
        }
        webLoginSessionMapper.update(null, new LambdaUpdateWrapper<WebLoginSession>()
                .eq(WebLoginSession::getTicket, ticket)
                .eq(WebLoginSession::getStatus, WebLoginSession.STATUS_CREATED)
                .gt(WebLoginSession::getExpireTime, LocalDateTime.now())
                .set(WebLoginSession::getStatus, WebLoginSession.STATUS_SCANNED)
                .set(WebLoginSession::getUserId, userId));
    }

    /**
     * 小程序确认授权：CREATED/SCANNED → CONFIRMED（记录确认者 user_id）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(String ticket, String userId) {
        if (invalidTicket(ticket)) {
            throw new IllegalArgumentException("二维码无效");
        }
        int rows = webLoginSessionMapper.update(null, new LambdaUpdateWrapper<WebLoginSession>()
                .eq(WebLoginSession::getTicket, ticket)
                .in(WebLoginSession::getStatus, WebLoginSession.STATUS_CREATED, WebLoginSession.STATUS_SCANNED)
                .gt(WebLoginSession::getExpireTime, LocalDateTime.now())
                .set(WebLoginSession::getStatus, WebLoginSession.STATUS_CONFIRMED)
                .set(WebLoginSession::getUserId, userId));
        if (rows == 0) {
            throw new IllegalArgumentException(resolveConfirmFailure(ticket));
        }
    }

    /**
     * PC 端轮询票据状态。CONFIRMED 时原子转移为 CONSUMED 并一次性签发 token：
     * 并发轮询只有一个能拿到 token，之后的轮询只返回 CONSUMED。
     */
    @Transactional(rollbackFor = Exception.class)
    public PcLoginStatusVO pollStatus(String ticket) {
        if (invalidTicket(ticket)) {
            return new PcLoginStatusVO(PcLoginStatusVO.STATUS_EXPIRED);
        }

        WebLoginSession session = webLoginSessionMapper.selectOne(
                new LambdaQueryWrapper<WebLoginSession>().eq(WebLoginSession::getTicket, ticket));
        if (session == null) {
            return new PcLoginStatusVO(PcLoginStatusVO.STATUS_EXPIRED);
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.getExpireTime() == null || !now.isBefore(session.getExpireTime())) {
            return new PcLoginStatusVO(PcLoginStatusVO.STATUS_EXPIRED);
        }

        String status = session.getStatus();
        if (WebLoginSession.STATUS_CREATED.equals(status)) {
            return new PcLoginStatusVO(WebLoginSession.STATUS_CREATED);
        }
        if (WebLoginSession.STATUS_SCANNED.equals(status)) {
            PcLoginStatusVO vo = new PcLoginStatusVO(WebLoginSession.STATUS_SCANNED);
            fillUser(vo, session.getUserId());
            return vo;
        }
        if (WebLoginSession.STATUS_CONSUMED.equals(status)) {
            return new PcLoginStatusVO(WebLoginSession.STATUS_CONSUMED);
        }
        if (WebLoginSession.STATUS_CONFIRMED.equals(status)) {
            int rows = webLoginSessionMapper.update(null, new LambdaUpdateWrapper<WebLoginSession>()
                    .eq(WebLoginSession::getTicket, ticket)
                    .eq(WebLoginSession::getStatus, WebLoginSession.STATUS_CONFIRMED)
                    .gt(WebLoginSession::getExpireTime, now)
                    .set(WebLoginSession::getStatus, WebLoginSession.STATUS_CONSUMED));
            if (rows == 0) {
                // 被并发轮询抢先取走，或恰好此刻过期
                return new PcLoginStatusVO(PcLoginStatusVO.STATUS_EXPIRED);
            }
            AuthLoginVO login = authService.issueLogin(session.getUserId());
            PcLoginStatusVO vo = new PcLoginStatusVO(WebLoginSession.STATUS_CONFIRMED);
            fillUser(vo, session.getUserId());
            vo.setToken(login.getToken());
            vo.setProfileCompleted(login.getProfileCompleted());
            return vo;
        }
        return new PcLoginStatusVO(PcLoginStatusVO.STATUS_EXPIRED);
    }

    private void fillUser(PcLoginStatusVO vo, String userId) {
        if (StrUtil.isBlank(userId)) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user != null) {
            vo.setNickname(user.getNickname());
            vo.setAvatarUrl(user.getAvatarUrl());
        }
    }

    private String resolveConfirmFailure(String ticket) {
        WebLoginSession session = webLoginSessionMapper.selectOne(
                new LambdaQueryWrapper<WebLoginSession>().eq(WebLoginSession::getTicket, ticket));
        if (session == null) {
            return "二维码无效";
        }
        if (session.getExpireTime() == null || !LocalDateTime.now().isBefore(session.getExpireTime())) {
            return "二维码已过期，请在电脑上刷新后重新扫码";
        }
        return "二维码已被使用，请在电脑上重新发起登录";
    }

    private static boolean invalidTicket(String ticket) {
        return ticket == null || !ticket.matches(TICKET_PATTERN);
    }
}
