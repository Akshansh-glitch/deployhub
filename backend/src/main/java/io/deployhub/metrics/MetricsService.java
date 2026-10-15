package io.deployhub.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import io.deployhub.config.DeployHubProperties;
import io.deployhub.domain.Application;
import io.deployhub.dto.Dtos.MetricsView;
import io.deployhub.dto.Dtos.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Reads app metrics from Prometheus so the dashboard can chart them without embedding Grafana. */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final RestClient http;
    private final String namespace;

    public MetricsService(RestClient.Builder builder, DeployHubProperties props) {
        this.http = builder.baseUrl(props.prometheus().url()).build();
        this.namespace = props.kubernetes().namespace();
    }

    public MetricsView fetch(Application app, int minutes) {
        long end = Instant.now().getEpochSecond();
        long start = end - minutes * 60L;
        long step = Math.max(15, (minutes * 60L) / 120);   // ~120 points per chart

        String name = app.getName();
        // Pod names are <deployment>-<replicaset hash>-<pod hash>; PromQL regexes are fully anchored.
        String pods = "namespace=\"%s\",pod=~\"%s-[a-z0-9]+-[a-z0-9]+\",container!=\"\"".formatted(namespace, name);

        try {
            return new MetricsView(true,
                    range("sum(rate(container_cpu_usage_seconds_total{" + pods + "}[2m])) * 1000", start, end, step),
                    range("sum(container_memory_working_set_bytes{" + pods + "}) / 1048576", start, end, step),
                    range("sum(kube_deployment_status_replicas_available{namespace=\"%s\",deployment=\"%s\"})"
                            .formatted(namespace, name), start, end, step),
                    range("sum(rate(http_server_requests_seconds_count{namespace=\"%s\",service=\"%s\"}[1m]))"
                            .formatted(namespace, name), start, end, step));
        } catch (RestClientException e) {
            log.debug("Prometheus not reachable: {}", e.getMessage());
            return new MetricsView(false, List.of(), List.of(), List.of(), List.of());
        }
    }

    private List<Point> range(String query, long start, long end, long step) {
        // POST form avoids URL-encoding trouble with PromQL braces and quotes.
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", query);
        form.add("start", String.valueOf(start));
        form.add("end", String.valueOf(end));
        form.add("step", String.valueOf(step));

        JsonNode body = http.post()
                .uri("/api/v1/query_range")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        List<Point> points = new ArrayList<>();
        JsonNode result = body == null ? null : body.path("data").path("result");
        if (result == null || !result.isArray() || result.isEmpty()) {
            return points;
        }
        for (JsonNode pair : result.get(0).path("values")) {
            double v = Double.parseDouble(pair.get(1).asText());
            if (!Double.isNaN(v)) {
                points.add(new Point(pair.get(0).asLong(), Math.round(v * 100) / 100.0));
            }
        }
        return points;
    }
}
