package org.clever.hinny.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/09/11 18:44 <br/>
 */
@ConfigurationProperties(prefix = Constant.Config_Root)
@Data
public class ScriptConfig implements Serializable {
    /**
     * 脚本文件路径
     */
    private String scriptPath;
    /**
     * 脚本文件监听配置
     */
    private FileWatcherConfig scriptFileWatcher = new FileWatcherConfig();

    @Data
    public static class FileWatcherConfig implements Serializable {
        /**
         * 是否启用实时动态加载Script文件
         */
        private boolean enableWatcher = true;
        /**
         * 文件检查时间间隔(默认3秒)
         */
        private Duration interval = Duration.ofSeconds(3);
        /**
         * 文件变化时刷新脚本引擎的频率
         */
        private Duration delayMillis = Duration.ofMillis(300);
        /**
         * 监听文件列表(白名单)<br />
         * 支持通配符(“?匹配一个字符”、“*匹配0个或多个字符”)
         */
        private Set<String> include = new HashSet<>();
        /**
         * 排除文件列表(黑名单)<br />
         * 支持通配符(“?匹配一个字符”、“*匹配0个或多个字符”)
         */
        private Set<String> exclude = new HashSet<>();
    }
}