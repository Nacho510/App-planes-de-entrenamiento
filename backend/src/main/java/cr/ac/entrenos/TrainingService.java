package cr.ac.entrenos;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
class TrainingService {
    private final JdbcTemplate db;
    TrainingService(JdbcTemplate db) { this.db = db; }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void coach(CurrentUser user) { if (!user.role().equals("COACH")) throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    private void athlete(CurrentUser user) { if (!user.role().equals("ATHLETE")) throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
    List<Map<String,Object>> athletes(CurrentUser user) {
        coach(user); return db.queryForList("SELECT u.id,u.name,u.email FROM app_user u JOIN coaching_relationship r ON r.athlete_id=u.id WHERE r.coach_id=? ORDER BY u.name", user.id());
    }
    List<Map<String,Object>> coaches(CurrentUser user) {
        athlete(user);
        return db.queryForList("SELECT u.id,u.name FROM app_user u JOIN coaching_relationship r ON r.coach_id=u.id WHERE r.athlete_id=? ORDER BY u.name,u.id",user.id());
    }
    @Transactional
    long createOwn(CurrentUser user, AthleteSessionInput input) {
        athlete(user);
        Integer n=db.queryForObject("SELECT COUNT(*) FROM coaching_relationship WHERE coach_id=? AND athlete_id=?",Integer.class,input.coachId(),user.id());
        if(n==null || n==0) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return create(new CurrentUser(input.coachId(),"", "", "COACH"), new SessionInput(user.id(),input.date(),input.title(),input.type(),input.notes(),input.steps()));
    }
    @Transactional
    void deleteOwn(CurrentUser user, long id) {
        athlete(user);
        canView(user,id);
        db.update("DELETE FROM session_execution WHERE session_id=?",id);
        db.update("DELETE FROM planned_session WHERE id=? AND athlete_id=?",id,user.id());
    }
    @Transactional
    void moveOwn(CurrentUser user, long id, LocalDate date) {
        athlete(user);
        int changed = db.update("UPDATE planned_session SET session_date=? WHERE id=? AND athlete_id=?",date,id,user.id());
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada");
    }
    private void canView(CurrentUser user, long sessionId) {
        Integer n = db.queryForObject("SELECT COUNT(*) FROM planned_session WHERE id=? AND ((coach_id=? AND ?='COACH') OR (athlete_id=? AND ?='ATHLETE'))", Integer.class, sessionId,user.id(),user.role(),user.id(),user.role());
        if (n == null || n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sesión no encontrada");
    }
    List<Map<String,Object>> progress(CurrentUser user, LocalDate from, LocalDate to, Long requestedAthlete) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 365)
            throw bad("El periodo debe ser válido y no superar 366 días");
        long athleteId;
        if (user.role().equals("ATHLETE")) {
            if (requestedAthlete != null && requestedAthlete.longValue() != user.id()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            athleteId = user.id();
        } else {
            coach(user);
            if (requestedAthlete == null) throw bad("Seleccione un atleta");
            athleteId = requestedAthlete;
            Integer n = db.queryForObject("SELECT COUNT(*) FROM coaching_relationship WHERE coach_id=? AND athlete_id=?", Integer.class, user.id(), athleteId);
            if (n == null || n == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return db.queryForList("SELECT s.id,s.session_date,s.type,s.title,e.id AS execution_id,e.distance_m,e.duration_s,e.avg_hr,e.rpe FROM planned_session s LEFT JOIN session_execution e ON e.session_id=s.id WHERE s.athlete_id=? AND s.session_date>=? AND s.session_date<=? AND (?='ATHLETE' OR s.coach_id=?) ORDER BY s.session_date,s.id", athleteId, from, to, user.role(), user.id());
    }
    List<Map<String,Object>> week(CurrentUser user, LocalDate from, Long requestedAthlete) {
        long athleteId;
        if (user.role().equals("ATHLETE")) { if (requestedAthlete != null && requestedAthlete != user.id()) throw new ResponseStatusException(HttpStatus.FORBIDDEN); athleteId=user.id(); }
        else { coach(user); if (requestedAthlete == null) throw bad("Seleccione un atleta"); athleteId=requestedAthlete;
            Integer n=db.queryForObject("SELECT COUNT(*) FROM coaching_relationship WHERE coach_id=? AND athlete_id=?",Integer.class,user.id(),athleteId);
            if(n==null || n==0) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return db.queryForList("SELECT s.id,s.session_date,s.title,s.type,s.notes, CASE WHEN e.id IS NULL THEN false ELSE true END AS completed FROM planned_session s LEFT JOIN session_execution e ON e.session_id=s.id WHERE s.athlete_id=? AND s.session_date>=? AND s.session_date<? AND (?='ATHLETE' OR s.coach_id=?) ORDER BY s.session_date,s.id", athleteId,from,from.plusDays(7),user.role(),user.id());
    }
    @Transactional
    long create(CurrentUser user, SessionInput input) {
        coach(user);
        Integer n=db.queryForObject("SELECT COUNT(*) FROM coaching_relationship WHERE coach_id=? AND athlete_id=?",Integer.class,user.id(),input.athleteId());
        if(n==null || n==0) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        int[] count={0}; for (StepInput step:input.steps()) validate(step,0,1,count);
        if (input.type().equals("INTERVALS") && count[0] == 0) throw bad("Agregue al menos una repetición");
        Long id=db.queryForObject("INSERT INTO planned_session(coach_id,athlete_id,session_date,title,type,notes) VALUES (?,?,?,?,?,?) RETURNING id",Long.class,user.id(),input.athleteId(),input.date(),input.title().trim(),input.type(),input.notes());
        List<Node> nodes=new ArrayList<>(); int position=0;
        for(StepInput step:input.steps()) nodes.add(insertStep(id,null,position++,step));
        int[] sequence={0}; for(Node node:nodes) expand(id,node,"",sequence);
        return id;
    }
    private void validate(StepInput step,int depth,int factor,int[] count) {
        if(step == null || depth>4) throw bad("Estructura demasiado profunda");
        int occurrences=factor*step.repeatCount();
        if(occurrences>500) throw bad("Máximo 500 repeticiones por sesión");
        List<StepInput> children=step.children()==null ? List.of() : step.children();
        if(step.kind().equals("GROUP")) {
            if(children.isEmpty() || step.distanceM()!=null || step.targetDurationS()!=null || step.paceMinSPerKm()!=null || step.paceMaxSPerKm()!=null) throw bad("Un grupo requiere pasos y no admite métricas propias");
            for(StepInput child:children) validate(child,depth+1,occurrences,count);
        } else {
            if(!children.isEmpty()) throw bad("Solo un grupo puede contener pasos");
            if(step.paceMinSPerKm()!=null && step.paceMaxSPerKm()!=null && step.paceMinSPerKm().compareTo(step.paceMaxSPerKm())>0) throw bad("Rango de ritmo inválido");
            count[0]+=occurrences; if(count[0]>500) throw bad("Máximo 500 repeticiones por sesión");
        }
    }
    record Node(long id,StepInput input,List<Node> children) {}
    private Node insertStep(long session,Long parent,int position,StepInput s) {
        Long id=db.queryForObject("INSERT INTO session_step(session_id,parent_id,position,kind,label,repeat_count,distance_m,target_duration_s,pace_min_s_per_km,pace_max_s_per_km) VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id",Long.class,session,parent,position,s.kind(),s.label().trim(),s.repeatCount(),s.distanceM(),s.targetDurationS(),s.paceMinSPerKm(),s.paceMaxSPerKm());
        List<Node> children=new ArrayList<>(); int p=0; if(s.children()!=null) for(StepInput child:s.children()) children.add(insertStep(session,id,p++,child));
        return new Node(id,s,children);
    }
    private void expand(long session,Node node,String path,int[] sequence) {
        for(int i=1;i<=node.input().repeatCount();i++) {
            String next=path.isEmpty()? String.valueOf(i):path+"."+i;
            if(node.input().kind().equals("GROUP")) for(Node child:node.children()) expand(session,child,next,sequence);
            else db.update("INSERT INTO step_occurrence(session_id,step_id,sequence_no,occurrence_path) VALUES (?,?,?,?)",session,node.id(),++sequence[0],next);
        }
    }
    Map<String,Object> detail(CurrentUser user,long id) {
        canView(user,id);
        Map<String,Object> result=new LinkedHashMap<>(db.queryForMap("SELECT id,coach_id,athlete_id,session_date,title,type,notes FROM planned_session WHERE id=?",id));
        result.put("steps",db.queryForList("SELECT id,parent_id,position,kind,label,repeat_count,distance_m,target_duration_s,pace_min_s_per_km,pace_max_s_per_km FROM session_step WHERE session_id=? ORDER BY id",id));
        result.put("occurrences",db.queryForList("SELECT o.id,o.sequence_no,o.occurrence_path,s.kind,s.label,s.distance_m,s.target_duration_s,s.pace_min_s_per_km,s.pace_max_s_per_km,r.duration_s AS actual_duration_s,r.distance_m AS actual_distance_m FROM step_occurrence o JOIN session_step s ON s.id=o.step_id LEFT JOIN occurrence_result r ON r.occurrence_id=o.id WHERE o.session_id=? ORDER BY o.sequence_no",id));
        List<Map<String,Object>> execution=db.queryForList("SELECT duration_s,distance_m,avg_hr,max_hr,rpe,notes,source,updated_at FROM session_execution WHERE session_id=?",id);
        result.put("execution",execution.isEmpty()?null:execution.getFirst()); return result;
    }
    @Transactional
    void feedback(CurrentUser user,long sessionId,FeedbackInput input) {
        athlete(user); canView(user,sessionId);
        if (input.durationS() == null && input.distanceM() == null && input.avgHr() == null
            && input.maxHr() == null && input.rpe() == null
            && (input.notes() == null || input.notes().isBlank()) && input.results().isEmpty())
            throw bad("Registre al menos un dato o comentario antes de guardar");
        if(input.avgHr()!=null && input.maxHr()!=null && input.avgHr()>input.maxHr()) throw bad("La FC media no puede superar la máxima");
        Set<Long> ids=new HashSet<>();
        for(ResultInput result:input.results()) {
            if(!ids.add(result.occurrenceId())) throw bad("Repetición duplicada");
            if(result.durationS()==null && result.distanceM()==null) throw bad("Cada resultado necesita una medición");
            Integer n=db.queryForObject("SELECT COUNT(*) FROM step_occurrence WHERE id=? AND session_id=?",Integer.class,result.occurrenceId(),sessionId);
            if(n==null || n==0) throw bad("La repetición no pertenece a la sesión");
        }
        List<Long> existing=db.queryForList("SELECT id FROM session_execution WHERE session_id=?",Long.class,sessionId);
        Long execution;
        if(existing.isEmpty()) execution=db.queryForObject("INSERT INTO session_execution(session_id,athlete_id,duration_s,distance_m,avg_hr,max_hr,rpe,notes) VALUES (?,?,?,?,?,?,?,?) RETURNING id",Long.class,sessionId,user.id(),input.durationS(),input.distanceM(),input.avgHr(),input.maxHr(),input.rpe(),input.notes());
        else { execution=existing.getFirst(); db.update("UPDATE session_execution SET duration_s=?,distance_m=?,avg_hr=?,max_hr=?,rpe=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",input.durationS(),input.distanceM(),input.avgHr(),input.maxHr(),input.rpe(),input.notes(),execution); db.update("DELETE FROM occurrence_result WHERE execution_id=?",execution); }
        for(ResultInput result:input.results()) db.update("INSERT INTO occurrence_result(execution_id,occurrence_id,duration_s,distance_m) VALUES (?,?,?,?)",execution,result.occurrenceId(),result.durationS(),result.distanceM());
    }
}
