package ai.applypilot.tracking;

import ai.applypilot.ai.AiProviderException;
import ai.applypilot.ai.LanguageModelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;

class InterviewAnswerDraftServiceTest {
    LanguageModelProvider provider; InterviewAnswerDraftService service; ObjectMapper mapper=new ObjectMapper(); UUID resume=UUID.randomUUID();
    @BeforeEach void setup(){provider=mock(LanguageModelProvider.class);service=new InterviewAnswerDraftService(provider,mapper);}
    @Test void returnsCitationForExactResumeEvidence(){when(provider.isConfigured()).thenReturn(true);when(provider.generateJson(anyString(),anyString())).thenReturn(json("{\"answer\":\"Built a Python API for job tracking.\",\"evidenceUsed\":[\"Built a Python API for job tracking.\"]}"));var draft=service.draft("Tell me about Python",resume,3,List.of("Built a Python API for job tracking."));assertThat(draft.evidenceUsed()).singleElement().satisfies(c->{assertThat(c.sourceId()).isEqualTo(resume.toString());assertThat(c.version()).isEqualTo(3);});}
    @Test void rejectsUnsupportedClaimsAndMissingEvidence(){when(provider.isConfigured()).thenReturn(true);when(provider.generateJson(anyString(),anyString())).thenReturn(json("{\"answer\":\"Led 50 engineers and increased revenue by 90 percent.\",\"evidenceUsed\":[\"Built a Python API for job tracking.\"]}"));assertThatThrownBy(()->service.draft("Tell me about your leadership",resume,1,List.of("Built a Python API for job tracking."))).isInstanceOf(AiProviderException.class);assertThatThrownBy(()->service.draft("Tell me about your work",resume,1,List.of())).isInstanceOf(AiProviderException.class);}
    @Test void disabledProviderAndMalformedResponseAreControlled(){when(provider.isConfigured()).thenReturn(false);assertThatThrownBy(()->service.draft("Question",resume,1,List.of("Built a Python API."))).isInstanceOf(AiProviderException.class).hasMessageContaining("disabled");when(provider.isConfigured()).thenReturn(true);when(provider.generateJson(anyString(),anyString())).thenReturn(json("{}"));assertThatThrownBy(()->service.draft("Question",resume,1,List.of("Built a Python API."))).isInstanceOf(AiProviderException.class);}
    private com.fasterxml.jackson.databind.JsonNode json(String s){try{return mapper.readTree(s);}catch(Exception e){throw new RuntimeException(e);}}
}
