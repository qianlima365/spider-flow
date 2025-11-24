package org.spiderflow.configuration;

import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.model.User;
import org.spiderflow.core.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class CookieTokenInterceptor implements HandlerInterceptor {

    @Autowired
    private UserService userService;

    @Autowired
    private org.spiderflow.core.service.UserSessionService userSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        // 白名单交由WebMvcConfig的excludePathPatterns处理，这里直接做鉴权

        // 优先从请求头读取令牌，其次回退到 Cookie
        String token = readTokenFromHeaders(request);
        if(StringUtils.isBlank(token)){
            token = readTokenFromCookies(request.getCookies());
        }
        if(StringUtils.isNotBlank(token)){
            org.spiderflow.core.model.UserSession session = userSessionService.findValidByToken(token);
            if(session == null){
                // token 无效或过期，清除客户端Cookie并强制顶层跳转到登录页
                clearTokenCookies(response);
                if(isAjaxRequest(request) || !expectsHtml(request)){
                    writeUnauthorizedJson(response);
                    // writeFullPageRedirectHtml(response, "/login.html");
                }else{
                    writeFullPageRedirectHtml(response, "/login.html");
                }
                return false;
            }
            // 有效 token，执行滑动过期：当剩余时间较短时顺延过期时间并刷新Cookie
            long now = System.currentTimeMillis();
            long remainingMs = session.getExpireAt() == null ? 0L : (session.getExpireAt().getTime() - now);
            long thresholdMs = 15L * 60 * 1000; // 阈值：15分钟
            if(remainingMs > 0 && remainingMs < thresholdMs){
                long ttlMs = userSessionService.getDefaultTtlMs();
                boolean ok = userSessionService.extendSessionExpiry(session, ttlMs);
                if(ok){
                    String cookieName = findTokenCookieName(request.getCookies(), token);
                    if(cookieName != null){
                        Cookie refreshed = new Cookie(cookieName, token);
                        refreshed.setPath("/");
                        refreshed.setHttpOnly(true);
                        refreshed.setMaxAge((int)(ttlMs / 1000));
                        response.addCookie(refreshed);
                    }
                }
            }
            // 有效 token 直接通过
            return true;
        }
        // 无 token 的请求，如果是非白名单路径则跳转/返回401（白名单已排除）
        if(isAjaxRequest(request) || !expectsHtml(request)){
            writeUnauthorizedJson(response);
            // writeFullPageRedirectHtml(response, "/login.html");
        }else{
            writeFullPageRedirectHtml(response, "/login.html");
        }
        return false;
    }

    private String readTokenFromCookies(Cookie[] cookies){
        if(cookies == null) return null;
        String token = null;
        for(Cookie c : cookies){
            if("X-Admin-Token".equalsIgnoreCase(c.getName()) || "X-Token".equalsIgnoreCase(c.getName())){
                String v = c.getValue();
                if(StringUtils.isNotBlank(v) && !StringUtils.equalsAnyIgnoreCase(v, "undefined", "null")){
                    token = v;
                    break;
                }
            }
        }
        return token;
    }

    private void clearTokenCookies(HttpServletResponse response){
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
    }

    private String findTokenCookieName(Cookie[] cookies, String token){
        if(cookies == null || StringUtils.isBlank(token)) return null;
        String name = null;
        for(Cookie c : cookies){
            if(StringUtils.equals(c.getValue(), token)){
                name = c.getName();
                break;
            }
        }
        if(name == null){
            for(Cookie c : cookies){
                if("X-Admin-Token".equalsIgnoreCase(c.getName()) || "X-Token".equalsIgnoreCase(c.getName())){
                    name = c.getName();
                    break;
                }
            }
        }
        return name;
    }

    private void writeTopRedirectHtml(HttpServletResponse response, String target) throws Exception {
        response.setStatus(200);
        response.setContentType("text/html;charset=UTF-8");
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><script>" +
                "if(window.top){window.top.location.href='" + target + "';}else{window.location.href='" + target + "';}" +
                "</script></head><body></body></html>";
        response.getWriter().write(html);
    }

    // 更强制的整页跳转方法：尝试顶层、父层、当前窗口、_top 打开，并提供 noscript 兜底
    private void writeFullPageRedirectHtml(HttpServletResponse response, String target) throws Exception {
        response.setStatus(200);
        response.setContentType("text/html;charset=UTF-8");
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">" +
                "<noscript><meta http-equiv=\"refresh\" content=\"0; url=" + target + "\"></noscript>" +
                "<script>(function(t){" +
                "try{if(window.top && window.top !== window){window.top.location.replace(t);return;}}catch(e){}" +
                "try{if(window.parent && window.parent !== window){window.parent.location.replace(t);return;}}catch(e){}" +
                "try{window.location.replace(t);return;}catch(e){}" +
                "try{window.open(t,'_top');return;}catch(e){window.location.href=t;}" +
                "})(\"" + target + "\");</script></head><body></body></html>";
        response.getWriter().write(html);
    }

    private boolean isAjaxRequest(HttpServletRequest request){
        String xrw = request.getHeader("X-Requested-With");
        return xrw != null && "XMLHttpRequest".equalsIgnoreCase(xrw);
    }

    private boolean expectsHtml(HttpServletRequest request){
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html");
    }

    private void writeUnauthorizedJson(HttpServletResponse response) throws Exception {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"Unauthorized: token expired or missing\"}");
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
}