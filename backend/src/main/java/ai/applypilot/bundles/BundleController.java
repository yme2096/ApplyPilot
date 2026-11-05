package ai.applypilot.bundles;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bundles")
public class BundleController {

    private final BundleService service;

    public BundleController(BundleService service) {
        this.service = service;
    }

    private UUID owner(Authentication a) {
        return (UUID) a.getPrincipal();
    }

    @GetMapping
    public List<BundleService.BundleDto> list(
        Authentication a,
        @RequestParam(required = false) UUID jobId,
        @RequestParam(required = false) String status
    ) {
        return service.listBundles(owner(a), jobId, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BundleService.BundleDto create(Authentication a, @Valid @RequestBody BundleService.CreateBundleRequest req) {
        return service.createBundle(owner(a), req);
    }

    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BundleService.BundleDto> createBulk(Authentication a, @RequestBody BundleService.BulkCreateRequest req) {
        return service.createBulk(owner(a), req);
    }

    @GetMapping("/{id}")
    public BundleService.BundleDto get(Authentication a, @PathVariable UUID id) {
        return service.getBundle(owner(a), id);
    }

    @PatchMapping("/{id}")
    public BundleService.BundleDto update(
        Authentication a,
        @PathVariable UUID id,
        @Valid @RequestBody BundleService.UpdateBundleRequest req
    ) {
        return service.updateBundle(owner(a), id, req);
    }

    @PostMapping("/{id}/answers")
    public BundleService.BundleDto addAnswer(
        Authentication a,
        @PathVariable UUID id,
        @Valid @RequestBody BundleService.ScreeningAnswer answer
    ) {
        return service.addScreeningAnswer(owner(a), id, answer);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a, @PathVariable UUID id) {
        service.deleteBundle(owner(a), id);
    }
}
