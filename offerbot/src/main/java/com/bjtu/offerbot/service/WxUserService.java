package com.bjtu.offerbot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bjtu.offerbot.domain.WxUser;
import com.bjtu.offerbot.mapper.WxUserMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WxUserService {

    public static final String STATE_NORMAL = "NORMAL";
    public static final String STATE_SPIDER = "SPIDER";
    public static final String STATE_BANNED = "BANNED";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";

    private static final int REQUEST_LIMIT_SECONDS = 30;
    private static final int REQUEST_LIMIT_SIZE = 30;

    private final WxUserMapper wxUserMapper;

    public WxUserService(WxUserMapper wxUserMapper) {
        this.wxUserMapper = wxUserMapper;
    }

    /**
     * 每次微信命令都会刷新用户请求窗口，并在高频访问时自动标记为 SPIDER。
     */
    @Transactional
    public WxUser recordRequest(String openid) {
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException("缺少微信用户 openid。");
        }

        LocalDateTime now = LocalDateTime.now();
        WxUser user = findByOpenid(openid);
        if (user == null) {
            user = new WxUser();
            user.setOpenid(openid);
            user.setState(STATE_NORMAL);
            user.setRole(ROLE_USER);
            user.setRequestCount(1);
            user.setWindowStart(now);
            user.setCreatedAt(now);
            user.setUpdatedAt(now);
            wxUserMapper.insert(user);
            return user;
        }

        LocalDateTime windowStart = user.getWindowStart();
        if (windowStart == null || Duration.between(windowStart, now).getSeconds() > REQUEST_LIMIT_SECONDS) {
            user.setWindowStart(now);
            user.setRequestCount(1);
        } else {
            int requestCount = user.getRequestCount() == null ? 0 : user.getRequestCount();
            user.setRequestCount(requestCount + 1);
            // 只自动从 NORMAL 标记为 SPIDER，避免覆盖管理员手动设置的 BANNED 等状态。
            if (user.getRequestCount() > REQUEST_LIMIT_SIZE && STATE_NORMAL.equals(user.getState())) {
                user.setState(STATE_SPIDER);
                user.setBannedReason("30 秒内请求超过 30 次");
            }
        }
        user.setUpdatedAt(now);
        wxUserMapper.updateById(user);
        return user;
    }

    public WxUser findByOpenid(String openid) {
        return wxUserMapper.selectOne(new LambdaQueryWrapper<WxUser>()
                .eq(WxUser::getOpenid, openid)
                .last("LIMIT 1"));
    }

    @Transactional
    public void updateState(String openid, String state) {
        WxUser user = findByOpenid(openid);
        if (user == null) {
            recordRequest(openid);
            user = findByOpenid(openid);
        }
        user.setState(state);
        user.setUpdatedAt(LocalDateTime.now());
        wxUserMapper.updateById(user);
    }
}
