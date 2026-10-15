package io.deployhub.dto;

/** Input rules shared by request validation and unit tests. */
public final class Patterns {
    private Patterns() {}

    /** DNS-1035 label: used as the k8s Deployment/Service/Ingress name and the URL host. */
    public static final String APP_NAME = "^[a-z]([-a-z0-9]{0,28}[a-z0-9])?$";

    /** v1 only accepts public github.com repositories over https. */
    public static final String GITHUB_REPO_URL = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?(\\.git)?/?$";

    public static final String BRANCH = "^[A-Za-z0-9._/-]{1,100}$";

    /** Optional sub-folder inside the repo; ".." is rejected separately. */
    public static final String APP_PATH = "^[A-Za-z0-9._/-]{0,200}$";

    public static final String HEALTH_PATH = "^/[A-Za-z0-9._/-]{0,199}$";
}
