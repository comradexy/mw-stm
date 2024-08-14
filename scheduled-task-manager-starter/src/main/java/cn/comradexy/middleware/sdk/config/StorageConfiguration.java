package cn.comradexy.middleware.sdk.config;

import cn.comradexy.middleware.sdk.support.storage.IStorageService;
import cn.comradexy.middleware.sdk.support.storage.jdbc.JdbcStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存储服务配置
 *
 * @Author: ComradeXY
 * @CreateTime: 2024-08-13
 * @Description: 存储服务配置
 */
@Configuration
@ConditionalOnProperty(prefix = "comradexy.middleware.scheudle", name = "enableStorage", havingValue = "true")
public class StorageConfiguration {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final EasyCronSchedulerProperties properties;

    @Autowired
    public StorageConfiguration(EasyCronSchedulerProperties properties) {
        this.properties = properties;
    }

    @Bean("comradexy-middleware-storage-service")
    public IStorageService storageService() {
        if (properties.getStorageType().equals(EasyCronSchedulerProperties.StorageType.JDBC.getValue())) {
            logger.info("JDBC 存储服务初始化");
            return new JdbcStorageService();
        } else if (properties.getStorageType().equals(EasyCronSchedulerProperties.StorageType.REDIS.getValue())) {
            // TODO: Redis存储服务
            logger.warn("暂不支持的存储类型：{}", properties.getStorageType());
            return null;
        } else {
            logger.warn("未知的存储类型：{}", properties.getStorageType());
            return null;
        }
    }
}
