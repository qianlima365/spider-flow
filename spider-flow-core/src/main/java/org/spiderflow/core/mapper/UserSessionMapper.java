package org.spiderflow.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.spiderflow.core.model.UserSession;

@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
}