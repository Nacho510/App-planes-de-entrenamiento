package cr.ac.entrenos;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Component
class Bootstrap implements ApplicationRunner {
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final Environment env;
    Bootstrap(JdbcTemplate db, PasswordEncoder encoder, Environment env) { this.db = db; this.encoder = encoder; this.env = env; }
    @Override @Transactional public void run(ApplicationArguments args) {
        String cp = env.getProperty("DEMO_COACH_PASSWORD"), ap = env.getProperty("DEMO_ATHLETE_PASSWORD");
        if (cp == null && ap == null) return;
        if (cp == null || ap == null || cp.length() < 12 || ap.length() < 12 || cp.equals(ap))
            throw new IllegalStateException("Both demo passwords must be different and at least 12 characters.");
        create("Entrenador", "coach@entrenos.local", cp, "COACH");
        create("Atleta", "atleta@entrenos.local", ap, "ATHLETE");
        db.update("INSERT INTO coaching_relationship(coach_id,athlete_id) SELECT c.id,a.id FROM app_user c,app_user a WHERE c.email=? AND a.email=? AND NOT EXISTS (SELECT 1 FROM coaching_relationship r WHERE r.coach_id=c.id AND r.athlete_id=a.id)", "coach@entrenos.local", "atleta@entrenos.local");
    }
    private void create(String name, String email, String password, String role) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM app_user WHERE email=?", Integer.class, email);
        if (count != null && count == 0) db.update("INSERT INTO app_user(name,email,password_hash,role) VALUES (?,?,?,?)", name, email.toLowerCase(Locale.ROOT), encoder.encode(password), role);
    }
}
