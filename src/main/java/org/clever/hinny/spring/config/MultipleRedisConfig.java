package org.clever.hinny.spring.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.Collections;
import java.util.Map;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/11/16 21:21 <br/>
 */
@ConfigurationProperties(prefix = Constant.Config_Multiple_Redis_Config)
@Data
public class MultipleRedisConfig {
    /**
     * 默认的数据源名称
     */
    private String defaultName = "default";

    /**
     * Redis全局配置
     */
    @NestedConfigurationProperty
    private RedisProperties globalConfig = new RedisProperties();

    /**
     * Redis数据源集合(数据源名称 --> 数据源配置)
     */
    private Map<String, RedisProperties> redisMap = Collections.emptyMap();
}
