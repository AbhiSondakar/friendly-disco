package com.ecoloop;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

// @SpringBootApplication
public class DbCheck {
    public static void main(String[] args) {
        // SpringApplication.run(DbCheck.class, args);
    }
    // @Bean
    public CommandLineRunner run(JdbcTemplate jdbc) {
        return args -> {
            List<Map<String, Object>> pickups = jdbc.queryForList("SELECT id, device_id, status FROM pickup_requests ORDER BY created_at DESC LIMIT 5");
            System.out.println("=== PICKUPS ===");
            for (Map<String, Object> p : pickups) {
                System.out.println(p.get("id") + " | " + p.get("status") + " | device_id=" + p.get("device_id"));
                List<Map<String, Object>> devices = jdbc.queryForList("SELECT category, ai_category FROM devices WHERE id = ?", p.get("device_id"));
                if (!devices.isEmpty()) {
                    System.out.println("  -> Device: cat=" + devices.get(0).get("CATEGORY") + " aiCat=" + devices.get(0).get("AI_CATEGORY"));
                }
            }
            List<Map<String, Object>> partners = jdbc.queryForList("SELECT id, status, capabilities FROM partners WHERE status = 'approved' LIMIT 5");
            System.out.println("=== PARTNERS ===");
            for (Map<String, Object> p : partners) {
                System.out.println(p.get("id") + " | " + p.get("status") + " | caps=" + p.get("CAPABILITIES"));
            }
            System.exit(0);
        };
    }
}
