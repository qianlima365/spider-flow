package org.spiderflow.core.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

@TableName("sp_user_session")
public class UserSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer userId;

    private String token;

    private Date expireAt;

    private Boolean revoked;

    private Date createDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Date getExpireAt() { return expireAt; }
    public void setExpireAt(Date expireAt) { this.expireAt = expireAt; }

    public Boolean getRevoked() { return revoked; }
    public void setRevoked(Boolean revoked) { this.revoked = revoked; }

    public Date getCreateDate() { return createDate; }
    public void setCreateDate(Date createDate) { this.createDate = createDate; }

    public static Builder builder(){ return new Builder(); }

    public static class Builder {
        private final UserSession s = new UserSession();
        public Builder withUserId(Integer userId){ s.setUserId(userId); return this; }
        public Builder withToken(String token){ s.setToken(token); return this; }
        public Builder withExpireAt(Date expireAt){ s.setExpireAt(expireAt); return this; }
        public Builder withTtlMs(long ttlMs){ s.setExpireAt(new Date(System.currentTimeMillis() + Math.max(ttlMs,0))); return this; }
        public Builder withRevoked(Boolean revoked){ s.setRevoked(revoked); return this; }
        public Builder withCreateDate(Date createDate){ s.setCreateDate(createDate); return this; }
        public UserSession build(){ return s; }
    }
}