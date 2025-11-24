package org.spiderflow.configuration;

import org.spiderflow.core.model.User;
import org.spiderflow.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private org.spiderflow.core.service.UserSessionService userSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        // 登录接口不拦截
        if(uri.startsWith("/auth/login")){
            return true;
        }
        // 其它 /admin/** 必须携带超级管理员令牌
        if(uri.startsWith("/admin/")){
            String token = request.getHeader("X-Admin-Token");
            if(token == null || token.trim().isEmpty()){
                javax.servlet.http.Cookie[] cookies = request.getCookies();
                if(cookies != null){
                    for(javax.servlet.http.Cookie c : cookies){
                        if("X-Admin-Token".equalsIgnoreCase(c.getName())){
                            token = c.getValue();
                            break;
                        }
                    }
                }
            }
            org.spiderflow.core.model.UserSession session = userSessionService.findValidByToken(token);
            User admin = (session == null) ? null : userService.getById(session.getUserId());
            if(admin == null){
                // 区分请求类型：HTML页返回顶层跳转脚本；Ajax/非HTML返回401 JSON
                String xrw = request.getHeader("X-Requested-With");
                String accept = request.getHeader("Accept");
                boolean ajax = xrw != null && "XMLHttpRequest".equalsIgnoreCase(xrw);
                boolean wantsHtml = accept == null || accept.contains("text/html");
                if(!ajax && wantsHtml){
                    response.setStatus(200);
                    response.setContentType("text/html;charset=UTF-8");
                    String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><script>" +
                            "if(window.top){window.top.location.href='/login.html';}else{window.location.href='/login.html';}" +
                            "</script></head><body></body></html>";
                    response.getWriter().write(html);
                }else{
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized: require SUPER_ADMIN token\"}");
                }
                return false;
            }
            if(!"SUPER_ADMIN".equalsIgnoreCase(admin.getRole())){
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized: require SUPER_ADMIN role\"}");
                return false;
            }
        }
        return true;
    }
}