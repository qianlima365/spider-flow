package org.spiderflow.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.spiderflow.core.model.User;

public interface UserMapper extends BaseMapper<User> {

    @Select("select * from sp_user where token = #{token} and role = #{role} and status = 'ACTIVE' and token_expire_at is not null and token_expire_at > now() limit 1")
    User selectByTokenAndRole(String token, String role);

    @Select("select * from sp_user where username = #{username} limit 1")
    User selectByUsername(String username);

    @Select("select * from sp_user where token = #{token} and status = 'ACTIVE' and token_expire_at is not null and token_expire_at > now() limit 1")
    User selectByToken(String token);
}