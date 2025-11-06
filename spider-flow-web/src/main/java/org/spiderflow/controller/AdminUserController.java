package org.spiderflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.model.User;
import org.spiderflow.core.service.UserService;
import org.spiderflow.model.JsonBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @GetMapping("/list")
    public IPage<User> list(@RequestParam(name = "page", defaultValue = "1") Integer page,
                            @RequestParam(name = "limit", defaultValue = "10") Integer size,
                            @RequestParam(name = "username", required = false) String username){
        QueryWrapper<User> qw = new QueryWrapper<>();
        if(StringUtils.isNotBlank(username)) qw.like("username", username);
        return userService.page(new Page<>(page, size), qw.orderByDesc("create_date"));
    }

    @PostMapping("/create")
    public JsonBean<User> create(@RequestBody User user){
        if(StringUtils.isBlank(user.getUsername()) || StringUtils.isBlank(user.getPassword())){
            return new JsonBean<>(1, "用户名或密码不能为空", null);
        }
        User exists = userService.findByUsername(user.getUsername());
        if(exists != null){
            return new JsonBean<>(1, "用户名已存在", null);
        }
        if(StringUtils.isBlank(user.getRole())) user.setRole("USER");
        user.setStatus(StringUtils.defaultIfBlank(user.getStatus(), "ACTIVE"));
        user.setCreateDate(new Date());
        user.setUpdateDate(new Date());
        userService.save(user);
        return new JsonBean<>(user);
    }

    @PostMapping("/update")
    public JsonBean<Boolean> update(@RequestBody User user){
        user.setUpdateDate(new Date());
        return new JsonBean<>(userService.updateById(user));
    }

    @PostMapping("/delete")
    public JsonBean<Boolean> delete(@RequestParam("id") String id){
        return new JsonBean<>(userService.removeById(id));
    }

    @GetMapping("/get")
    public JsonBean<User> get(@RequestParam("id") String id){
        return new JsonBean<>(userService.getById(id));
    }
}