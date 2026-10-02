package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class LocalBuildPathContractTest {

  private static final Path MAKEFILE = Path.of("makefile");
  private static final Path DOCKERFILE = Path.of("Dockerfile");
  private static final Path DOCKERIGNORE = Path.of(".dockerignore");

  @Test
  void makefileHasNoPrivateDownloadOrMiniPcProductionDeployPath() throws IOException {
    String makefile = Files.readString(MAKEFILE);
    String normalized = makefile.toLowerCase(Locale.ROOT);

    assertThat(normalized)
        .doesNotContain(
            "getkey",
            "private_",
            "poppang-private",
            "raw.githubusercontent.com",
            "github_access_token",
            "include .env",
            "curl ",
            "deploy-prod.sh",
            "poppang-server",
            "scp ",
            "ssh ");

    assertThat(recipe(makefile, "prod-deploy"))
        .as("Manual production deployment must fail closed")
        .contains("exit 1");
    assertThat(recipe(makefile, "build-jar"))
        .contains("./gradlew clean bootJar", "verify-build-artifacts.sh jar build/libs/*.jar");
    assertThat(recipe(makefile, "build-image"))
        .contains(
            "docker buildx build --platform linux/amd64",
            "verify-build-artifacts.sh image $(IMAGE_NAME)");
  }

  @Test
  void dockerBuildContextContainsOnlyBootJar() throws IOException {
    List<String> rules =
        Files.readAllLines(DOCKERIGNORE).stream()
            .map(String::trim)
            .filter(line -> !line.isEmpty() && !line.startsWith("#"))
            .toList();

    assertThat(rules).containsExactly("*", "!build/libs/*.jar");
  }

  @Test
  void dockerfilePinsHeapAndKeepsContainerSupport() throws IOException {
    String dockerfile = Files.readString(DOCKERFILE);

    assertThat(dockerfile)
        .contains(
            "COPY build/libs/*.jar app.jar",
            "ENV JAVA_TOOL_OPTIONS=\"-Xmx1g\"",
            "ENTRYPOINT [\"java\",\"-jar\",\"app.jar\"]")
        .doesNotContain("UseContainerSupport", "application", ".p8", "COPY . ");
  }

  private static String recipe(String makefile, String target) {
    Matcher matcher =
        Pattern.compile("(?m)^" + Pattern.quote(target) + ":[^\\n]*\\n((?:\\t[^\\n]*\\n?)*)")
            .matcher(makefile);
    assertThat(matcher.find()).as("makefile target %s must exist", target).isTrue();
    return matcher.group(1);
  }
}
