package ai.applypilot.certifications;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/certifications")
public class CertificationVaultController {
    private final CertificationVaultService service;
    public CertificationVaultController(CertificationVaultService service) { this.service = service; }
    private UUID owner(Authentication auth) { return (UUID) auth.getPrincipal(); }

    @GetMapping
    public List<CertificationVaultService.CertificationDto> list(Authentication auth,
            @RequestParam(required = false) String status, @RequestParam(required = false) String q) {
        return service.list(owner(auth), status, q);
    }

    @GetMapping("/{id}")
    public CertificationVaultService.CertificationDto get(Authentication auth, @PathVariable UUID id) {
        return service.get(owner(auth), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CertificationVaultService.CertificationDto create(Authentication auth, @Valid @RequestBody CertificationVaultService.CertificationRequest request) {
        return service.create(owner(auth), request);
    }

    @PutMapping("/{id}")
    public CertificationVaultService.CertificationDto update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody CertificationVaultService.CertificationRequest request) {
        return service.update(owner(auth), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(Authentication auth, @PathVariable UUID id) { service.archive(owner(auth), id); }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CertificationVaultService.CertificationDto upload(Authentication auth, @PathVariable UUID id, @RequestPart("file") MultipartFile file) {
        return service.upload(owner(auth), id, file);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> download(Authentication auth, @PathVariable UUID id) {
        var file = service.download(owner(auth), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
            .header("X-Content-SHA256", file.sha256()).body(file.content());
    }
}
