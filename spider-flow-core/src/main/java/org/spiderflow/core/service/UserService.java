package org.spiderflow.core.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.spiderflow.core.mapper.UserMapper;
import org.spiderflow.core.model.User;
import org.springframework.stereotype.Service;

@Service
public class UserService extends ServiceImpl<UserMapper, User> {

    public User findByUsername(String username){
        return this.baseMapper.selectByUsername(username);
    }
}