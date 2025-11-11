package org.spiderflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.model.Team;
import org.spiderflow.core.model.TeamMember;
import org.spiderflow.core.service.TeamMemberService;
import org.spiderflow.core.service.TeamService;
import org.spiderflow.model.JsonBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/admin/teams")
public class AdminTeamController {

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamMemberService teamMemberService;

    @GetMapping("/list")
    public IPage<Team> list(@RequestParam(name = "page", defaultValue = "1") Integer page,
                            @RequestParam(name = "limit", defaultValue = "10") Integer size,
                            @RequestParam(name = "name", required = false) String name){
        QueryWrapper<Team> qw = new QueryWrapper<>();
        if(StringUtils.isNotBlank(name)) qw.like("name", name);
        return teamService.page(new Page<>(page, size), qw.orderByDesc("create_date"));
    }

    @PostMapping("/create")
    public JsonBean<Team> create(@RequestBody Team team){
        if(StringUtils.isBlank(team.getName()) || team.getOwnerUserId() == null){
            return new JsonBean<>(1, "团队名称与Owner不能为空", null);
        }
        team.setStatus(StringUtils.defaultIfBlank(team.getStatus(), "ACTIVE"));
        team.setCreateDate(new Date());
        team.setUpdateDate(new Date());
        teamService.save(team);
        // 创建时将Owner加入成员表
        TeamMember owner = new TeamMember();
        owner.setTeamId(team.getId());
        owner.setUserId(team.getOwnerUserId());
        owner.setMemberRole("OWNER");
        owner.setCreateDate(new Date());
        teamMemberService.save(owner);
        return new JsonBean<>(team);
    }

    @PostMapping("/update")
    public JsonBean<Boolean> update(@RequestBody Team team){
        team.setUpdateDate(new Date());
        return new JsonBean<>(teamService.updateById(team));
    }

    @PostMapping("/delete")
    public JsonBean<Boolean> delete(@RequestParam("id") String id){
        // 成员通过 FK 级联删除
        return new JsonBean<>(teamService.removeById(id));
    }

    @GetMapping("/get")
    public JsonBean<Team> get(@RequestParam("id") String id){
        return new JsonBean<>(teamService.getById(id));
    }

    // 团队成员管理
    @GetMapping("/members")
    public IPage<TeamMember> members(@RequestParam("teamId") String teamId,
                                     @RequestParam(name = "page", defaultValue = "1") Integer page,
                                     @RequestParam(name = "limit", defaultValue = "10") Integer size){
        return teamMemberService.page(new Page<>(page, size), new QueryWrapper<TeamMember>().eq("team_id", teamId));
    }

    @PostMapping("/members/add")
    public JsonBean<TeamMember> addMember(@RequestBody TeamMember member){
        if(member.getTeamId() == null || member.getUserId() == null){
            return new JsonBean<>(1, "teamId 或 userId 不能为空", null);
        }
        member.setMemberRole(StringUtils.defaultIfBlank(member.getMemberRole(), "MEMBER"));
        member.setCreateDate(new Date());
        teamMemberService.save(member);
        return new JsonBean<>(member);
    }

    @PostMapping("/members/remove")
    public JsonBean<Boolean> removeMember(@RequestParam("teamId") String teamId,
                                          @RequestParam("userId") String userId){
        TeamMember existing = teamMemberService.getOne(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("user_id", userId));
        if(existing != null && "OWNER".equalsIgnoreCase(existing.getMemberRole())){
            return new JsonBean<>(1, "不能移除拥有者", false);
        }
        return new JsonBean<>(0, "删除成功", teamMemberService.remove(new QueryWrapper<TeamMember>().eq("team_id", teamId).eq("user_id", userId)));
    }

    @PostMapping("/members/update")
    public JsonBean<Boolean> updateMember(@RequestBody TeamMember member){
        if(member.getTeamId() == null || member.getUserId() == null || StringUtils.isBlank(member.getMemberRole())){
            return new JsonBean<>(1, "teamId、userId 与 memberRole 不能为空", false);
        }
        TeamMember existing = teamMemberService.getOne(new QueryWrapper<TeamMember>().eq("team_id", member.getTeamId()).eq("user_id", member.getUserId()));
        if(existing == null){
            return new JsonBean<>(1, "成员不存在", false);
        }
        if("OWNER".equalsIgnoreCase(existing.getMemberRole())){
            return new JsonBean<>(1, "拥有者角色不可修改", false);
        }
        existing.setMemberRole(member.getMemberRole());
        return new JsonBean<>(0, "更新成功", teamMemberService.updateById(existing));
    }
}