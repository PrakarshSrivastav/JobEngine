package io.github.prakarshsrivastav.jobengine.api;

import io.github.prakarshsrivastav.jobengine.api.dto.StatsResponse;
import io.github.prakarshsrivastav.jobengine.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class StatsController {

    private final JobService jobService;

    @GetMapping("/api/stats")
    public StatsResponse stats() {
        return jobService.stats();
    }
}
