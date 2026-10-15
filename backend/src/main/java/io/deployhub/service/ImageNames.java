package io.deployhub.service;

import java.util.Locale;

/** Image naming rules: every app lives in one GHCR package, one tag per deployment. */
public final class ImageNames {
    private ImageNames() {}

    /** ghcr.io/<owner>/<repository>:<app>-d<deploymentId>. GHCR requires a lowercase owner. */
    public static String forDeployment(String owner, String repository, String appName, long deploymentId) {
        return "ghcr.io/" + owner.toLowerCase(Locale.ROOT) + "/" + repository + ":" + appName + "-d" + deploymentId;
    }

    public static String tagOf(String image) {
        if (image == null) return null;
        int slash = image.lastIndexOf('/');
        int colon = image.lastIndexOf(':');
        return colon > slash ? image.substring(colon + 1) : "latest";
    }
}
