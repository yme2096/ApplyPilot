package ai.applypilot.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@RequestMapping("/api/profile/records")
public class ProfileRecordsController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Map<String, Spec> specs = Map.of(
        "education", new Spec("education_records", Map.of("institution","institution", "degree","degree", "fieldOfStudy","field_of_study", "graduationYear","graduation_year", "grade","grade"), Set.of("institution"), Set.of()),
        "skills", new Spec("skill_records", Map.of("name","name", "verified","verified", "evidence","evidence"), Set.of("name"), Set.of()),
        "projects", new Spec("projects", Map.of("name","name", "description","description", "technologies","technologies", "projectUrl","project_url", "startDate","start_date", "endDate","end_date"), Set.of("name"), Set.of("technologies")),
        "experience", new Spec("work_experiences", Map.of("company","company", "title","title", "description","description", "startDate","start_date", "endDate","end_date", "internship","internship"), Set.of("company","title"), Set.of()),
        "certifications", new Spec("certifications", Map.of("name","name", "issuer","issuer", "issueDate","issue_date", "credentialUrl","credential_url"), Set.of("name"), Set.of()),
        "achievements", new Spec("achievements", Map.of("title","title", "description","description", "achievedOn","achieved_on"), Set.of("title"), Set.of())
    );

    public ProfileRecordsController(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    private UUID owner(Authentication auth) { return (UUID) auth.getPrincipal(); }
    private Spec spec(String kind) { Spec s=specs.get(kind); if(s==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Unknown profile record type"); return s; }

    @GetMapping("/{kind}")
    public List<Map<String,Object>> list(@PathVariable String kind, Authentication auth) {
        Spec s=spec(kind); String cols=String.join(", ",s.fields.entrySet().stream().map(e->s.jsonFields.contains(e.getKey())?e.getValue()+"::text AS "+e.getValue():e.getValue()).toList());
        String activeFilter=kind.equals("certifications")?" AND archived_at IS NULL":"";
        return jdbc.queryForList("SELECT id, "+cols+" FROM "+s.table+" WHERE user_id=?"+activeFilter+" ORDER BY created_at DESC",owner(auth));
    }

    @PostMapping("/{kind}") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@PathVariable String kind,@RequestBody Map<String,Object> body,Authentication auth) {
        Spec s=spec(kind); validate(s,body);
        if(kind.equals("certifications")) ensureNoLegacyDuplicate(owner(auth),null,body);
        var values=new LinkedHashMap<String,Object>(); values.put("user_id",owner(auth));
        for(var entry:s.fields.entrySet()) if(body.containsKey(entry.getKey())) values.put(entry.getValue(),dbValue(s,entry.getKey(),body.get(entry.getKey())));
        String columns=String.join(",",values.keySet()); String marks=String.join(",",values.keySet().stream().map(k->s.jsonFields.contains(bodyKey(s,k))?"?::jsonb":"?").toList());
        Object[] args=values.values().toArray();
        Map<String,Object> inserted=jdbc.queryForMap("INSERT INTO "+s.table+" ("+columns+") VALUES ("+marks+") RETURNING *",args);
        return kind.equals("certifications")?safeCertification((UUID)inserted.get("id"),owner(auth)):inserted;
    }

    @PutMapping("/{kind}/{id}")
    public Map<String,Object> update(@PathVariable String kind,@PathVariable UUID id,@RequestBody Map<String,Object> body,Authentication auth) {
        Spec s=spec(kind); validate(s,body);
        if(kind.equals("certifications")) ensureNoLegacyDuplicate(owner(auth),id,body);
        var fields=new ArrayList<Map.Entry<String,Object>>(); for(var f:s.fields.entrySet()) if(body.containsKey(f.getKey())) fields.add(new AbstractMap.SimpleImmutableEntry<>(f.getValue(),dbValue(s,f.getKey(),body.get(f.getKey()))));
        if(fields.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No supported profile fields were provided");
        String set=String.join(",",fields.stream().map(f->f.getKey()+"="+(s.jsonFields.contains(bodyKey(s,f.getKey()))?"?::jsonb":"?")).toList());
        Object[] args=new Object[fields.size()+2]; for(int i=0;i<fields.size();i++)args[i]=fields.get(i).getValue();args[fields.size()]=id;args[fields.size()+1]=owner(auth);
        String activeFilter=kind.equals("certifications")?" AND archived_at IS NULL":"";
        List<Map<String,Object>> result=jdbc.queryForList("UPDATE "+s.table+" SET "+set+" WHERE id=? AND user_id=?"+activeFilter+" RETURNING *",args);
        if(result.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return kind.equals("certifications")?safeCertification(id,owner(auth)):result.get(0);
    }

    @DeleteMapping("/{kind}/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String kind,@PathVariable UUID id,Authentication auth) {
        Spec s=spec(kind);
        int count=kind.equals("certifications")
            ?jdbc.update("UPDATE certifications SET archived_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND archived_at IS NULL",id,owner(auth))
            :jdbc.update("DELETE FROM "+s.table+" WHERE id=? AND user_id=?",id,owner(auth));
        if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(kind.equals("certifications"))jdbc.update("UPDATE in_app_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE certification_id=? AND user_id=?",id,owner(auth));
    }

    private void validate(Spec s,Map<String,Object> body) {
        if(body==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A JSON record is required");
        for(String key:body.keySet()) if(!s.fields.containsKey(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported field: "+key);
        for(String key:s.required) if(body.get(key)==null||body.get(key).toString().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,key+" is required");
        for(var entry:body.entrySet()) if(entry.getValue() instanceof String value && value.length()>10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,entry.getKey()+" is too long");
    }
    private void ensureNoLegacyDuplicate(UUID user,UUID exclude,Map<String,Object> body) {
        String name=Objects.toString(body.get("name"),"").trim();
        String issuer=Objects.toString(body.get("issuer"),"").trim();
        String issue=Objects.toString(body.get("issueDate"),"").trim();
        String exclusion=exclude==null?"":" AND id<>?";
        List<Object> args=new ArrayList<>(Arrays.asList(user,name,issuer,issue)); if(exclude!=null)args.add(exclude);
        Integer count=jdbc.queryForObject("SELECT count(*) FROM certifications WHERE user_id=? AND archived_at IS NULL AND credential_id IS NULL AND lower(name)=lower(?) AND lower(coalesce(issuer,''))=lower(coalesce(? ,'')) AND coalesce(issue_date,'')=coalesce(?,'')"+exclusion,Integer.class,args.toArray());
        if(count!=null&&count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,"This certification already exists in the vault.");
    }
    private Map<String,Object> safeCertification(UUID id,UUID user) {
        return jdbc.queryForMap("SELECT id,name,issuer,issue_date,credential_url FROM certifications WHERE id=? AND user_id=? AND archived_at IS NULL",id,user);
    }
    private Object dbValue(Spec s,String key,Object value) {
        if(s.jsonFields.contains(key)) try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid JSON field");}
        return value;
    }
    private String bodyKey(Spec s,String column){return s.fields.entrySet().stream().filter(e->e.getValue().equals(column)).map(Map.Entry::getKey).findFirst().orElse("");}
    private record Spec(String table,Map<String,String> fields,Set<String> required,Set<String> jsonFields){}
}
