package cr.ac.entrenos;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class AccountController {
    private final AccountService accounts;
    AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/auth/register-coach") @ResponseStatus(HttpStatus.CREATED)
    void registerCoach(@Valid @RequestBody NewAccountRequest body) { accounts.registerCoach(body); }

    @PostMapping("/athletes") @ResponseStatus(HttpStatus.CREATED)
    void createAthlete(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NewAccountRequest body) {
        accounts.createAthlete(user, body);
    }
}
