package org.spiderflow.controller;

import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.model.User;
import org.spiderflow.core.service.UserService;
import org.spiderflow.model.JsonBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private org.spiderflow.core.service.UserSessionService userSessionService;

    public static class LoginRequest {
        public String username;
        public String password;
        public Boolean remember;
    }

    @GetMapping("/me")
    public ResponseEntity<JsonBean<Map<String, Object>>> me(HttpServletRequest request){
        String token = readTokenFromHeaders(request);
        if(StringUtils.isBlank(token)){
            token = readTokenFromCookies(request.getCookies());
        }
        if(StringUtils.isBlank(token)){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JsonBean<>(-1, "Unauthorized: token missing", null));
        }
        org.spiderflow.core.model.UserSession session = userSessionService.findValidByToken(token);
        if(session == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JsonBean<>(-1, "Unauthorized: token invalid or expired", null));
        }
        User u = userService.getById(session.getUserId());
        if(u == null){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new JsonBean<>(-1, "Unauthorized: user not found", null));
        }
        Map<String,Object> resp = new HashMap<>();
        resp.put("username", u.getUsername());
        resp.put("role", u.getRole());
        long nowMs = System.currentTimeMillis();
        long expMs = (session.getExpireAt() != null) ? session.getExpireAt().getTime() : 0L;
        long remainingSec = Math.max((expMs - nowMs) / 1000L, 0L);
        resp.put("expireAt", expMs);
        resp.put("remainingSeconds", remainingSec);
        return ResponseEntity.ok(new JsonBean<>(0, "ok", resp));
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

    private String readTokenFromHeaders(HttpServletRequest request){
        String t = request.getHeader("X-Admin-Token");
        if(StringUtils.isBlank(t)){
            t = request.getHeader("X-Token");
        }
        if(StringUtils.isNotBlank(t) && !StringUtils.equalsAnyIgnoreCase(t, "undefined", "null")){
            return t;
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
        // 创建独立会话令牌（不覆盖用户表token），支持记住我长TTL
        boolean remember = req.remember != null && req.remember;
        Long ttlMs = remember ? 7L * 24 * 60 * 60 * 1000 : null; // 记住我：7天；否则默认TTL
        org.spiderflow.core.model.UserSession session = userSessionService.issueSessionForUser(user, ttlMs);
        String token = session.getToken();
        Map<String,Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("username", user.getUsername());
        resp.put("role", user.getRole());
        int maxAgeSeconds = (int)Math.max((session.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L, 0);
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
        System.out.println("maxAgeSeconds: " + maxAgeSeconds);
        System.out.println("resp: " + resp);
        return new JsonBean<>(0, "登录成功", resp);
    }

    @PostMapping("/logout")
    public JsonBean<Boolean> logout(HttpServletRequest request, HttpServletResponse response){
        String token = readTokenFromCookies(request.getCookies());
        if(StringUtils.isNotBlank(token)){
            try{ userSessionService.expireSession(token); }catch(Exception ignore){}
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