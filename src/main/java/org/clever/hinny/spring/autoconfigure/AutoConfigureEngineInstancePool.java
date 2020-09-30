package org.clever.hinny.spring.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOCase;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.clever.hinny.api.ScriptEngineInstance;
import org.clever.hinny.api.folder.ClassPathFolder;
import org.clever.hinny.api.folder.FileSystemFolder;
import org.clever.hinny.api.folder.Folder;
import org.clever.hinny.api.pool.EngineInstancePool;
import org.clever.hinny.api.pool.GenericEngineInstancePool;
import org.clever.hinny.api.utils.Assert;
import org.clever.hinny.api.watch.FileSystemWatcher;
import org.clever.hinny.graaljs.pool.GraalSingleEngineFactory;
import org.clever.hinny.spring.config.Constant;
import org.clever.hinny.spring.config.FileSystemType;
import org.clever.hinny.spring.config.ScriptConfig;
import org.clever.hinny.spring.config.ScriptEnginePoolConfig;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.Objects;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/09/11 19:14 <br/>
 */
@Configuration
@EnableConfigurationProperties({ScriptEnginePoolConfig.class, ScriptConfig.class})
@Slf4j
public class AutoConfigureEngineInstancePool {
    private final ScriptEnginePoolConfig scriptEnginePoolConfig;
    private final ScriptConfig scriptConfig;
    /**
     * 脚本文件绝对路径
     */
    private final String scriptAbsolutePath;

    public AutoConfigureEngineInstancePool(ScriptConfig scriptConfig, ScriptEnginePoolConfig scriptEnginePoolConfig) {
        this.scriptEnginePoolConfig = scriptEnginePoolConfig;
        this.scriptConfig = scriptConfig;
        this.scriptAbsolutePath = new File(scriptConfig.getScriptPath()).getAbsolutePath();
    }

    @Bean("scriptFolder")
    @ConditionalOnMissingBean
    public Folder scriptFolder() {
        Folder folder;
        if (Objects.equals(scriptConfig.getFileSystemType(), FileSystemType.FileSystem)) {
            log.info("#脚本文件绝对路径: {}", scriptAbsolutePath);
            folder = FileSystemFolder.createRootPath(scriptConfig.getScriptPath());
        } else if (Objects.equals(scriptConfig.getFileSystemType(), FileSystemType.Jar)) {
            log.info("#脚本文件classpath文件模式: {}", "classpath:**/*.*");
            folder = ClassPathFolder.createRootPath("classpath:**/*.*", scriptConfig.getScriptPath());
        } else {
            throw new IllegalArgumentException("配置fileSystemType错误：fileSystemType=" + scriptConfig.getFileSystemType());
        }
        return folder;
    }

    @Bean("pooledObjectFactory")
    @ConditionalOnMissingBean
    public BasePooledObjectFactory<ScriptEngineInstance<Context, Value>> pooledObjectFactory(Folder rootFolder) {
        Engine engine = Engine.newBuilder()
                .useSystemProperties(true)
                .build();
        return new GraalSingleEngineFactory(rootFolder, engine);
    }

    @Bean("engineInstancePool")
    @ConditionalOnMissingBean
    public EngineInstancePool<Context, Value> engineInstancePool(BasePooledObjectFactory<ScriptEngineInstance<Context, Value>> graalEngineFactory) {
        // 创建对象池配置
        GenericObjectPoolConfig<ScriptEngineInstance<Context, Value>> config = new GenericObjectPoolConfig<>();
        config.setLifo(scriptEnginePoolConfig.isLifo());
        config.setFairness(scriptEnginePoolConfig.isFairness());
        config.setMaxIdle(scriptEnginePoolConfig.getMaxIdle());
        config.setMinIdle(scriptEnginePoolConfig.getMinIdle());
        config.setMaxTotal(scriptEnginePoolConfig.getMaxTotal());
        config.setMaxWaitMillis(scriptEnginePoolConfig.getMaxWaitMillis());
        config.setBlockWhenExhausted(scriptEnginePoolConfig.isBlockWhenExhausted());
        config.setTestOnCreate(scriptEnginePoolConfig.isTestOnCreate());
        config.setTestOnBorrow(scriptEnginePoolConfig.isTestOnBorrow());
        config.setTestOnReturn(scriptEnginePoolConfig.isTestOnReturn());
        config.setTestWhileIdle(scriptEnginePoolConfig.isTestWhileIdle());
        config.setTimeBetweenEvictionRunsMillis(scriptEnginePoolConfig.getTimeBetweenEvictionRunsMillis());
        config.setNumTestsPerEvictionRun(scriptEnginePoolConfig.getNumTestsPerEvictionRun());
        config.setMinEvictableIdleTimeMillis(scriptEnginePoolConfig.getMinEvictableIdleTimeMillis());
        config.setSoftMinEvictableIdleTimeMillis(scriptEnginePoolConfig.getSoftMinEvictableIdleTimeMillis());
        config.setEvictionPolicyClassName(scriptEnginePoolConfig.getEvictionPolicyClassName());
        config.setEvictorShutdownTimeoutMillis(scriptEnginePoolConfig.getEvictorShutdownTimeoutMillis());
        config.setJmxEnabled(scriptEnginePoolConfig.isJmxEnabled());
        config.setJmxNamePrefix(scriptEnginePoolConfig.getJmxNamePrefix());
        config.setJmxNameBase(scriptEnginePoolConfig.getJmxNameBase());
        return new GenericEngineInstancePool<>(graalEngineFactory, config);
    }

    @Bean("scriptFileWatcher")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = Constant.Config_Root, name = "script-file-watcher.enable-watcher", havingValue = "true", matchIfMissing = true)
    public FileSystemWatcher scriptFileWatcher(EngineInstancePool<Context, Value> pool) {
        Assert.isTrue(
                Objects.equals(scriptConfig.getFileSystemType(), FileSystemType.FileSystem),
                "当前FileSystemType[" + scriptConfig.getFileSystemType() + "]不支持监听文件变化"
        );
        FileSystemWatcher fileSystemWatcher = new FileSystemWatcher(
                scriptConfig.getScriptPath(),
                scriptConfig.getScriptFileWatcher().getInclude(),
                scriptConfig.getScriptFileWatcher().getExclude(),
                IOCase.SYSTEM,
                event -> {
                    log.info("#文件发生变化 | [{}] -> [{}]", event.getEventType(), event.getFileOrDir().getAbsolutePath());
                    try {
                        pool.clear();
                    } catch (Exception e) {
                        log.warn("清空脚本引擎池失败", e);
                    }
                },
                scriptConfig.getScriptFileWatcher().getInterval().toMillis(),
                scriptConfig.getScriptFileWatcher().getDelayMillis().toMillis()
        );
        if (scriptConfig.getScriptFileWatcher().isEnableWatcher()) {
            fileSystemWatcher.start();
            log.info("#已监听脚本文件，绝对路径: {}", scriptAbsolutePath);
        }
        return fileSystemWatcher;
    }
}
