package io.deployhub.web;

import io.deployhub.config.DeployHubProperties;
import io.deployhub.dto.Dtos.ConfigView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    private final DeployHubProperties props;

    public ConfigController(DeployHubProperties props) {
        this.props = props;
    }

    @GetMapping("/api/config")
    public ConfigView config() {
        return new ConfigView(props.grafana().url(), props.kubernetes().appDomain(), props.github().configured());
    }
}
