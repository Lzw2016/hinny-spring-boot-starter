package org.clever.hinny.spring.autoconfigure;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOCase;
import org.clever.hinny.api.utils.Assert;
import org.clever.hinny.data.jdbc.dynamic.watch.FileSystemWatcher;
import org.clever.hinny.data.jdbc.mybatis.ClassPathMyBatisMapperSql;
import org.clever.hinny.data.jdbc.mybatis.FileSystemMyBatisMapperSql;
import org.clever.hinny.data.jdbc.mybatis.MyBatisMapperSql;
import org.clever.hinny.spring.config.Constant;
import org.clever.hinny.spring.config.FileSystemType;
import org.clever.hinny.spring.config.MyBatisMapperConfig;
import org.clever.hinny.spring.config.ScriptConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xml.sax.SAXParseException;

import java.io.File;
import java.util.Objects;

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

    @Bean("myBatisMapperSql")
    @ConditionalOnMissingBean
    public MyBatisMapperSql myBatisMapperSql(ObjectProvider<ScriptConfig> scriptConfig) {
        final FileSystemType fileSystemType = Objects.requireNonNull(scriptConfig.getIfAvailable()).getFileSystemType();
        MyBatisMapperSql myBatisMapperSql;
        if (Objects.equals(fileSystemType, FileSystemType.FileSystem)) {
            String scriptAbsolutePath = new File(myBatisMapperConfig.getMapperPath()).getAbsolutePath();
            log.info("#Mapper.xml文件绝对路径: {}", scriptAbsolutePath);
            myBatisMapperSql = new FileSystemMyBatisMapperSql(myBatisMapperConfig.getMapperPath());
        } else if (Objects.equals(fileSystemType, FileSystemType.Jar)) {
            log.info("#Mapper.xml文件classpath文件模式: {}", myBatisMapperConfig.getMapperPath());
            myBatisMapperSql = new ClassPathMyBatisMapperSql(myBatisMapperConfig.getMapperPath());
        } else {
            throw new IllegalArgumentException("配置fileSystemType错误：fileSystemType=" + fileSystemType);
        }
        return myBatisMapperSql;
    }

    @Bean("mapperFileWatcher")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = Constant.Config_MyBatis_Mapper_Config, name = "enable-watcher", havingValue = "true", matchIfMissing = true)
    public FileSystemWatcher mapperFileWatcher(MyBatisMapperSql mapperSql) {
        Assert.isTrue(mapperSql instanceof FileSystemMyBatisMapperSql, "当前MyBatisMapperSql类型[+" + mapperSql.getClass().getName() + "+]不支持监听文件变化");
        FileSystemMyBatisMapperSql fileSystemMyBatisMapperSql = (FileSystemMyBatisMapperSql) mapperSql;
        FileSystemWatcher watcher = new FileSystemWatcher(
                myBatisMapperConfig.getMapperPath(),
                file -> {
                    final String absPath = file.getAbsolutePath();
                    try {
                        fileSystemMyBatisMapperSql.reloadFile(absPath);
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