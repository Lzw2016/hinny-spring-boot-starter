package org.clever.hinny.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * mvc配置
 * <p>
 * 作者：lizw <br/>
 * 创建时间：2020/09/11 19:10 <br/>
 */
@ConfigurationProperties(prefix = Constant.Config_Mvc_Handler)
@Data
public class ScriptMvcHandlerConfig implements Serializable {
    /**
     * “请求路径”和“脚本路径”映射规则<br />
     * 路径处理时使用“脚本路径”替换“请求路径”<br />
     */
    @NestedConfigurationProperty
    private List<PrefixMapping> prefixMappings = new ArrayList<PrefixMapping>() {{
        add(new PrefixMapping("/!/", ""));
    }};

    /**
     * Script Mvc Handler支持的请求后缀
     */
    private Set<String> supportSuffix = new HashSet<String>(3) {{
        add("");
        add(".json");
        add(".action'");
    }};

    @Data
    public static class PrefixMapping implements Serializable {
        /**
         * 请求路径
         */
        private String requestPath;
        /**
         * 脚本路径
         */
        private String scriptPath;

        public PrefixMapping() {
        }

        public PrefixMapping(String requestPath, String scriptPath) {
            this.requestPath = requestPath;
            this.scriptPath = scriptPath;
        }
    }
}
