package org.clever.hinny.spring.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.clever.hinny.data.redis.RedisDataSource;
import org.clever.hinny.graal.data.redis.RedisDatabase;
import org.clever.hinny.graaljs.jackson.JacksonMapperSupport;
import org.clever.hinny.spring.config.MultipleRedisConfig;
import org.clever.hinny.spring.utils.MergeRedisProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/11/16 21:25 <br/>
 */
@Order
@Configuration
@ConditionalOnClass({RedisProperties.class, RedisConnectionFactory.class, RedisDataSource.class, RedisDatabase.class})
@EnableConfigurationProperties({MultipleRedisConfig.class})
@Slf4j
public class AutoConfigureMultipleRedis implements CommandLineRunner {
    private final MultipleRedisConfig multipleRedisConfig;
    private final List<RedisConnectionFactory> redisConnectionFactoryList;
    private final ObjectMapper objectMapper;

    protected boolean initialized = false;

    public AutoConfigureMultipleRedis(ObjectProvider<List<RedisConnectionFactory>> redisConnectionFactoryList, MultipleRedisConfig multipleRedisConfig) {
        this.redisConnectionFactoryList = redisConnectionFactoryList.getIfAvailable();
        this.multipleRedisConfig = multipleRedisConfig;
        this.objectMapper = JacksonMapperSupport.getRedisJacksonMapper().getMapper();
    }

    @Override
    public synchronized void run(String... args) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (multipleRedisConfig.isDisable()) {
            return;
        }
        // 加入已存在的数据源
        if (redisConnectionFactoryList != null) {
            int index = 0;
            for (RedisConnectionFactory redisConnectionFactory : redisConnectionFactoryList) {
                index++;
                RedisDataSource redisDataSource = new RedisDataSource(redisConnectionFactory, objectMapper);
                RedisDatabase.Instance.add(String.format("autowired-redis-%s", index), redisDataSource);
            }
        }
        // 初始化配置的数据源
        final RedisProperties globalConfig = multipleRedisConfig.getGlobalConfig();
        final Map<String, RedisDataSource> destroyMap = new HashMap<>(multipleRedisConfig.getRedisMap().size());
        multipleRedisConfig.getRedisMap().forEach((name, redisConfig) -> {
            if (RedisDatabase.Instance.hasDataSource(name)) {
                throw new RuntimeException("redis数据源名称重复: " + name);
            }
            redisConfig = MergeRedisProperties.mergeConfig(globalConfig, redisConfig);
            RedisDataSource redisDataSource = new RedisDataSource(redisConfig, objectMapper);
            RedisDatabase.Instance.add(name, redisDataSource);
            destroyMap.put(name, redisDataSource);
        });
        if (!RedisDatabase.Instance.hasDataSource(multipleRedisConfig.getDefaultName())) {
            throw new RuntimeException("默认的redis数据源不存在,DefaultName: " + multipleRedisConfig.getDefaultName());
        }
        RedisDatabase.Instance.setDefault(multipleRedisConfig.getDefaultName());
        // 关闭连接池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> destroyMap.forEach((name, redisDataSource) -> {
            log.info("[" + name + "] RedisDataSource Destroy start...");
            try {
                redisDataSource.close();
                log.info("[" + name + "] RedisDataSource Destroy completed!");
            } catch (Throwable e) {
                log.info("[" + name + "] RedisDataSource Destroy error", e);
            }
        })));
    }
}
