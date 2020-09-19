package org.clever.hinny.spring.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOCase;
import org.clever.hinny.data.jdbc.dynamic.MyBatisMapperSql;
import org.clever.hinny.data.jdbc.dynamic.watch.FileSystemWatcher;
import org.clever.hinny.spring.config.Constant;
import org.clever.hinny.spring.config.MyBatisMapperConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xml.sax.SAXParseException;

import java.io.File;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/09/11 19:15 <br/>
 */
@Configuration
@EnableConfigurationProperties({MyBatisMapperConfig.class})
@Slf4j
public class AutoConfigureMyBatisMapperSql {
    private final MyBatisMapperConfig myBatisMapperConfig;

    public AutoConfigureMyBatisMapperSql(MyBatisMapperConfig myBatisMapperConfig) {
        this.myBatisMapperConfig = myBatisMapperConfig;
    }

    @Bean
    @ConditionalOnMissingBean
    public MyBatisMapperSql myBatisMapperSql() {
        return new MyBatisMapperSql(myBatisMapperConfig.getMapperPath());
    }

    @Bean("mapperFileWatcher")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = Constant.Config_MyBatis_Mapper_Config, name = "enable-watcher", havingValue = "true", matchIfMissing = true)
    public FileSystemWatcher fileSystemWatcher(MyBatisMapperSql mapperSql) {
        FileSystemWatcher watcher = new FileSystemWatcher(
                myBatisMapperConfig.getMapperPath(),
                file -> {
                    final String absPath = file.getAbsolutePath();
                    try {
                        mapperSql.reloadFile(absPath);
                    } catch (Exception e) {
                        String error = e.getMessage();
                        if (e.getCause() instanceof SAXParseException) {
                            SAXParseException saxParseException = (SAXParseException) e.getCause();
                            error = String.format(
                                    "#第%d行，第%d列存在错误: %s",
                                    saxParseException.getLineNumber(),
                                    saxParseException.getColumnNumber(),
                                    saxParseException.getMessage()
                            );
                        }
                        log.error("#重新加载Mapper.xml文件失败 | path={} | error={}", absPath, error);
                    }
                },
                myBatisMapperConfig.getInclude(),
                myBatisMapperConfig.getExclude(),
                IOCase.SYSTEM,
                myBatisMapperConfig.getInterval().toMillis()
        );
        if (myBatisMapperConfig.isEnableWatcher()) {
            final String mapperAbsolutePath = new File(myBatisMapperConfig.getMapperPath()).getAbsolutePath();
            watcher.start();
            log.info("#已监听Mapper.xml文件，绝对路径: {}", mapperAbsolutePath);
        }
        return watcher;
    }
}