package io.github.prakarshsrivastav.jobengine.api;

import io.github.prakarshsrivastav.jobengine.api.dto.DeadLetterResponse;
import io.github.prakarshsrivastav.jobengine.api.dto.JobResponse;
import io.github.prakarshsrivastav.jobengine.service.DeadLetterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/dead-letters")
@RequiredArgsConstructor
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    @GetMapping
    public Page<DeadLetterResponse> list(
            @PageableDefault(size = 20, sort = "movedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return deadLetterService.list(pageable).map(DeadLetterResponse::from);
    }

    @PostMapping("/{jobId}/replay")
    public JobResponse replay(@PathVariable UUID jobId) {
        return JobResponse.from(deadLetterService.replay(jobId));
    }
}
