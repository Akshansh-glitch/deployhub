package io.deployhub;

import io.deployhub.config.DeployHubProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DeployHubProperties.class)
public class DeployHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeployHubApplication.class, args);
    }
}
