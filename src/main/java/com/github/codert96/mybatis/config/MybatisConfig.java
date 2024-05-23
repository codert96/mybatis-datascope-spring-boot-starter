package com.github.codert96.mybatis.config;

import com.github.codert96.mybatis.DataScopeInterceptor;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Interceptor.class)
public class MybatisConfig {

    @Bean
    public DataScopeInterceptor dataScopeInterceptor(DataSource dataSource) {
        return new DataScopeInterceptor(new NamedParameterJdbcTemplate(dataSource));
    }
}
