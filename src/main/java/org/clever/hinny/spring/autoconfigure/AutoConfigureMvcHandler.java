package org.clever.hinny.spring.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.clever.hinny.api.pool.EngineInstancePool;
import org.clever.hinny.graal.mvc.HttpRequestGraalScriptHandler;
import org.clever.hinny.mvc.DefaultExceptionResolver;
import org.clever.hinny.mvc.ExceptionResolver;
import org.clever.hinny.mvc.HttpRequestScriptHandler;
import org.clever.hinny.spring.config.ScriptMvcHandlerConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;

import java.util.LinkedHashMap;
import java.util.Objects;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/09/11 19:15 <br/>
 */
@Configuration
@AutoConfigureAfter({AutoConfigureEngineInstancePool.class})
@EnableConfigurationProperties({ScriptMvcHandlerConfig.class})
@Slf4j
public class AutoConfigureMvcHandler {
    private final ScriptMvcHandlerConfig scriptMvcHandlerConfig;

    public AutoConfigureMvcHandler(ScriptMvcHandlerConfig scriptMvcHandlerConfig) {
        this.scriptMvcHandlerConfig = scriptMvcHandlerConfig;
    }

    @Bean("exceptionResolver")
    @ConditionalOnMissingBean
    public ExceptionResolver exceptionResolver() {
        return DefaultExceptionResolver.Instance;
    }

    @Bean("httpRequestScriptHandler")
    @ConditionalOnMissingBean
    public HttpRequestScriptHandler<Context, Value> httpRequestScriptHandler(
            ObjectProvider<EngineInstancePool<Context, Value>> pool,
            ObjectProvider<ConversionService> conversionService,
            ExceptionResolver exceptionResolver) {
        LinkedHashMap<String, String> supportPrefix = new LinkedHashMap<>(scriptMvcHandlerConfig.getPrefixMappings().size());
        for (ScriptMvcHandlerConfig.PrefixMapping mapping : scriptMvcHandlerConfig.getPrefixMappings()) {
            supportPrefix.put(mapping.getRequestPath(), mapping.getScriptPath());
        }
        return new HttpRequestGraalScriptHandler(
                supportPrefix,
                scriptMvcHandlerConfig.getSupportSuffix(),
                scriptMvcHandlerConfig.getCorsConfig(),
                Objects.requireNonNull(pool.getIfAvailable()),
                exceptionResolver == null ? DefaultExceptionResolver.Instance : exceptionResolver,
                conversionService
        );
    }
}
