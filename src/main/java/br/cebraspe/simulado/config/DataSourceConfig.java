package br.cebraspe.simulado.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.Bean;
import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}