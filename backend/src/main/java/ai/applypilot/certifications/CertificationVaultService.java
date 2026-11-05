package ai.applypilot.certifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class CertificationVaultService {
    public static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private final JdbcTemplate jdbc;
    private final Path root;
    private final CertificationMalwareScanner scanner;

    public CertificationVaultService(JdbcTemplate jdbc,
                                     @Value("${app.upload-dir}") String uploadDir,
                                     CertificationMalwareScanner scanner) {
        this.jdbc = jdbc;
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.scanner = scanner;
    }

    public record CertificationRequest(String name, String issuer, String issueDate, String expirationDate,
                                       String credentialId, String officialVerificationUrl, String credlyBadgeUrl,
                                       String scoreResult, Boolean candidateVerified, List<Integer> reminderDays) {}

    public record CertificationDto(UUID id, String name, String issuer, String issueDate, String expirationDate,
                                   String credentialId, String officialVerificationUrl, String credlyBadgeUrl,
                                   String scoreResult, boolean candidateVerified, List<Integer> reminderDays,
                                   String expirationStatus, String certificateFileName, Long certificateSizeBytes,
                                   String certificateSha256, String malwareScanStatus, boolean linksCandidateProvided) {}

    public record Download(byte[] content, String filename, String sha256) {}
    public record Selected(List<Map<String, Object>> snapshot, List<String> resumeLines, List<Map<String, Object>> evidence) {}

    @Transactional(readOnly = true)
    public List<CertificationDto> list(UUID owner, String status, String query) {
        String sql = "SELECT * FROM certifications WHERE user_id=? AND archived_at IS NULL ORDER BY created_at DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, owner);
        String search = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return rows.stream().map(this::dto)
            .filter(c -> status == null || status.isBlank() || status.equals(c.expirationStatus()))
            .filter(c -> search.isBlank() || (c.name()+" "+Objects.toString(c.issuer(),"")+" "+Objects.toString(c.credentialId(),"")).toLowerCase(Locale.ROOT).contains(search))
            .toList();
    }

    @Transactional(readOnly = true)
    public CertificationDto get(UUID owner, UUID id) { return dto(active(owner, id)); }

    @Transactional
    public CertificationDto create(UUID owner, CertificationRequest request) {
        Fields f = fields(request);
        ensureUnique(owner, null, f);
        UUID id;
        try {
            id = jdbc.queryForObject("INSERT INTO certifications(user_id,name,issuer,issue_date,credential_url,expiration_date,credential_id,verification_url,credly_badge_url,score_result,candidate_verified,reminder_days) VALUES (?,?,?,?,?,CAST(? AS DATE),?,?,?,?,?,?) RETURNING id",
                UUID.class, owner, f.name, f.issuer, text(f.issueDate), f.officialUrl, f.expirationDate == null ? null : Date.valueOf(f.expirationDate), f.credentialId, f.officialUrl, f.credlyUrl, f.score, f.candidateVerified, encodeDays(f.days));
        } catch (org.springframework.dao.DuplicateKeyException ex) { throw conflict("This certification already exists in the vault."); }
        syncReminders(owner, id);
        return get(owner, id);
    }

    @Transactional
    public CertificationDto update(UUID owner, UUID id, CertificationRequest request) {
        active(owner, id);
        Fields f = fields(request);
        ensureUnique(owner, id, f);
        try {
            int changed = jdbc.update("UPDATE certifications SET name=?,issuer=?,issue_date=?,credential_url=?,expiration_date=CAST(? AS DATE),credential_id=?,verification_url=?,credly_badge_url=?,score_result=?,candidate_verified=?,reminder_days=? WHERE id=? AND user_id=? AND archived_at IS NULL",
                f.name, f.issuer, text(f.issueDate), f.officialUrl, f.expirationDate == null ? null : Date.valueOf(f.expirationDate), f.credentialId, f.officialUrl, f.credlyUrl, f.score, f.candidateVerified, encodeDays(f.days), id, owner);
            if (changed == 0) throw notFound();
        } catch (org.springframework.dao.DuplicateKeyException ex) { throw conflict("This certification already exists in the vault."); }
        syncReminders(owner, id);
        return get(owner, id);
    }

    @Transactional
    public void archive(UUID owner, UUID id) {
        int changed = jdbc.update("UPDATE certifications SET archived_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND archived_at IS NULL", id, owner);
        if (changed == 0) throw notFound();
        jdbc.update("UPDATE in_app_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE certification_id=? AND user_id=? AND kind='CERTIFICATION_EXPIRY'", id, owner);
        // Private PDF files and metadata are deliberately retained; archive is reversible at the storage layer.
    }

    @Transactional
    public CertificationDto upload(UUID owner, UUID id, MultipartFile file) {
        Map<String, Object> cert = active(owner, id);
        if (cert.get("certificate_storage_key") != null) throw conflict("A certificate PDF is already attached. Existing uploads are preserved and cannot be replaced.");
        if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_BYTES) throw bad("Upload a PDF up to 10 MiB.");
        String filename = Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(), "certificate.pdf")).getFileName().toString();
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) throw bad("Certificate attachment must be a PDF.");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read certificate PDF."); }
        validatePdf(bytes);
        CertificationMalwareScanner.ScanResult scan;
        try { scan = scanner.scan(bytes); }
        catch (Exception ex) { scan = new CertificationMalwareScanner.ScanResult(CertificationMalwareScanner.Verdict.UNAVAILABLE, "UNAVAILABLE"); }
        if (scan.verdict() == CertificationMalwareScanner.Verdict.INFECTED) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The certificate file did not pass the available safety scanner.");
        String sha = sha256(bytes);
        String key = "certifications/" + owner + "/" + id + "/" + UUID.randomUUID() + ".pdf";
        Path target = safePath(key);
        boolean created = false;
        try {
            Files.createDirectories(target.getParent());
            Path realRoot = root.toRealPath();
            if (!target.getParent().toRealPath().startsWith(realRoot)) throw new java.io.IOException("Certificate storage directory escaped private root.");
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created = true;
            int changed = jdbc.update("UPDATE certifications SET certificate_file_name=?,certificate_storage_key=?,certificate_sha256=?,certificate_size_bytes=?,certificate_scan_status=? WHERE id=? AND user_id=? AND archived_at IS NULL AND certificate_storage_key IS NULL",
                filename, key, sha, bytes.length, safeScanStatus(scan.status()), id, owner);
            if (changed == 0) throw conflict("Certificate attachment changed while uploading; existing files are preserved.");
        } catch (ResponseStatusException ex) { cleanupNewFile(target, created); throw ex; }
        catch (Exception ex) { cleanupNewFile(target, created); throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store certificate PDF."); }
        return get(owner, id);
    }

    @Transactional(readOnly = true)
    public Download download(UUID owner, UUID id) {
        Map<String, Object> row = active(owner, id);
        String key = Objects.toString(row.get("certificate_storage_key"), "");
        if (key.isBlank()) throw notFound();
        try {
            Path realRoot = root.toRealPath();
            Path realFile = safePath(key).toRealPath();
            if (!realFile.startsWith(realRoot)) throw notFound();
            byte[] bytes = Files.readAllBytes(realFile);
            if (bytes.length == 0 || !validPdf(bytes)) throw notFound();
            String expectedHash = Objects.toString(row.get("certificate_sha256"), "");
            if (!SHA256.matcher(expectedHash).matches() || !sha256(bytes).equals(expectedHash))
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored certificate integrity check failed.");
            return new Download(bytes, safeFilename(Objects.toString(row.get("certificate_file_name"), "certificate.pdf")), expectedHash);
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw notFound(); }
    }

    @Transactional(readOnly = true)
    public Selected selected(UUID owner, List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return new Selected(List.of(), List.of(), List.of());
        if (ids.size() > 20 || new HashSet<>(ids).size() != ids.size()) throw bad("Select up to 20 distinct certifications.");
        List<Map<String,Object>> snapshots = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        List<Map<String,Object>> evidence = new ArrayList<>();
        for (UUID id : ids) {
            Map<String,Object> row = active(owner,id);
            CertificationDto c = dto(row);
            Map<String,Object> snapshot = new LinkedHashMap<>();
            snapshot.put("id", c.id().toString()); snapshot.put("name", c.name()); snapshot.put("issuer", c.issuer());
            snapshot.put("issueDate", c.issueDate()); snapshot.put("expirationDate", c.expirationDate());
            snapshot.put("credentialId", c.credentialId()); snapshot.put("officialVerificationUrl", c.officialVerificationUrl());
            snapshot.put("credlyBadgeUrl", c.credlyBadgeUrl()); snapshot.put("scoreResult", c.scoreResult());
            snapshot.put("candidateVerified", c.candidateVerified()); snapshot.put("linksCandidateProvided", true);
            snapshot.put("evidenceReference", Map.of("sourceType", "certification", "sourceId", c.id().toString()));
            snapshots.add(snapshot);
            lines.add(c.name() + (c.issuer() == null ? "" : " — " + c.issuer()) + (c.issueDate() == null ? "" : " (issued " + c.issueDate() + ")"));
            evidence.add(Map.of("sourceType", "certification", "sourceId", c.id().toString(), "name", c.name()));
        }
        return new Selected(List.copyOf(snapshots), List.copyOf(lines), List.copyOf(evidence));
    }

    @Transactional
    public void syncReminders(UUID owner, UUID id) {
        Map<String,Object> row;
        try { row = jdbc.queryForMap("SELECT id,name,expiration_date,reminder_days,archived_at FROM certifications WHERE id=? AND user_id=?", id, owner); }
        catch (org.springframework.dao.EmptyResultDataAccessException ex) { return; }
        boolean enabled = jdbc.queryForObject("SELECT COALESCE((SELECT certification_reminders FROM user_notification_preferences WHERE user_id=?),TRUE)", Boolean.class, owner);
        Object expiration = row.get("expiration_date");
        if (!enabled || expiration == null || row.get("archived_at") != null) {
            jdbc.update("UPDATE in_app_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE certification_id=? AND user_id=? AND kind='CERTIFICATION_EXPIRY'", id, owner);
            return;
        }
        LocalDate expiry = expiration instanceof Date d ? d.toLocalDate() : LocalDate.parse(expiration.toString());
        String name = Objects.toString(row.get("name"), "Certification");
        List<Integer> reminderDays = decodeDays(Objects.toString(row.get("reminder_days"), "90,30,7"));
        if (reminderDays.isEmpty()) {
            jdbc.update("UPDATE in_app_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE certification_id=? AND user_id=? AND kind='CERTIFICATION_EXPIRY'", id, owner);
            return;
        }
        for (int days : reminderDays) {
            java.time.OffsetDateTime scheduled = expiry.minusDays(days).atStartOfDay(java.time.ZoneOffset.UTC).toOffsetDateTime();
            jdbc.update("INSERT INTO in_app_notifications(user_id,kind,title,body,certification_id,reminder_days_before,scheduled_at) VALUES(?,?,?,?,?,?,?) ON CONFLICT(certification_id,reminder_days_before) WHERE kind='CERTIFICATION_EXPIRY' AND certification_id IS NOT NULL DO UPDATE SET title=EXCLUDED.title,body=EXCLUDED.body,scheduled_at=EXCLUDED.scheduled_at,read_at=CASE WHEN in_app_notifications.scheduled_at IS DISTINCT FROM EXCLUDED.scheduled_at THEN NULL ELSE in_app_notifications.read_at END",
                owner, "CERTIFICATION_EXPIRY", "Certification expiration reminder", name + " expires in " + days + " days.", id, days, scheduled);
        }
        jdbc.update("UPDATE in_app_notifications SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP) WHERE certification_id=? AND user_id=? AND kind='CERTIFICATION_EXPIRY' AND reminder_days_before NOT IN (" + String.join(",", Collections.nCopies(reminderDays.size(), "?")) + ")",
            prepend(id, owner, reminderDays).toArray());
    }

    @Scheduled(fixedDelayString = "${app.certifications.reminder-scan-ms:3600000}", initialDelayString = "${app.certifications.reminder-initial-delay-ms:60000}")
    @Transactional
    public void syncAllReminders() {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,user_id FROM certifications WHERE archived_at IS NULL AND expiration_date IS NOT NULL");
        for (Map<String,Object> row : rows) syncReminders((UUID) row.get("user_id"), (UUID) row.get("id"));
    }

    private Map<String,Object> active(UUID owner, UUID id) {
        try { return jdbc.queryForMap("SELECT * FROM certifications WHERE id=? AND user_id=? AND archived_at IS NULL", id, owner); }
        catch (org.springframework.dao.EmptyResultDataAccessException ex) { throw notFound(); }
    }

    private Fields fields(CertificationRequest r) {
        if (r == null || r.name() == null || r.name().isBlank() || r.name().length() > 200) throw bad("Certification name is required and must be at most 200 characters.");
        if (r.issuer() != null && r.issuer().length() > 200) throw bad("Issuing organization must be at most 200 characters.");
        if (r.credentialId() != null && r.credentialId().length() > 255) throw bad("Credential ID must be at most 255 characters.");
        if (r.scoreResult() != null && r.scoreResult().length() > 200) throw bad("Score or result must be at most 200 characters.");
        LocalDate issue = CertificationRules.date(r.issueDate(), "Issue date");
        LocalDate expiry = CertificationRules.date(r.expirationDate(), "Expiration date");
        CertificationRules.validateDates(issue, expiry);
        return new Fields(r.name().trim(), blankToNull(r.issuer()), issue, expiry, blankToNull(r.credentialId()),
            CertificationRules.httpsUrl(r.officialVerificationUrl(), "Official verification URL"),
            CertificationRules.httpsUrl(r.credlyBadgeUrl(), "Credly badge URL"), blankToNull(r.scoreResult()),
            Boolean.TRUE.equals(r.candidateVerified()), CertificationRules.reminderDays(r.reminderDays()));
    }

    private void ensureUnique(UUID owner, UUID exclude, Fields f) {
        Integer count;
        String exclusion = exclude == null ? "" : " AND id<>?";
        if (f.credentialId != null) {
            List<Object> args = new ArrayList<>(List.of(owner, f.credentialId)); if (exclude != null) args.add(exclude);
            count = jdbc.queryForObject("SELECT count(*) FROM certifications WHERE user_id=? AND archived_at IS NULL AND lower(credential_id)=lower(?)" + exclusion, Integer.class, args.toArray());
        } else {
            List<Object> args = new ArrayList<>(Arrays.asList(owner, f.name, f.issuer, text(f.issueDate))); if (exclude != null) args.add(exclude);
            count = jdbc.queryForObject("SELECT count(*) FROM certifications WHERE user_id=? AND archived_at IS NULL AND credential_id IS NULL AND lower(name)=lower(?) AND lower(coalesce(issuer,''))=lower(coalesce(? ,'')) AND coalesce(issue_date,'')=coalesce(?,'')" + exclusion, Integer.class, args.toArray());
        }
        if (count != null && count > 0) throw conflict("This certification already exists in the vault.");
    }

    private CertificationDto dto(Map<String,Object> row) {
        UUID id=(UUID)row.get("id"); String issue=Objects.toString(row.get("issue_date"), null);
        LocalDate issueDate=null;
        if(issue!=null&&!issue.isBlank()) try { issueDate=LocalDate.parse(issue); } catch(Exception ignored) { }
        Object expiry=row.get("expiration_date"); LocalDate expiration=expiry instanceof Date d?d.toLocalDate():expiry==null?null:LocalDate.parse(expiry.toString());
        List<Integer> days=decodeDays(Objects.toString(row.get("reminder_days"), "90,30,7"));
        String official=Objects.toString(row.get("verification_url"), null);
        if(official==null||official.isBlank()) official=Objects.toString(row.get("credential_url"), null);
        return new CertificationDto(id,Objects.toString(row.get("name"),""),Objects.toString(row.get("issuer"),null),issueDate==null?issue:issueDate.toString(),expiration==null?null:expiration.toString(),Objects.toString(row.get("credential_id"),null),official,Objects.toString(row.get("credly_badge_url"),null),Objects.toString(row.get("score_result"),null),Boolean.TRUE.equals(row.get("candidate_verified")),days,expirationStatus(expiration),Objects.toString(row.get("certificate_file_name"),null),row.get("certificate_size_bytes")==null?null:((Number)row.get("certificate_size_bytes")).longValue(),Objects.toString(row.get("certificate_sha256"),null),Objects.toString(row.get("certificate_scan_status"),"NOT_CONFIGURED"),true);
    }

    public static String expirationStatus(LocalDate expiration) {
        if(expiration==null)return "NO_EXPIRY";
        LocalDate today=LocalDate.now();
        if(expiration.isBefore(today))return "EXPIRED";
        if(!expiration.isAfter(today.plusDays(30)))return "EXPIRING_SOON";
        return "ACTIVE";
    }

    private void validatePdf(byte[] bytes) {
        if (!validPdf(bytes)) throw bad("File content is not a valid PDF. The filename or MIME type alone is not accepted.");
    }
    private boolean validPdf(byte[] bytes) {
        return CertificatePdfValidator.isValid(bytes, MAX_FILE_BYTES);
    }
    private String sha256(byte[] bytes) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception ex){throw new IllegalStateException("SHA-256 unavailable.");} }
    private Path safePath(String key) {
        Path p=root.resolve(key).normalize();
        if(!p.startsWith(root))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Certificate file not found.");
        return p;
    }
    private String safeScanStatus(String status){return Set.of("CLEAN","INFECTED","UNAVAILABLE","NOT_CONFIGURED").contains(status)?status:"UNAVAILABLE";}
    private String safeFilename(String input){String value=Paths.get(input).getFileName().toString().replaceAll("[\\r\\n\\\"]","_");return value.toLowerCase(Locale.ROOT).endsWith(".pdf")?value:value+".pdf";}
    private void cleanupNewFile(Path target,boolean created){if(created)try{Files.deleteIfExists(target);}catch(Exception ignored){}}
    private List<Integer> decodeDays(String value){try{return Arrays.stream(value.split(",")).map(String::trim).filter(s->!s.isBlank()).map(Integer::parseInt).toList();}catch(Exception ex){return List.of(90,30,7);}}
    private String encodeDays(List<Integer> days){return String.join(",",days.stream().map(String::valueOf).toList());}
    private String text(LocalDate d){return d==null?null:d.toString();}
    private String blankToNull(String s){return s==null||s.isBlank()?null:s.trim();}
    private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Certification not found.");}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private ResponseStatusException bad(String message){return CertificationRules.bad(message);}
    private List<Object> prepend(UUID id, UUID owner, List<Integer> days){List<Object> a=new ArrayList<>();a.add(id);a.add(owner);a.addAll(days);return a;}
    private record Fields(String name,String issuer,LocalDate issueDate,LocalDate expirationDate,String credentialId,String officialUrl,String credlyUrl,String score,boolean candidateVerified,List<Integer> days){}
}
