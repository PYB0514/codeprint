package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.AnalysisApplicationService;
import com.codeprint.domain.analysis.AnalysisResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisApplicationService analysisApplicationService;

    @PostMapping
    public ResponseEntity<AnalysisResult> startAnalysis(@RequestBody StartAnalysisRequest request) {
        AnalysisResult result = analysisApplicationService.startAnalysis(request.projectId());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{analysisId}")
    public ResponseEntity<AnalysisResult> getAnalysis(@PathVariable UUID analysisId) {
        return ResponseEntity.ok(analysisApplicationService.getAnalysis(analysisId));
    }

    public record StartAnalysisRequest(UUID projectId) {}
}
