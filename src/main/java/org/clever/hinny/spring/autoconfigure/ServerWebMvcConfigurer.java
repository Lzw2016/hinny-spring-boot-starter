package org.clever.hinny.spring.autoconfigure;

import org.clever.hinny.mvc.HttpRequestScriptHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * 作者： lzw<br/>
 * 创建时间：2019-05-17 14:29 <br/>
 */
@Configuration
@AutoConfigureAfter({AutoConfigureMvcHandler.class})
public class ServerWebMvcConfigurer implements WebMvcConfigurer {
    private final HttpRequestScriptHandler<?, ?> httpRequestScriptHandler;

    public ServerWebMvcConfigurer(ObjectProvider<HttpRequestScriptHandler<?, ?>> httpRequestScriptHandler) {
        this.httpRequestScriptHandler = Objects.requireNonNull(httpRequestScriptHandler.getIfAvailable());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(httpRequestScriptHandler).addPathPatterns("/**").order(Integer.MAX_VALUE);
    }
}
