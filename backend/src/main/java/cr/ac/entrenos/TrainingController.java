package cr.ac.entrenos;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api")
class TrainingController {
    private final TrainingService service;
    TrainingController(TrainingService service) { this.service=service; }
    @GetMapping("/athletes") List<Map<String,Object>> athletes(@AuthenticationPrincipal CurrentUser user) { return service.athletes(user); }
    @GetMapping("/coaches") List<Map<String,Object>> coaches(@AuthenticationPrincipal CurrentUser user) { return service.coaches(user); }
    @GetMapping("/sessions") List<Map<String,Object>> week(@AuthenticationPrincipal CurrentUser user, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required=false) Long athleteId) { return service.week(user,from,athleteId); }
    @GetMapping("/progress") List<Map<String,Object>> progress(@AuthenticationPrincipal CurrentUser user, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required=false) Long athleteId) { return service.progress(user,from,to,athleteId); }
    @PostMapping("/sessions") @ResponseStatus(HttpStatus.CREATED) Map<String,Long> create(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody SessionInput body) { return Map.of("id",service.create(user,body)); }
    @PostMapping("/my-sessions") @ResponseStatus(HttpStatus.CREATED) Map<String,Long> createOwn(@AuthenticationPrincipal CurrentUser user,@Valid @RequestBody AthleteSessionInput body) { return Map.of("id",service.createOwn(user,body)); }
    @DeleteMapping("/my-sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteOwn(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { service.deleteOwn(user,id); }
    @PatchMapping("/my-sessions/{id}/date") @ResponseStatus(HttpStatus.NO_CONTENT) void moveOwn(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@Valid @RequestBody RescheduleInput body) { service.moveOwn(user,id,body.date()); }
    @GetMapping("/sessions/{id}") Map<String,Object> detail(@AuthenticationPrincipal CurrentUser user,@PathVariable long id) { return service.detail(user,id); }
    @PutMapping("/sessions/{id}/feedback") @ResponseStatus(HttpStatus.NO_CONTENT) void feedback(@AuthenticationPrincipal CurrentUser user,@PathVariable long id,@Valid @RequestBody FeedbackInput body) { service.feedback(user,id,body); }
}
