package org.spiderflow.controller;

import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.model.User;
import org.spiderflow.core.service.UserService;
import org.spiderflow.model.JsonBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    public static class LoginRequest {
        public String username;
        public String password;
    }

    @GetMapping("/me")
    public JsonBean<Map<String, Object>> me(HttpServletRequest request){
        String token = readTokenFromCookies(request.getCookies());
        if(StringUtils.isBlank(token)){
            return new JsonBean<>(401, "Unauthorized: token missing", null);
        }
        User u = userService.findByToken(token);
        if(u == null){
            return new JsonBean<>(401, "Unauthorized: token invalid or expired", null);
        }
        Map<String,Object> resp = new HashMap<>();
        resp.put("username", u.getUsername());
        resp.put("role", u.getRole());
        return new JsonBean<>(0, "ok", resp);
    }

    private String readTokenFromCookies(Cookie[] cookies){
        if(cookies == null) return null;
        for(Cookie c : cookies){
            if("X-Admin-Token".equalsIgnoreCase(c.getName()) || "X-Token".equalsIgnoreCase(c.getName())){
                String v = c.getValue();
                if(StringUtils.isNotBlank(v) && !StringUtils.equalsAnyIgnoreCase(v, "undefined", "null")){
                    return v;
                }
            }
        }
        return null;
    }

    @PostMapping("/login")
    public JsonBean<Map<String, Object>> login(@RequestBody AuthController.LoginRequest req, HttpServletResponse response){
        if(req == null || StringUtils.isAnyBlank(req.username, req.password)){
            return new JsonBean<>(1, "用户名或密码为空", null);
        }
        User user = userService.findByUsername(req.username);
        if(user == null){
            return new JsonBean<>(1, "用户不存在", null);
        }
        if(!StringUtils.equals(user.getPassword(), req.password)){
            return new JsonBean<>(1, "密码错误", null);
        }
        String token = userService.issueTokenForUser(user);
        Map<String,Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("username", user.getUsername());
        resp.put("role", user.getRole());
        // 根据角色设置不同的响应头，前端据此保存并跳转
        int maxAgeSeconds = 0;
        if(user.getTokenExpireAt() != null){
            long delta = user.getTokenExpireAt().getTime() - System.currentTimeMillis();
            maxAgeSeconds = (int) Math.max(delta / 1000L, 0);
        }
        if("SUPER_ADMIN".equalsIgnoreCase(user.getRole())){
            response.setHeader("X-Admin-Token", token);
            javax.servlet.http.Cookie adminCookie = new javax.servlet.http.Cookie("X-Admin-Token", token);
            adminCookie.setPath("/");
            adminCookie.setHttpOnly(true);
            adminCookie.setSecure(false);
            adminCookie.setMaxAge(maxAgeSeconds);
            response.addCookie(adminCookie);
        }else{
            response.setHeader("X-Token", token);
            javax.servlet.http.Cookie userCookie = new javax.servlet.http.Cookie("X-Token", token);
            userCookie.setPath("/");
            userCookie.setHttpOnly(true);
            userCookie.setSecure(false);
            userCookie.setMaxAge(maxAgeSeconds);
            response.addCookie(userCookie);
        }
        return new JsonBean<>(0, "登录成功", resp);
    }

    @PostMapping("/logout")
    public JsonBean<Boolean> logout(HttpServletRequest request, HttpServletResponse response){
        String token = readTokenFromCookies(request.getCookies());
        if(StringUtils.isNotBlank(token)){
            try{ userService.expireToken(token); }catch(Exception ignore){}
        }
        // 清除客户端 Cookie（HttpOnly）
        Cookie c1 = new Cookie("X-Admin-Token", "");
        c1.setPath("/");
        c1.setHttpOnly(true);
        c1.setMaxAge(0);
        response.addCookie(c1);

        Cookie c2 = new Cookie("X-Token", "");
        c2.setPath("/");
        c2.setHttpOnly(true);
        c2.setMaxAge(0);
        response.addCookie(c2);

        return new JsonBean<>(0, "已退出", true);
    }
}