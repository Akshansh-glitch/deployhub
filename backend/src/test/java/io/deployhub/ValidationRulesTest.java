package io.deployhub;

import io.deployhub.dto.Patterns;
import io.deployhub.service.ImageNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationRulesTest {

    @Test
    void appNamesMustBeDnsSafe() {
        assertThat("hello-spring").matches(Patterns.APP_NAME);
        assertThat("a").matches(Patterns.APP_NAME);
        assertThat("Hello").doesNotMatch(Patterns.APP_NAME);
        assertThat("1app").doesNotMatch(Patterns.APP_NAME);
        assertThat("app-").doesNotMatch(Patterns.APP_NAME);
        assertThat("a".repeat(31)).doesNotMatch(Patterns.APP_NAME);
    }

    @Test
    void onlyGithubHttpsUrlsAreAccepted() {
        assertThat("https://github.com/amit/deployhub").matches(Patterns.GITHUB_REPO_URL);
        assertThat("https://github.com/amit/deployhub.git").matches(Patterns.GITHUB_REPO_URL);
        assertThat("https://gitlab.com/amit/x").doesNotMatch(Patterns.GITHUB_REPO_URL);
        assertThat("git@github.com:amit/x.git").doesNotMatch(Patterns.GITHUB_REPO_URL);
        assertThat("https://github.com/amit/x; rm -rf /").doesNotMatch(Patterns.GITHUB_REPO_URL);
    }

    @Test
    void branchesRejectShellCharacters() {
        assertThat("feature/login").matches(Patterns.BRANCH);
        assertThat("main$(whoami)").doesNotMatch(Patterns.BRANCH);
    }

    @Test
    void imageNamesAreLowercaseAndTaggedPerDeployment() {
        String image = ImageNames.forDeployment("AmitK", "deployhub-apps", "hello", 42);
        assertThat(image).isEqualTo("ghcr.io/amitk/deployhub-apps:hello-d42");
        assertThat(ImageNames.tagOf(image)).isEqualTo("hello-d42");
        assertThat(ImageNames.tagOf("localhost:5000/app")).isEqualTo("latest");
    }
}
