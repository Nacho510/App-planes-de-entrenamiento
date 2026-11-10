package cr.ac.entrenos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Arrays;
import jakarta.validation.Validation;
import static org.junit.jupiter.api.Assertions.*;

class TrainingServiceTest {
    JdbcTemplate db; TrainingService service;
    CurrentUser coach = new CurrentUser(1,"Coach","c@example.com","COACH");
    CurrentUser athlete = new CurrentUser(2,"Athlete","a@example.com","ATHLETE");
    @BeforeEach void setup() {
        var source=new SingleConnectionDataSource("jdbc:h2:mem:"+System.nanoTime()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","",true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        db=new JdbcTemplate(source); service=new TrainingService(db);
        db.update("INSERT INTO app_user(id,name,email,password_hash,role) VALUES (1,'Coach','c@example.com','hash','COACH'),(2,'Athlete','a@example.com','hash','ATHLETE'),(3,'Other','o@example.com','hash','ATHLETE')");
        db.update("INSERT INTO coaching_relationship(coach_id,athlete_id) VALUES (1,2)");
        db.update("INSERT INTO planned_session(id,coach_id,athlete_id,session_date,title,type) VALUES (10,1,2,'2026-11-09','Series','INTERVALS')");
    }
    @Test void athleteCannotPlan() {
        var request=new SessionInput(2L,LocalDate.now(),"Test","EASY",null,List.of());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.create(athlete,request)).getStatusCode().value());
    }
    @Test void onlyAthleteCanRemoveTheirOwnSessionAndItsFeedback() {
        db.update("INSERT INTO session_execution(session_id,athlete_id,duration_s) VALUES (10,2,120)");
        var other = new CurrentUser(3,"Other","o@example.com","ATHLETE");
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.deleteOwn(other,10)).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.deleteOwn(coach,10)).getStatusCode().value());
        service.deleteOwn(athlete,10);
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM planned_session WHERE id=10",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM session_execution WHERE session_id=10",Integer.class));
    }
    @Test void athleteCannotCreateSessionForUnrelatedCoach() {
        var body = new AthleteSessionInput(99L,LocalDate.now(),"Rodaje","EASY",null,List.of());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.createOwn(athlete,body)).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.createOwn(coach,body)).getStatusCode().value());
    }
    @Test void athleteCanMoveOnlyTheirSessionAndKeepFeedback() {
        db.update("INSERT INTO session_execution(session_id,athlete_id,duration_s) VALUES (10,2,120)");
        var date=LocalDate.of(2026,11,12);
        var other=new CurrentUser(3,"Other","o@example.com","ATHLETE");
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.moveOwn(other,10,date)).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.moveOwn(coach,10,date)).getStatusCode().value());
        service.moveOwn(athlete,10,date);
        assertEquals(date,db.queryForObject("SELECT session_date FROM planned_session WHERE id=10",LocalDate.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM session_execution WHERE session_id=10",Integer.class));
    }
    @Test void coachCannotAssignUnrelatedAthlete() {
        var request=new SessionInput(3L,LocalDate.now(),"Test","EASY",null,List.of());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.create(coach,request)).getStatusCode().value());
    }
    @Test void limitsExpansionBeforeWriting() {
        var run=new StepInput("RUN","Series",50,null,null,null,null,List.of());
        var group=new StepInput("GROUP","Bloque",11,null,null,null,null,List.of(run));
        var request=new SessionInput(2L,LocalDate.now(),"Test","INTERVALS",null,List.of(group));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.create(coach,request)).getStatusCode().value());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM planned_session",Integer.class));
    }
    @Test void rejectsOccurrenceOutsideSession() {
        var feedback=new FeedbackInput(300,null,140,180,5,null,List.of(new ResultInput(999L,new BigDecimal("52.5"),null)));
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service.feedback(athlete,10,feedback)).getStatusCode().value());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM session_execution",Integer.class));
    }
    @Test void anotherAthleteCannotReadSession() {
        var other=new CurrentUser(3,"Other","o@example.com","ATHLETE");
        assertEquals(404,assertThrows(ResponseStatusException.class,()->service.detail(other,10)).getStatusCode().value());
    }
    @Test void progressRejectsAnotherAthleteAndExcessiveRanges() {
        var other = new CurrentUser(3,"Other","o@example.com","ATHLETE");
        var day = LocalDate.of(2026,11,9);
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.progress(other,day,day,2L)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.progress(coach,day,day,3L)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.progress(athlete,day,day.plusDays(366),null)).getStatusCode().value());
        assertEquals(1, service.progress(coach,day,day,2L).size());
    }
    @Test void rejectsEmptyFeedbackWithoutMarkingSessionComplete() {
        var feedback = new FeedbackInput(null, null, null, null, null, "  ", List.of());
        assertEquals(400, assertThrows(ResponseStatusException.class,
            () -> service.feedback(athlete, 10, feedback)).getStatusCode().value());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM session_execution", Integer.class));
    }
    @Test void rejectsNullResultsAndValuesOutsideDatabasePrecision() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var nullResult = new FeedbackInput(null, null, null, null, null, null, Arrays.asList((ResultInput) null));
            var oversizedValue = new FeedbackInput(null, new BigDecimal("100000000.00"), null, null, null, null, List.of());
            assertFalse(validator.validate(nullResult).isEmpty());
            assertFalse(validator.validate(oversizedValue).isEmpty());
        }
    }
}
