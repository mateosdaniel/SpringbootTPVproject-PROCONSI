package com.proconsi.electrobazar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@SpringBootTest
public class SqlRunnerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void runQuery() {
        String sql = "SELECT w.id, w.username, w.role_id, r.name as role_name FROM workers w LEFT JOIN roles r ON w.role_id = r.id LIMIT 10;";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        
        System.out.println("\n\n=== QUERY RESULT START ===");
        if (rows.isEmpty()) {
            System.out.println("No results found.");
        } else {
            for (Map<String, Object> row : rows) {
                System.out.printf("id: %s, username: %s, role_id: %s, role_name: %s%n",
                        row.get("id"), row.get("username"), row.get("role_id"), row.get("role_name"));
            }
        }
        System.out.println("=== QUERY RESULT END ===\n\n");
    }
}
