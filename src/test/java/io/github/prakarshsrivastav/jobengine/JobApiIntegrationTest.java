package io.github.prakarshsrivastav.jobengine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Worker disabled so submitted jobs stay PENDING and the API can be checked deterministically.
@Import({TestcontainersConfiguration.class, TestJobHandlers.class})
@SpringBootTest(properties = "jobengine.worker.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobApiIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE jobs CASCADE");
    }

    @Test
    void submitReturnsCreatedJobWithLocation() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "EMAIL_NOTIFICATION", "priority": "HIGH", "payload": {"to": "a@b.c"}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/jobs/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.maxRetries").value(3))
                .andExpect(jsonPath("$.payload.to").value("a@b.c"));
    }

    @Test
    void invalidRequestsAreRejectedWithProblemDetails() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "", "maxRetries": 99}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.maxRetries").exists());

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "NOT_A_REAL_TYPE"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("DATA_PROCESSING")));

        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("""
                        {"type": "DATA_PROCESSING", "priority": "URGENT"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownJobIsNotFound() throws Exception {
        mvc.perform(get("/api/jobs/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("Job not found")));
    }

    @Test
    void pendingJobCanBeCancelledOnce() throws Exception {
        String id = submit("DATA_PROCESSING");

        mvc.perform(post("/api/jobs/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(post("/api/jobs/{id}/cancel", id))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/jobs/{id}/events", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].fromStatus").value("PENDING"))
                .andExpect(jsonPath("$[1].toStatus").value("CANCELLED"));
    }

    @Test
    void listIsPaginatedAndFilterable() throws Exception {
        for (int i = 0; i < 3; i++) {
            submit("DATA_PROCESSING");
        }
        submit("REPORT_GENERATION");

        mvc.perform(get("/api/jobs").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(4))
                .andExpect(jsonPath("$.page.totalPages").value(2));

        mvc.perform(get("/api/jobs").param("type", "REPORT_GENERATION").param("status", "PENDING"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void statsCountJobsByStatus() throws Exception {
        submit("DATA_PROCESSING");
        String cancelled = submit("DATA_PROCESSING");
        mvc.perform(post("/api/jobs/{id}/cancel", cancelled));

        mvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalJobs").value(2))
                .andExpect(jsonPath("$.jobsByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.jobsByStatus.CANCELLED").value(1))
                .andExpect(jsonPath("$.jobsByStatus.SUCCESS").value(0));
    }

    @Test
    void replayingAJobThatIsNotDeadIsAConflict() throws Exception {
        String id = submit("DATA_PROCESSING");

        mvc.perform(post("/api/dead-letters/{id}/replay", id))
                .andExpect(status().isConflict());
    }

    private String submit(String type) throws Exception {
        String body = mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\": \"" + type + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("id").asText();
    }
}
