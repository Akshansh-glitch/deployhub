package io.deployhub.domain;

/** Supported application runtimes and the port each template listens on by default. */
public enum Runtime {
    SPRINGBOOT(8080),
    NODE(3000),
    FLASK(5000);

    private final int defaultPort;

    Runtime(int defaultPort) {
        this.defaultPort = defaultPort;
    }

    public int defaultPort() {
        return defaultPort;
    }

    /** Value used for Dockerfile template names, workflow inputs and k8s labels. */
    public String key() {
        return name().toLowerCase();
    }
}
