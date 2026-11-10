package cr.ac.entrenos;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController @RequestMapping("/api/auth")
class AuthController {
    private final JdbcTemplate db; private final PasswordEncoder encoder; private final Tokens tokens;
    AuthController(JdbcTemplate db, PasswordEncoder encoder, Tokens tokens) { this.db=db; this.encoder=encoder; this.tokens=tokens; }
    @PostMapping("/login") Map<String,Object> login(@Valid @RequestBody LoginRequest input) {
        record Credential(CurrentUser user, String hash) {}
        List<Credential> found = db.query("SELECT id,name,email,role,password_hash FROM app_user WHERE email=?", (rs,n) -> new Credential(new CurrentUser(rs.getLong("id"),rs.getString("name"),rs.getString("email"),rs.getString("role")),rs.getString("password_hash")), input.email().trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty() || !encoder.matches(input.password(), found.getFirst().hash())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
        return Map.of("token", tokens.issue(found.getFirst().user()), "user", found.getFirst().user());
    }
    @GetMapping("/me") CurrentUser me(@AuthenticationPrincipal CurrentUser user) { return user; }
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ChangePasswordRequest input) {
        if (input.newPassword().getBytes(StandardCharsets.UTF_8).length > 72 || input.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String hash = db.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, user.id());
        if (!encoder.matches(input.currentPassword(), hash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo cambiar la contraseña");
        if (encoder.matches(input.newPassword(), hash)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige una contraseña distinta");
        int updated = db.update("UPDATE app_user SET password_hash=? WHERE id=? AND password_hash=?", encoder.encode(input.newPassword()), user.id(), hash);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT);
        tokens.revokeUser(user.id());
    }
    @PostMapping("/logout") void logout(@RequestHeader("Authorization") String header) { if (header.startsWith("Bearer ")) tokens.revoke(header.substring(7)); }
}
