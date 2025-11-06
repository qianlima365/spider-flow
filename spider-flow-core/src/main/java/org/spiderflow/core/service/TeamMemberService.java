package org.spiderflow.core.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.spiderflow.core.mapper.TeamMemberMapper;
import org.spiderflow.core.model.TeamMember;
import org.springframework.stereotype.Service;

@Service
public class TeamMemberService extends ServiceImpl<TeamMemberMapper, TeamMember> {
}