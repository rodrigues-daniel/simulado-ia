package br.cebraspe.simulado.config;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public class SystemConfigRepository {

    private final JdbcClient jdbcClient;

    public SystemConfigRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<String> get(String key) {
        return jdbcClient.sql("""
                SELECT value FROM system_config WHERE key = :key
                """)
                .param("key", key)
                .query(String.class)
                .optional();
    }

    public void set(String key, String value) {
        jdbcClient.sql("""
                INSERT INTO system_config (key, value, updated_at)
                VALUES (:key, :value, NOW())
                ON CONFLICT (key) DO UPDATE
                SET value = EXCLUDED.value, updated_at = NOW()
                """)
                .param("key",   key)
                .param("value", value)
                .update();
    }

    public int getInt(String key, int defaultValue) {
        return get(key).map(v -> {
            try { return Integer.parseInt(v); }
            catch (NumberFormatException e) { return defaultValue; }
        }).orElse(defaultValue);
    }

    public record ConfigEntry(String key, String value, String description) {}

    public java.util.List<ConfigEntry> findAll() {
        return jdbcClient.sql("""
                SELECT key, value, description
                FROM system_config ORDER BY key
                """)
                .query(ConfigEntry.class)
                .list();
    }
}