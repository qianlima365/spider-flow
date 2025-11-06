package org.spiderflow.core.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.mapper.UserMapper;
import org.spiderflow.core.model.User;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.util.Date;
import java.util.UUID;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    // 默认 token 有效期（毫秒）：24 小时
    private static final long DEFAULT_TOKEN_TTL_MS = 24L * 60 * 60 * 1000;

    public User findByTokenAndRole(String token, String role){
        if(StringUtils.isBlank(token) || StringUtils.isBlank(role)) return null;
        return this.baseMapper.selectByTokenAndRole(token, role);
    }

    public User findByUsername(String username){
        return this.baseMapper.selectByUsername(username);
    }

    public String issueTokenForUser(User user){
        String token = UUID.randomUUID().toString().replace("-","");
        user.setToken(token);
        // 设置过期时间
        user.setTokenExpireAt(new Date(System.currentTimeMillis() + DEFAULT_TOKEN_TTL_MS));
        this.updateById(user);
        return token;
    }

    public User findByToken(String token){
        if(StringUtils.isBlank(token)) return null;
        return this.baseMapper.selectByToken(token);
    }

    // 将指定 token 立即置为过期（不依赖当前过期状态）
    public boolean expireToken(String token){
        if(StringUtils.isBlank(token)) return false;
        UpdateWrapper<User> uw = new UpdateWrapper<>();
        uw.eq("token", token).set("token_expire_at", new Date(System.currentTimeMillis() - 100000));
        return this.baseMapper.update(null, uw) > 0;
    }
}