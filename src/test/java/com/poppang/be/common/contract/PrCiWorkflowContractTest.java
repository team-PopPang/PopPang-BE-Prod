package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

  @TempDir Path tempDir;

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

  @Test
  void requiresBranchNameCheckBeforeCheckoutWithoutBlockingManualRuns() {
    Map<Object, Object> job =
        asMap(asMap(rootValue("jobs"), "Workflow jobs").get(JOB_ID), "PR CI job");
    List<Object> steps = asList(job.get("steps"), "PR CI steps");
    Map<Object, Object> check = branchNameCheck();

    assertThat(steps.get(0)).isEqualTo(check);
    assertThat(check)
        .containsEntry("if", "github.event_name == 'pull_request'")
        .containsEntry("shell", "bash")
        .doesNotContainKey("continue-on-error");
    assertThat(job).doesNotContainKey("continue-on-error");
    assertThat(asMap(check.get("env"), "Branch names must enter the shell through env"))
        .containsEntry("BRANCH_NAME", "${{ github.head_ref }}");
    assertThat(check.get("run")).isInstanceOf(String.class);
    assertThat((String) check.get("run")).doesNotContain("${{");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "feature/popup-collector-api",
        "fix/popup-road-address",
        "docs/development-workflow",
        "refactor/popup-response-mapping",
        "test/popup-registration",
        "ci/branch-name-check",
        "chore/update-dependencies",
        "feature/v2-popup-api",
        "fix/address-2026",
        "docs/a"
      })
  void acceptsApprovedBranchNames(String branchName) throws Exception {
    ShellResult result = runBranchNameCheck(branchName);

    assertThat(result.exitCode()).as(result.log()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "main",
        "develop",
        "feat/popup-api",
        "hotfix/popup-api",
        "codex/popup-api",
        "release/1.2.0",
        "feature/123-popup-api",
        "feature/#123",
        "feature/Popup-api",
        "Feature/popup-api",
        "feature/popup_api",
        "feature/popup api",
        "feature/팝업",
        "feature/",
        "feature/-popup",
        "feature/popup-",
        "feature/popup--api",
        "feature/popup/api",
        "feature/popup.api",
        "feature/popup-api\n"
      })
  void rejectsUnapprovedBranchNames(String branchName) throws Exception {
    ShellResult result = runBranchNameCheck(branchName);

    assertThat(result.exitCode()).as(result.log()).isNotZero();
    assertThat(result.log()).contains("::error::");
  }

  @Test
  void treatsShellSyntaxInBranchNamesAsData() throws Exception {
    Path sentinel = tempDir.resolve("branch-name-command-executed");

    ShellResult result = runBranchNameCheck("feature/$(touch " + sentinel + ")");

    assertThat(result.exitCode()).as(result.log()).isNotZero();
    assertThat(sentinel).doesNotExist();
  }

  private Map<Object, Object> branchNameCheck() {
    Map<Object, Object> job =
        asMap(asMap(rootValue("jobs"), "Workflow jobs").get(JOB_ID), "PR CI job");
    return asList(job.get("steps"), "PR CI steps").stream()
        .map(value -> asMap(value, "Workflow step"))
        .filter(step -> "Check branch name".equals(step.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("PR CI must validate the source branch name"));
  }

  private ShellResult runBranchNameCheck(String branchName) throws Exception {
    String script = (String) branchNameCheck().get("run");
    assertThat(script).doesNotContain("${{");
    ProcessBuilder builder = new ProcessBuilder("bash", "-c", script).redirectErrorStream(true);
    builder.directory(tempDir.toFile());
    builder.environment().clear();
    builder.environment().put("PATH", "/usr/bin:/bin");
    builder.environment().put("BRANCH_NAME", branchName);
    Process process = builder.start();
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
      throw new AssertionError("Branch name check did not finish within five seconds");
    }
    return new ShellResult(
        process.exitValue(),
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
  }

  private record ShellResult(int exitCode, String log) {}

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
