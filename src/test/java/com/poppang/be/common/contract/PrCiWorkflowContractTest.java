package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PrCiWorkflowContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/build-test.yml");
  private static final String WORKFLOW_NAME = "PopPang BE PR CI";
  private static final String JOB_ID = "pr-ci";
  private static final String STATUS_CHECK_NAME = "PR CI";
  private static final String VALIDATION_COMMAND = "./gradlew clean test spotlessCheck --no-daemon";
  private static final List<String> FORBIDDEN_MARKERS =
      List.of(
          "private_base_url",
          "personal_access_token",
          "poppang-private",
          "${{ secrets.",
          "application.yml",
          "application-prod.yml",
          ".p8",
          "curl",
          "authorization: bearer",
          "jdbc:",
          "mysql://",
          "redis://",
          "spring.datasource",
          "spring.data.redis",
          "database_url",
          "redis_host");

  private String workflowSource;
  private Map<Object, Object> workflow;

  @BeforeEach
  void loadWorkflow() throws IOException {
    workflowSource = Files.readString(WORKFLOW_PATH);
    workflow = asMap(new Yaml().load(workflowSource), "Workflow must be valid YAML");
  }

  @Test
  void usesOnlyApprovedTriggersAndReadOnlyContentsPermission() {
    Map<Object, Object> triggers = asMap(rootValue("on"), "Workflow must declare triggers");
    assertThat(triggers.keySet())
        .as("Only the existing main PR and manual triggers are approved")
        .containsExactlyInAnyOrder("pull_request", "workflow_dispatch");

    Map<Object, Object> pullRequest =
        asMap(triggers.get("pull_request"), "pull_request trigger must be configured");
    assertThat(asList(pullRequest.get("branches"), "pull_request branches"))
        .containsExactly("main");
    assertThat(triggers.get("workflow_dispatch")).isNull();

    Map<Object, Object> permissions =
        asMap(rootValue("permissions"), "Workflow permissions must be explicit");
    assertThat(permissions).containsOnly(entry("contents", "read"));

    Map<Object, Object> jobs = asMap(rootValue("jobs"), "Workflow must declare jobs");
    for (Object jobValue : jobs.values()) {
      assertThat(asMap(jobValue, "Every job must be a mapping"))
          .as("Jobs must not override the workflow's read-only permissions")
          .doesNotContainKey("permissions");
    }
  }

  @Test
  void doesNotReferencePrivateConfigsCredentialsOrExternalServices() {
    assertThat(rootValue("env"))
        .as("PR CI must not declare operational global environment variables")
        .isNull();

    String normalizedSource = workflowSource.toLowerCase(Locale.ROOT);
    assertThat(normalizedSource)
        .as("PR CI must not download or reference private config, credentials, DB, or Redis")
        .doesNotContain(FORBIDDEN_MARKERS.toArray(String[]::new));
  }

  @Test
  void usesStableStatusCheckNameAndExactValidationCommand() {
    assertThat(rootValue("name")).isEqualTo(WORKFLOW_NAME);

    Map<Object, Object> jobs = asMap(rootValue("jobs"), "Workflow must declare jobs");
    assertThat(jobs.keySet()).containsExactly(JOB_ID);
    Map<Object, Object> job = asMap(jobs.get(JOB_ID), "PR CI job must be configured");
    assertThat(job.get("name")).isEqualTo(STATUS_CHECK_NAME);

    List<String> gradleCommands = new ArrayList<>();
    for (Object stepValue : asList(job.get("steps"), "PR CI steps")) {
      Map<Object, Object> step = asMap(stepValue, "Every step must be a mapping");
      Object run = step.get("run");
      if (run instanceof String command && command.contains("./gradlew")) {
        gradleCommands.add(command);
      }
    }

    assertThat(gradleCommands).containsExactly(VALIDATION_COMMAND);
  }

  private Object rootValue(String key) {
    if (workflow.containsKey(key)) {
      return workflow.get(key);
    }
    if ("on".equals(key)) {
      return workflow.get(Boolean.TRUE);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Map<Object, Object> asMap(Object value, String description) {
    assertThat(value).as(description).isInstanceOf(Map.class);
    return (Map<Object, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value, String description) {
    assertThat(value).as(description).isInstanceOf(List.class);
    return (List<Object>) value;
  }
}
