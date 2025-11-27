package org.spiderflow.controller;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private org.spiderflow.core.service.UserSessionService userSessionService;

    @Autowired
    private org.springframework.core.env.Environment env;

    @Value("${auth.oidc.providers:github,google}")
    private String configuredProviders;

    static class OidcProvider {
        String id;
        String name;
        String authUrl;
        String tokenUrl;
        String userInfoUrl;
        String clientId;
        String clientSecret;
        String redirectUri;
        String scope;
    }

    private OidcProvider readProvider(String id){
        String prefix = "auth.oidc." + id + ".";
        OidcProvider p = new OidcProvider();
        p.id = id;
        p.name = capitalize(id);
        p.authUrl = env.getProperty(prefix + "authUrl", defaultAuthUrl(id));
        p.tokenUrl = env.getProperty(prefix + "tokenUrl", defaultTokenUrl(id));
        p.userInfoUrl = env.getProperty(prefix + "userInfoUrl", defaultUserInfoUrl(id));
        p.clientId = env.getProperty(prefix + "clientId", "");
        p.clientSecret = env.getProperty(prefix + "clientSecret", "");
        p.redirectUri = env.getProperty(prefix + "redirectUri", defaultRedirectUri(id));
        p.scope = env.getProperty(prefix + "scope", defaultScope(id));
        return p;
    }

    private String capitalize(String s){
        if(StringUtils.isBlank(s)) return s;
        if("github".equalsIgnoreCase(s)) return "GitHub";
        if("google".equalsIgnoreCase(s)) return "Google";
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    private String defaultRedirectUri(String id){
        return String.format("http://localhost:8088/auth/oidc/callback?provider=%s", id);
    }

    private String defaultAuthUrl(String id){
        switch (id.toLowerCase()){
            case "github": return "https://github.com/login/oauth/authorize";
            case "google": return "https://accounts.google.com/o/oauth2/v2/auth";
            default: return "";
        }
    }

    private String defaultTokenUrl(String id){
        switch (id.toLowerCase()){
            case "github": return "https://github.com/login/oauth/access_token";
            case "google": return "https://oauth2.googleapis.com/token";
            default: return "";
        }
    }

    private String defaultUserInfoUrl(String id){
        switch (id.toLowerCase()){
            case "github": return "https://api.github.com/user";
            case "google": return "https://openidconnect.googleapis.com/v1/userinfo";
            default: return "";
        }
    }

    private String defaultScope(String id){
        switch (id.toLowerCase()){
            case "github": return "read:user user:email";
            case "google": return "openid email profile";
            default: return "openid";
        }
    }

    public static class LoginRequest {
        public String username;
        public String password;
        public Boolean remember;
    }

    @GetMapping("/oidc/providers")
    public JsonBean<List<Map<String,String>>> providers(){
        System.out.println(configuredProviders);
        List<String> ids = Arrays.stream(StringUtils.split(configuredProviders, ','))
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .collect(Collectors.toList());
        List<Map<String,String>> list = new ArrayList<>();
        for(String id : ids){
            OidcProvider p = readProvider(id);
            if(StringUtils.isNotBlank(p.authUrl) && StringUtils.isNotBlank(p.clientId) && StringUtils.isNotBlank(p.redirectUri)){
                Map<String,String> m = new HashMap<>();
                m.put("id", p.id);
                m.put("name", p.name);
                list.add(m);
            }
        }
        return new JsonBean<>(0, "ok", list);
    }

    @GetMapping("/oidc/start")
    public ResponseEntity<Void> start(@RequestParam("provider") String provider,
                                      @RequestParam(value = "state", required = false) String state) throws UnsupportedEncodingException {
        OidcProvider p = readProvider(provider);
        if(StringUtils.isAnyBlank(p.authUrl, p.clientId, p.redirectUri)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        StringBuilder url = new StringBuilder(p.authUrl);
        url.append(url.indexOf("?") >= 0 ? "&" : "?");
        url.append("response_type=code");
        url.append("&client_id=").append(URLEncoder.encode(p.clientId, String.valueOf(StandardCharsets.UTF_8)));
        url.append("&redirect_uri=").append(URLEncoder.encode(p.redirectUri, String.valueOf(StandardCharsets.UTF_8)));
        if(StringUtils.isNotBlank(p.scope)){
            url.append("&scope=").append(URLEncoder.encode(p.scope, String.valueOf(StandardCharsets.UTF_8)));
        }
        if(StringUtils.isNotBlank(state)){
            url.append("&state=").append(URLEncoder.encode(state, String.valueOf(StandardCharsets.UTF_8)));
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url.toString())
                .build();
    }

    @GetMapping("/oidc/callback")
    public ResponseEntity<Void> callback(@RequestParam("provider") String provider,
                                         @RequestParam(value = "code", required = false) String code,
                                         @RequestParam(value = "state", required = false) String state,
                                         HttpServletResponse response){
        if(StringUtils.isBlank(code)){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        OidcProvider p = readProvider(provider);
        try{
            String accessToken = exchangeCodeForToken(p, code);
            if(StringUtils.isBlank(accessToken)){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            Map<String,Object> info = fetchUserInfo(p, accessToken);
            String username = deriveUsername(provider, info);
            if(StringUtils.isBlank(username)){
                username = provider + "_user";
            }
            User user = userService.findByUsername(username);
            Date now = new Date();
            if(user == null){
                user = new User();
                user.setUsername(username);
                user.setPassword("");
                user.setRole("USER");
                user.setStatus("ACTIVE");
                user.setCreateDate(now);
                user.setUpdateDate(now);
                userService.save(user);
            }
            org.spiderflow.core.model.UserSession session = userSessionService.issueSessionForUser(user, null);
            String token = session.getToken();
            int maxAgeSeconds = (int)Math.max((session.getExpireAt().getTime() - System.currentTimeMillis()) / 1000L, 0);
            if("SUPER_ADMIN".equalsIgnoreCase(user.getRole())){
                response.setHeader("X-Admin-Token", token);
                javax.servlet.http.Cookie adminCookie = new javax.servlet.http.Cookie("X-Admin-Token", token);
                adminCookie.setPath("/");
                adminCookie.setHttpOnly(true);
                adminCookie.setSecure(false);
                adminCookie.setMaxAge(maxAgeSeconds);
                response.addCookie(adminCookie);
                // 写入用户名 Cookie（可供前端读取展示）
                javax.servlet.http.Cookie usernameCookie = new javax.servlet.http.Cookie("X-Username", user.getUsername());
                usernameCookie.setPath("/");
                usernameCookie.setHttpOnly(false);
                usernameCookie.setSecure(false);
                usernameCookie.setMaxAge(maxAgeSeconds);
                response.addCookie(usernameCookie);
            }else{
                response.setHeader("X-Token", token);
                javax.servlet.http.Cookie userCookie = new javax.servlet.http.Cookie("X-Token", token);
                userCookie.setPath("/");
                userCookie.setHttpOnly(true);
                userCookie.setSecure(false);
                userCookie.setMaxAge(maxAgeSeconds);
                response.addCookie(userCookie);
                // 写入用户名 Cookie（可供前端读取展示）
                javax.servlet.http.Cookie usernameCookie = new javax.servlet.http.Cookie("X-Username", user.getUsername());
                usernameCookie.setPath("/");
                usernameCookie.setHttpOnly(false);
                usernameCookie.setSecure(false);
                usernameCookie.setMaxAge(maxAgeSeconds);
                response.addCookie(usernameCookie);
            }
            String next = "/";
            if(StringUtils.isNotBlank(state)){
                try{ next = java.net.URLDecoder.decode(state, StandardCharsets.UTF_8.name()); }catch(Exception ignore){}
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", next)
                    .build();
        }catch(Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String exchangeCodeForToken(OidcProvider p, String code) throws Exception {
        Map<String,String> data = new LinkedHashMap<>();
        data.put("grant_type", "authorization_code");
        data.put("code", code);
        data.put("client_id", p.clientId);
        data.put("client_secret", p.clientSecret);
        data.put("redirect_uri", p.redirectUri);

        Connection conn = Jsoup.connect(p.tokenUrl)
                .ignoreContentType(true)
                .method(Connection.Method.POST)
                .header("Accept", "application/json");
        for(Map.Entry<String,String> e : data.entrySet()){
            conn.data(e.getKey(), e.getValue());
        }
        Connection.Response resp = conn.execute();
        String body = resp.body();
        JSONObject json;
        try{
            json = JSON.parseObject(body);
        }catch(Exception ex){
            // GitHub may return urlencoded, try to parse manually
            json = new JSONObject();
            for(String pair : body.split("&")){
                String[] kv = pair.split("=");
                if(kv.length==2) json.put(kv[0], kv[1]);
            }
        }
        String token = json.getString("access_token");
        if(StringUtils.isBlank(token)){
            token = json.getString("id_token"); // Some OIDC may use id_token
        }
        return token;
    }

    private Map<String,Object> fetchUserInfo(OidcProvider p, String accessToken) throws Exception {
        Connection conn = Jsoup.connect(p.userInfoUrl)
                .ignoreContentType(true)
                .method(Connection.Method.GET)
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");
        Connection.Response resp = conn.execute();
        String body = resp.body();
        try{
            return JSON.parseObject(body);
        }catch(Exception e){
            return Collections.emptyMap();
        }
    }

    private String deriveUsername(String provider, Map<String,Object> info){
        if(info == null) return null;
        String p = provider.toLowerCase();
        if("google".equals(p)){
            String email = asString(info.get("email"));
            String preferred = asString(info.get("preferred_username"));
            String name = StringUtils.defaultIfBlank(preferred, email);
            if(StringUtils.isBlank(name)) name = asString(info.get("sub"));
            return name;
        }else if("github".equals(p)){
            String login = asString(info.get("login"));
            String email = asString(info.get("email"));
            return StringUtils.defaultIfBlank(login, email);
        }else{
            String preferred = asString(info.get("preferred_username"));
            String email = asString(info.get("email"));
            String sub = asString(info.get("sub"));
            String name = StringUtils.defaultIfBlank(preferred, email);
            if(StringUtils.isBlank(name)) name = sub;
            return name;
        }
    }

    private String asString(Object o){
        return o == null ? null : String.valueOf(o);
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
            // 写入用户名 Cookie（可供前端读取展示）
            javax.servlet.http.Cookie usernameCookie = new javax.servlet.http.Cookie("X-Username", user.getUsername());
            usernameCookie.setPath("/");
            usernameCookie.setHttpOnly(false);
            usernameCookie.setSecure(false);
            usernameCookie.setMaxAge(maxAgeSeconds);
            response.addCookie(usernameCookie);
        }else{
            response.setHeader("X-Token", token);
            javax.servlet.http.Cookie userCookie = new javax.servlet.http.Cookie("X-Token", token);
            userCookie.setPath("/");
            userCookie.setHttpOnly(true);
            userCookie.setSecure(false);
            userCookie.setMaxAge(maxAgeSeconds);
            response.addCookie(userCookie);
            // 写入用户名 Cookie（可供前端读取展示）
            javax.servlet.http.Cookie usernameCookie = new javax.servlet.http.Cookie("X-Username", user.getUsername());
            usernameCookie.setPath("/");
            usernameCookie.setHttpOnly(false);
            usernameCookie.setSecure(false);
            usernameCookie.setMaxAge(maxAgeSeconds);
            response.addCookie(usernameCookie);
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

        // 清除用户名 Cookie（非 HttpOnly）
        Cookie c3 = new Cookie("X-Username", "");
        c3.setPath("/");
        c3.setHttpOnly(false);
        c3.setMaxAge(0);
        response.addCookie(c3);

        return new JsonBean<>(0, "已退出", true);
    }
}