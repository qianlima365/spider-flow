package org.spiderflow.core.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.mapper.UserSessionMapper;
import org.spiderflow.core.model.User;
import org.spiderflow.core.model.UserSession;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class UserSessionService extends ServiceImpl<UserSessionMapper, UserSession> {

    private static final long DEFAULT_SESSION_TTL_MS = 24L * 60 * 60 * 1000; // 24小时

    public long getDefaultTtlMs(){
        return DEFAULT_SESSION_TTL_MS;
    }

    public UserSession issueSessionForUser(User user, Long ttlMs){
        long ttl = (ttlMs != null && ttlMs > 0) ? ttlMs : DEFAULT_SESSION_TTL_MS;
        UserSession session = UserSession.builder()
                .withUserId(user.getId())
                .withToken(UUID.randomUUID().toString().replace("-", ""))
                .withTtlMs(ttl)
                .withRevoked(Boolean.FALSE)
                .withCreateDate(new Date())
                .build();
        this.save(session);
        return session;
    }

    public UserSession findValidByToken(String token){
        if(StringUtils.isBlank(token)) return null;
        UserSession s = this.getOne(new QueryWrapper<UserSession>()
                .eq("token", token)
                .eq("revoked", false)
                .last("limit 1"));
        if(s == null) return null;
        if(s.getExpireAt() == null || s.getExpireAt().getTime() <= System.currentTimeMillis()){
            return null;
        }
        return s;
    }

    public boolean expireSession(String token){
        if(StringUtils.isBlank(token)) return false;
        UpdateWrapper<UserSession> uw = new UpdateWrapper<>();
        uw.eq("token", token)
          .set("revoked", true)
          .set("expire_at", new Date(System.currentTimeMillis() - 1000));
        return this.update(uw);
    }

    /**
     * 滑动过期：从当前时间起按给定TTL顺延会话过期时间，并更新会话。
     */
    public boolean extendSessionExpiry(UserSession session, long ttlMs){
        if(session == null || Boolean.TRUE.equals(session.getRevoked())){
            return false;
        }
        Date newExpire = new Date(System.currentTimeMillis() + ttlMs);
        session.setExpireAt(newExpire);
        return this.updateById(session);
    }
}