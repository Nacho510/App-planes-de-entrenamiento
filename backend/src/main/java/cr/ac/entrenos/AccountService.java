package cr.ac.entrenos;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountService {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    AccountService(JdbcTemplate db, PasswordEncoder encoder) { this.db = db; this.encoder = encoder; }

    @Transactional
    void registerCoach(NewAccountRequest input) { create(input, "COACH"); }

    @Transactional
    void createAthlete(CurrentUser coach, NewAccountRequest input) {
        if (!"COACH".equals(coach.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        long athleteId = create(input, "ATHLETE");
        db.update("INSERT INTO coaching_relationship(coach_id,athlete_id) VALUES (?,?)", coach.id(), athleteId);
    }

    private long create(NewAccountRequest input, String role) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña excede 72 bytes");
        String email = input.email().trim().toLowerCase(Locale.ROOT);
        try {
            return db.queryForObject("INSERT INTO app_user(name,email,password_hash,role) VALUES (?,?,?,?) RETURNING id",
                Long.class, input.name().trim(), email, encoder.encode(input.password()), role);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo crear la cuenta");
        }
    }
}
