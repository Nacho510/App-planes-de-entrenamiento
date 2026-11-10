package cr.ac.entrenos;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;

class AccountServiceTest {
    private final JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource("jdbc:h2:mem:accounttest", "sa", "", true));
    private final AccountService accounts = new AccountService(db, new BCryptPasswordEncoder());

    @Test void athleteCannotCreateAccounts() {
        var athlete = new CurrentUser(2, "Atleta", "a@example.com", "ATHLETE");
        var input = new NewAccountRequest("Otro", "o@example.com", "clave-segura-123");
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> accounts.createAthlete(athlete, input)).getStatusCode().value());
    }

    @Test void rejectsPasswordLongerThanBcryptLimitInBytes() {
        var input = new NewAccountRequest("Coach", "c@example.com", "á".repeat(40));
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> accounts.registerCoach(input)).getStatusCode().value());
    }
    @Test void passwordChangeRevokesAllUserTokensAndRequiresCurrentPassword() {
        var source = new SingleConnectionDataSource("jdbc:h2:mem:passwordtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        var jdbc = new JdbcTemplate(source);
        var encoder = new BCryptPasswordEncoder();
        jdbc.update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES (?,?,?,?,?)", 11, "Atleta", "a@example.com", encoder.encode("contraseña-anterior"), "ATHLETE");
        var tokens = new Tokens(); var user = new CurrentUser(11,"Atleta","a@example.com","ATHLETE");
        var first = tokens.issue(user); var second = tokens.issue(user);
        var controller = new AuthController(jdbc,encoder,tokens);
        assertThrows(ResponseStatusException.class, () -> controller.changePassword(user,new ChangePasswordRequest("incorrecta", "contraseña-nueva")));
        assertNotNull(tokens.resolve(first));
        controller.changePassword(user,new ChangePasswordRequest("contraseña-anterior", "contraseña-nueva"));
        assertNull(tokens.resolve(first)); assertNull(tokens.resolve(second));
        assertTrue(encoder.matches("contraseña-nueva", jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=11",String.class)));
    }
}
