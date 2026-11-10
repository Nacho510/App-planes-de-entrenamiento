package cr.ac.entrenos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

record LoginRequest(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=100) String password) {}
record ChangePasswordRequest(@NotBlank @Size(max=100) String currentPassword, @NotBlank @Size(min=12,max=72) String newPassword) {}
record NewAccountRequest(@NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email,
    @NotBlank @Size(min=12, max=72) String password) {}
record StepInput(@NotBlank @Pattern(regexp="GROUP|RUN|REST") String kind,
    @NotBlank @Size(max=120) String label, @Min(1) @Max(50) int repeatCount,
    @Positive @Digits(integer=8, fraction=2) BigDecimal distanceM, @Positive @Digits(integer=8, fraction=2) BigDecimal targetDurationS,
    @Positive @Digits(integer=8, fraction=2) BigDecimal paceMinSPerKm, @Positive @Digits(integer=8, fraction=2) BigDecimal paceMaxSPerKm,
    @Size(max=30) List<@NotNull @Valid StepInput> children) {}
record SessionInput(@NotNull Long athleteId, @NotNull LocalDate date, @NotBlank @Size(max=120) String title,
    @NotBlank @Pattern(regexp="EASY|FARTLEK|INTERVALS|STRENGTH|REST") String type,
    @Size(max=2000) String notes, @NotNull @Size(max=30) List<@NotNull @Valid StepInput> steps) {}
record AthleteSessionInput(@NotNull Long coachId, @NotNull LocalDate date, @NotBlank @Size(max=120) String title,
    @NotBlank @Pattern(regexp="EASY|FARTLEK|INTERVALS|STRENGTH|REST") String type,
    @Size(max=2000) String notes, @NotNull @Size(max=30) List<@NotNull @Valid StepInput> steps) {}
record RescheduleInput(@NotNull LocalDate date) {}
record ResultInput(@NotNull Long occurrenceId, @Positive @Digits(integer=8, fraction=2) BigDecimal durationS, @Positive @Digits(integer=8, fraction=2) BigDecimal distanceM) {}
record FeedbackInput(@Positive Integer durationS, @Positive @Digits(integer=8, fraction=2) BigDecimal distanceM,
    @Min(30) @Max(240) Integer avgHr, @Min(30) @Max(240) Integer maxHr,
    @Min(1) @Max(10) Integer rpe, @Size(max=2000) String notes,
    @NotNull @Size(max=500) List<@NotNull @Valid ResultInput> results) {}
