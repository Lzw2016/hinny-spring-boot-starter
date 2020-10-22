package org.clever.hinny.spring.autoconfigure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.clever.hinny.data.jdbc.JdbcDataSource;
import org.clever.hinny.data.jdbc.MyBatisJdbcDataSource;
import org.clever.hinny.data.jdbc.mybatis.MyBatisMapperSql;
import org.clever.hinny.graal.data.jdbc.JdbcDatabase;
import org.clever.hinny.graal.data.jdbc.MyBatisJdbcDatabase;
import org.clever.hinny.graal.meta.data.MateDataManage;
import org.clever.hinny.spring.config.MultipleDataSourceConfig;
import org.clever.hinny.spring.utils.MergeDataSourceConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;
import java.util.*;

/**
 * 作者：lizw <br/>
 * 创建时间：2020/10/03 19:27 <br/>
 */
@Order
@Configuration
@AutoConfigureAfter({AutoConfigureMyBatisMapperSql.class})
@EnableConfigurationProperties({MultipleDataSourceConfig.class})
@Slf4j
public class AutoConfigureMultipleDataSource implements CommandLineRunner {
    private final boolean CanInit_JdbcDataSource = canInitJdbcDataSource();
    private final boolean CanInit_JdbcDatabase = canInitJdbcDatabase();
    private final boolean CanInit_MyBatisJdbcDatabase = canInitMyBatisJdbcDatabase();
    private final boolean CanInit_MateDataManage = canInitMateDataManage();
    private final boolean Exists_HikariDataSource = existsHikariDataSource();

    private final List<DataSource> dataSourceList = new ArrayList<>();
    private final MultipleDataSourceConfig multipleDataSourceConfig;
    private final MyBatisMapperSql mybatisMapperSql;

    protected boolean initialized = false;

    public AutoConfigureMultipleDataSource(
            ObjectProvider<DataSource> dataSourceList,
            ObjectProvider<MultipleDataSourceConfig> multipleDataSourceConfig,
            ObjectProvider<MyBatisMapperSql> mybatisMapperSql) {
        for (DataSource dataSource : dataSourceList) {
            this.dataSourceList.add(dataSource);
        }
        this.multipleDataSourceConfig = multipleDataSourceConfig.getIfAvailable() == null ? new MultipleDataSourceConfig() : multipleDataSourceConfig.getIfAvailable();
        this.mybatisMapperSql = mybatisMapperSql.getIfAvailable();
    }

    @Override
    public void run(String... args) {
        if (initialized) {
            return;
        }
        initialized = true;
        if (multipleDataSourceConfig.isDisable()) {
            return;
        }
        if (!Exists_HikariDataSource) {
            log.info("缺少依赖 com.zaxxer:HikariCP");
            return;
        }
        if (!CanInit_JdbcDataSource) {
            log.warn("无法初始化hinny jdbc模块，缺少class lib: graaljs-data-jdbc");
            return;
        }
        int dataSourceCount = multipleDataSourceConfig.getJdbcMap().size() + dataSourceList.size();
        final Map<String, DataSource> dataSourceMap = new HashMap<>(dataSourceCount);
        // 加入已存在的数据源
        for (DataSource dataSource : dataSourceList) {
            String name = null;
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource tmp = (HikariDataSource) dataSource;
                name = tmp.getPoolName();
            }
            if (StringUtils.isBlank(name)) {
                name = dataSource.toString();
            }
            if (dataSourceMap.containsKey(name)) {
                throw new RuntimeException("JdbcDataSource 名称重复: " + name);
            }
            dataSourceMap.put(name, dataSource);
            if (StringUtils.isBlank(multipleDataSourceConfig.getDefaultName())) {
                multipleDataSourceConfig.setDefaultName(name);
            }
        }
        if (StringUtils.isBlank(multipleDataSourceConfig.getDefaultName())) {
            throw new RuntimeException("默认的数据源名称 defaultName 不能是空");
        }
        // 初始化配置的数据源
        final HikariConfig dataSourceGlobalConfig = multipleDataSourceConfig.getGlobalConfig();
        multipleDataSourceConfig.getJdbcMap().forEach((name, hikariConfig) -> {
            if (dataSourceMap.containsKey(name)) {
                throw new RuntimeException("JdbcDataSource 名称重复: " + name);
            }
            hikariConfig = MergeDataSourceConfig.mergeConfig(dataSourceGlobalConfig, hikariConfig);
            if (StringUtils.isBlank(hikariConfig.getPoolName())) {
                hikariConfig.setPoolName(name);
            }
            HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);
            dataSourceMap.put(name, hikariDataSource);
        });
        final Map<String, DataSource> result = Collections.unmodifiableMap(dataSourceMap);
        // 关闭连接池
        Runtime.getRuntime().addShutdownHook(new Thread(() -> result.forEach((name, dataSource) -> {
            if (dataSource instanceof HikariDataSource) {
                HikariDataSource tmp = (HikariDataSource) dataSource;
                tmp.close();
            }
            // 其他类型的连接池也要关闭连接池
        })));
        // 初始化 JdbcDatabase、MyBatisJdbcDatabase、MateDataManage
        for (Map.Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            String name = entry.getKey();
            DataSource dataSource = entry.getValue();
            JdbcDataSource jdbcDataSource = new JdbcDataSource(dataSource);
            if (CanInit_JdbcDatabase) {
                JdbcDatabase.Instance.add(name, jdbcDataSource);
                log.info("初始化 JdbcDatabase: {}", name);
            }
            if (CanInit_MyBatisJdbcDatabase && mybatisMapperSql != null) {
                MyBatisJdbcDataSource myBatisJdbcDataSource = new MyBatisJdbcDataSource(jdbcDataSource, mybatisMapperSql);
                MyBatisJdbcDatabase.Instance.add(name, myBatisJdbcDataSource);
                log.info("初始化 MyBatisJdbcDatabase: {}", name);
            }
            if (CanInit_MateDataManage) {
                MateDataManage.Instance.add(name, dataSource);
                log.info("初始化 MateDataManage: {}", name);
            }
        }
        if (CanInit_JdbcDatabase) {
            JdbcDatabase.Instance.setDefault(multipleDataSourceConfig.getDefaultName());
            log.info("默认的 JdbcDatabase: {}", JdbcDatabase.Instance.getDefaultName());
        }
        if (CanInit_MyBatisJdbcDatabase && mybatisMapperSql != null) {
            MyBatisJdbcDatabase.Instance.setDefault(multipleDataSourceConfig.getDefaultName());
            log.info("默认的 MyBatisJdbcDatabase: {}", multipleDataSourceConfig.getDefaultName());
        }
        if (CanInit_MateDataManage) {
            MateDataManage.Instance.setDefault(multipleDataSourceConfig.getDefaultName());
            log.info("默认的 MateDataManage: {}", multipleDataSourceConfig.getDefaultName());
        }
    }

    protected static boolean existsHikariDataSource() {
        try {
            Class.forName("com.zaxxer.hikari.HikariDataSource");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected static boolean canInitJdbcDataSource() {
        try {
            Class.forName("org.clever.hinny.graal.data.jdbc.JdbcDataSource");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected static boolean canInitJdbcDatabase() {
        try {
            Class.forName("org.clever.hinny.graal.data.jdbc.JdbcDatabase");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected static boolean canInitMyBatisJdbcDatabase() {
        try {
            Class.forName("org.clever.hinny.graal.data.jdbc.MyBatisJdbcDatabase");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    protected static boolean canInitMateDataManage() {
        try {
            Class.forName("org.clever.hinny.graal.meta.data.MateDataManage");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
