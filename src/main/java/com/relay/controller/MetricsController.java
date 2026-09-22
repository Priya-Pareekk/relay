package com.relay.controller;

import com.relay.dto.MetricsSummaryResponse;
import com.relay.service.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    @GetMapping("/summary")
    public ResponseEntity<MetricsSummaryResponse> getSummary() {
        return ResponseEntity.ok(metricsService.getSummary());
    }
}
