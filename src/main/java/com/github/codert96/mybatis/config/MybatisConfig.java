package com.github.codert96.mybatis.config;

import com.github.codert96.mybatis.DataScopeInterceptor;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(MybatisAutoConfiguration.class)
public class MybatisConfig {

    @Bean
    public DataScopeInterceptor dataScopeInterceptor(JdbcOperations jdbcOperations) {
        return new DataScopeInterceptor(new NamedParameterJdbcTemplate(jdbcOperations));
    }
}
