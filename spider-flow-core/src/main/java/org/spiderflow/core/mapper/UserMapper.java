package org.spiderflow.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.spiderflow.core.model.User;

public interface UserMapper extends BaseMapper<User> {

    @Select("select * from sp_user where username = #{username} limit 1")
    User selectByUsername(String username);
}