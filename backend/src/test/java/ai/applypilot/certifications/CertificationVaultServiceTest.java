package ai.applypilot.certifications;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CertificationVaultServiceTest {
    @TempDir Path temp;

    @Test void certificate_metadata_and_evidence_snapshot_are_owner_scoped() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class); CertificationMalwareScanner scanner=mock(CertificationMalwareScanner.class);
        UUID owner=UUID.randomUUID(), certId=UUID.randomUUID();
        Map<String,Object> row=new HashMap<>(); row.put("id",certId);row.put("name","Synthetic Cloud Certificate");row.put("issuer","Example Academy");row.put("issue_date","2025-02-01");row.put("expiration_date",null);row.put("credential_id","SYN-42");row.put("verification_url","https://issuer.example/verify");row.put("credential_url","https://issuer.example/verify");row.put("credly_badge_url",null);row.put("score_result","Pass");row.put("candidate_verified",false);row.put("reminder_days","90,30,7");row.put("certificate_file_name",null);row.put("certificate_size_bytes",null);row.put("certificate_sha256",null);row.put("certificate_scan_status","NOT_CONFIGURED");row.put("archived_at",null);
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(row);
        var service=new CertificationVaultService(jdbc,temp.toString(),scanner);
        var selected=service.selected(owner,List.of(certId));
        assertThat(selected.snapshot()).singleElement().satisfies(snapshot->{
            assertThat(snapshot.get("name")).isEqualTo("Synthetic Cloud Certificate");
            assertThat(snapshot.get("linksCandidateProvided")).isEqualTo(true);
            @SuppressWarnings("unchecked") Map<String,Object> evidenceRef=(Map<String,Object>)snapshot.get("evidenceReference");
            assertThat(evidenceRef).containsEntry("sourceId",certId.toString()).containsEntry("sourceType","certification");
        });
        verify(jdbc).queryForMap(contains("WHERE id=? AND user_id=? AND archived_at IS NULL"),eq(certId),eq(owner));
    }

    @Test void another_owner_cannot_read_certificate_metadata() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenThrow(new EmptyResultDataAccessException(1));
        var service=new CertificationVaultService(jdbc,temp.toString(),content->new CertificationMalwareScanner.ScanResult(CertificationMalwareScanner.Verdict.UNAVAILABLE,"NOT_CONFIGURED"));
        assertThatThrownBy(()->service.get(UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Certification not found");
    }

    @Test void malformed_pdf_is_rejected_before_scanner_or_storage() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class); CertificationMalwareScanner scanner=mock(CertificationMalwareScanner.class);
        UUID owner=UUID.randomUUID(), id=UUID.randomUUID(); Map<String,Object> row=new HashMap<>();row.put("id",id);row.put("certificate_storage_key",null);
        when(jdbc.queryForMap(anyString(),any(Object[].class))).thenReturn(row);
        var service=new CertificationVaultService(jdbc,temp.toString(),scanner);
        var file=new org.springframework.mock.web.MockMultipartFile("file","fake.pdf","application/pdf","not a pdf".getBytes());
        assertThatThrownBy(()->service.upload(owner,id,file)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("not a valid PDF");
        verifyNoInteractions(scanner);
        verify(jdbc,never()).update(contains("certificate_storage_key"),any(Object[].class));
    }
}
