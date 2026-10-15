package io.deployhub.domain;

/**
 * QUEUED -> BUILDING -> BUILD_FAILED
 *                    -> IMAGE_PUSHED -> DEPLOYING -> RUNNING -> SUPERSEDED (when a newer one is running)
 *                                                 -> DEPLOY_FAILED
 */
public enum DeployState {
    QUEUED, BUILDING, BUILD_FAILED, IMAGE_PUSHED, DEPLOYING, RUNNING, DEPLOY_FAILED, SUPERSEDED;

    public boolean isActive() {
        return this == QUEUED || this == BUILDING || this == IMAGE_PUSHED || this == DEPLOYING;
    }
}
