package org.spiderflow.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AdminAuthInterceptor adminAuthInterceptor;

    @Autowired
    private CookieTokenInterceptor cookieTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 全局 Cookie Token 拦截，放行静态与登录页
        registry.addInterceptor(cookieTokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login.html",
                        "/auth/login",
                        "/auth/logout",
                        "/",
                        "/index.html",
                        "/static/**",
                        "/js/**",
                        "/css/**",
                        "/images/**",
                        "/favicon.ico"
                );
        // 原有管理员接口拦截
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/admin/**");
    }
}