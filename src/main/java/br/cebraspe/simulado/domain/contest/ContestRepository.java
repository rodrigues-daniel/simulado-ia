package br.cebraspe.simulado.domain.contest;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class ContestRepository {

    private final JdbcClient jdbcClient;

    public ContestRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Contest> findAll() {
        return jdbcClient.sql("""
                SELECT id, name, organ, role, year, level,
                       is_default, created_at
                FROM contests ORDER BY is_default DESC, year DESC
                """)
                .query(Contest.class)
                .list();
    }

    public Optional<Contest> findDefault() {
        return jdbcClient.sql("""
                SELECT id, name, organ, role, year, level,
                       is_default, created_at
                FROM contests WHERE is_default = TRUE LIMIT 1
                """)
                .query(Contest.class)
                .optional();
    }

    public Optional<Contest> findById(Long id) {
        return jdbcClient.sql("""
                SELECT id, name, organ, role, year, level,
                       is_default, created_at
                FROM contests WHERE id = :id
                """)
                .param("id", id)
                .query(Contest.class)
                .optional();
    }

    public Contest save(Contest contest) {
        if (contest.id() == null) {
            var id = jdbcClient.sql("""
                    INSERT INTO contests (name, organ, role, year, level, is_default)
                    VALUES (:name, :organ, :role, :year, :level, :isDefault)
                    RETURNING id
                    """)
                    .param("name", contest.name())
                    .param("organ", contest.organ())
                    .param("role", contest.role())
                    .param("year", contest.year())
                    .param("level", contest.level())
                    .param("isDefault", contest.isDefault())
                    .query(Long.class)
                    .single();
            return findById(id).orElseThrow();
        }
        jdbcClient.sql("""
                UPDATE contests SET name=:name, organ=:organ, role=:role,
                year=:year, level=:level, is_default=:isDefault WHERE id=:id
                """)
                .param("name", contest.name())
                .param("organ", contest.organ())
                .param("role", contest.role())
                .param("year", contest.year())
                .param("level", contest.level())
                .param("isDefault", contest.isDefault())
                .param("id", contest.id())
                .update();
        return findById(contest.id()).orElseThrow();
    }
}