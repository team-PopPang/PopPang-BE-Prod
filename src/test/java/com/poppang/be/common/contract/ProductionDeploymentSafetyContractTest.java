package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * EC2 배포는 root 진입점(/usr/local/sbin/poppang-deploy-be)이 flock·tar 검증·BE 교체·60초 health·직전 이미지 롤백을
 * 맡는다. helper는 계약을 검증한 뒤 서버 wrapper를 한 번 호출하고 stdout과 종료 코드를 그대로 전파한다.
 */
class ProductionDeploymentSafetyContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/cicd.yml");
  private static final Path DEPLOYMENT_SCRIPT =
      Path.of("scripts/ci/production-deploy-with-rollback.sh");
  private static final String HEALTH_URL = "http://localhost:4002/actuator/health";
  private static final String NEW_IMAGE = "poppang-prod:abcdef1";
  private static final String NEW_TAR = "/home/poppang-deploy/app/poppang-prod-abcdef1.tar";
  private static final String COMMIT_SHA = "abcdef1234567890abcdef1234567890abcdef12";

  @TempDir Path tempDir;

  private Map<Object, Object> workflow;

  @BeforeEach
  void loadWorkflow() throws IOException {
    workflow =
        asMap(new Yaml().load(Files.readString(WORKFLOW_PATH)), "Workflow must be valid YAML");
  }

  @Test
  void serializesProductionDeploymentsWithoutCancellingTheRunningDeployment() {
    Map<Object, Object> concurrency =
        asMap(rootValue("concurrency"), "Production concurrency must be configured");

    assertThat(concurrency.get("group")).isEqualTo("poppang-production-deployment");
    assertThat(concurrency.get("cancel-in-progress")).isEqualTo(false);
    assertThat(concurrency.get("queue")).isEqualTo("max");
  }

  @Test
  void invokesTheDelegatingHelperOnlyAfterTheVerifyGate() {
    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    assertThat(buildAndDeploy.get("needs")).isEqualTo("verify");
    assertThat(buildAndDeploy.get("if")).isEqualTo("needs.verify.result == 'success'");

    Map<Object, Object> environment =
        asMap(buildAndDeploy.get("env"), "Production job environment");
    assertThat(environment)
        .containsEntry("HEALTH_URL", HEALTH_URL)
        .containsEntry("SERVER_DIR", "/home/poppang-deploy/app")
        .containsEntry("DEPLOY_SCRIPT", "/opt/poppang/deploy-prod.sh")
        .doesNotContainKey("ROLLBACK_DIR");

    Map<Object, Object> copyHelper = step(buildAndDeploy, "Copy deployment helper to server");
    assertThat(copyHelper.get("uses")).isEqualTo("appleboy/scp-action@v0.1.7");
    assertThat(asMap(copyHelper.get("with"), "Helper copy inputs").get("source"))
        .isEqualTo("scripts/ci/production-deploy-with-rollback.sh");

    String remoteScript =
        String.valueOf(
            asMap(step(buildAndDeploy, "Remote deploy").get("with"), "SSH inputs").get("script"));
    assertThat(remoteScript)
        .contains(
            "bash ${{ env.SERVER_DIR }}/scripts/ci/production-deploy-with-rollback.sh",
            "${{ env.SERVER_DIR }}/${{ env.IMAGE_TAR }}",
            "${{ env.IMAGE_NAME }}",
            "${{ env.CONTAINER_NAME }}",
            "${{ env.DEPLOY_SCRIPT }}",
            "${{ env.HEALTH_URL }}",
            "${{ github.sha }}",
            "${{ github.run_id }}-${{ github.run_attempt }}")
        .doesNotContain("ROLLBACK_DIR", "${{ env.SERVER_DIR }}/deploy-prod.sh", "sudo", "docker");
  }

  @Test
  void helperDelegatesTheWholeTransactionWithoutDockerHealthOrRollbackOfItsOwn()
      throws IOException {
    String source = deploymentScriptSource();

    assertThat(source)
        .contains(
            "EXPECTED_CONTAINER_NAME=\"poppang-prod\"",
            "EXPECTED_HEALTH_URL=\"" + HEALTH_URL + "\"",
            "UPLOAD_DIR=\"/home/poppang-deploy/app\"",
            "\"${deploy_script}\" \"${new_tar}\" \"${new_image}\" 2>/dev/null",
            "exit \"${deploy_exit_code}\"")
        .doesNotContain(
            "docker", "curl", "jq ", "sleep", "rollback_tar", "rm ", "set -x", "printenv", "sudo");
  }

  @Test
  void propagatesEveryEntrypointResultAndExitCodeAfterExactlyOneCall() throws Exception {
    Map<String, Integer> statuses = new java.util.LinkedHashMap<>();
    statuses.put("success", 0);
    statuses.put("rolled_back", 10);
    statuses.put("rollback_failed", 20);
    statuses.put("rollback_unavailable", 21);
    statuses.put("rejected", 64);
    statuses.put("busy", 75);
    statuses.put("failed", 70);

    for (Map.Entry<String, Integer> status : statuses.entrySet()) {
      DryRunResult result = runHelper(status.getKey(), status.getValue(), validArguments());

      assertThat(result.exitCode()).as(status.getKey()).isEqualTo(status.getValue());
      List<String> lines = result.output().lines().toList();
      assertThat(lines.get(0))
          .isEqualTo("deployment_start commit=" + COMMIT_SHA + " new_image=" + NEW_IMAGE);
      assertThat(lines.get(lines.size() - 1))
          .isEqualTo("POPPANG_DEPLOY_RESULT status=" + status.getKey());
      assertThat(result.output()).doesNotContain("wrapper-stderr-must-not-leak");
      assertThat(result.wrapperCalls().lines().toList())
          .as("The entrypoint wrapper must be called exactly once")
          .containsExactly(NEW_TAR + "|" + NEW_IMAGE);
      assertThat(result.forbiddenToolCalls()).as("No docker/curl/jq/sleep/rm").isBlank();
    }
  }

  @Test
  void rejectsContractViolationsWithoutCallingTheEntrypoint() throws Exception {
    List<List<String>> invalidArguments =
        List.of(
            replace(validArguments(), 0, "/tmp/poppang-prod-abcdef1.tar"),
            replace(validArguments(), 0, "/home/poppang-deploy/app/poppang-prod-other.tar"),
            replace(validArguments(), 1, "poppang-dev:abcdef1"),
            replace(validArguments(), 1, "poppang-prod:-bad"),
            replace(validArguments(), 2, "poppang-dev"),
            replace(validArguments(), 4, "http://localhost:4003/actuator/health"),
            replace(validArguments(), 5, "not-a-sha"),
            replace(validArguments(), 6, "run key"),
            validArguments().subList(0, 6));

    for (List<String> arguments : invalidArguments) {
      DryRunResult result = runHelper("success", 0, arguments);

      assertThat(result.exitCode()).as(String.valueOf(arguments)).isEqualTo(64);
      assertThat(result.output()).contains("manual_recovery=not_required");
      assertThat(result.wrapperCalls()).as(String.valueOf(arguments)).isBlank();
    }
  }

  private List<String> validArguments() {
    return new ArrayList<>(
        List.of(
            NEW_TAR,
            NEW_IMAGE,
            "poppang-prod",
            tempDir.resolve("wrapper/deploy-prod.sh").toString(),
            HEALTH_URL,
            COMMIT_SHA,
            "123-1"));
  }

  private static List<String> replace(List<String> arguments, int index, String value) {
    List<String> replaced = new ArrayList<>(arguments);
    replaced.set(index, value);
    return replaced;
  }

  private DryRunResult runHelper(String status, int exitCode, List<String> arguments)
      throws Exception {
    assertThat(DEPLOYMENT_SCRIPT).as("The production deployment helper must exist").exists();

    Path scenario = Files.createTempDirectory(tempDir, status + "-");
    Path binDirectory = Files.createDirectories(scenario.resolve("bin"));
    Path stateDirectory = Files.createDirectories(scenario.resolve("state"));
    for (String tool : List.of("docker", "curl", "jq", "sleep", "rm", "sudo")) {
      writeExecutable(binDirectory.resolve(tool), forbiddenToolStub(tool));
    }
    Path wrapper = tempDir.resolve("wrapper/deploy-prod.sh");
    Files.createDirectories(wrapper.getParent());
    writeExecutable(wrapper, wrapperStub());

    List<String> command = new ArrayList<>(List.of("bash", DEPLOYMENT_SCRIPT.toString()));
    command.addAll(arguments);
    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.directory(Path.of(".").toFile());
    processBuilder.environment().put("STATE_DIR", stateDirectory.toString());
    processBuilder.environment().put("RESULT_STATUS", status);
    processBuilder.environment().put("RESULT_EXIT", String.valueOf(exitCode));
    processBuilder
        .environment()
        .put("PATH", binDirectory + ":" + System.getenv().getOrDefault("PATH", ""));

    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());
    int helperExitCode = process.waitFor();

    return new DryRunResult(
        helperExitCode,
        output,
        readIfPresent(stateDirectory.resolve("wrapper-calls")),
        readIfPresent(stateDirectory.resolve("forbidden-calls")));
  }

  private String wrapperStub() {
    return """
        #!/usr/bin/env bash
        printf '%s|%s\n' "$1" "$2" >> "${STATE_DIR}/wrapper-calls"
        printf '%s\n' 'wrapper-stderr-must-not-leak' >&2
        case "${RESULT_STATUS}" in
          success) printf '%s\n' 'new_health=UP attempts=1' 'deployment_result=success' ;;
          rolled_back) printf '%s\n' 'rollback_result=success' 'deployment_result=failed_new_release' ;;
          rollback_failed) printf '%s\n' 'rollback_result=failed' 'deployment_result=failed_new_release' ;;
          rollback_unavailable)
            printf '%s\n' 'rollback_result=unavailable' 'deployment_result=failed_new_release' ;;
        esac
        printf 'POPPANG_DEPLOY_RESULT status=%s\n' "${RESULT_STATUS}"
        exit "${RESULT_EXIT}"
        """;
  }

  private String forbiddenToolStub(String tool) {
    return """
        #!/usr/bin/env bash
        printf '%s %s\n' "__TOOL__" "$*" >> "${STATE_DIR}/forbidden-calls"
        exit 99
        """
        .replace("__TOOL__", tool);
  }

  private String deploymentScriptSource() throws IOException {
    assertThat(DEPLOYMENT_SCRIPT).as("The production deployment helper must exist").exists();
    return Files.readString(DEPLOYMENT_SCRIPT);
  }

  private void writeExecutable(Path path, String source) throws IOException {
    Files.writeString(path, source);
    assertThat(path.toFile().setExecutable(true)).isTrue();
  }

  private String readIfPresent(Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path) : "";
  }

  private Map<Object, Object> job(String jobId) {
    Map<Object, Object> jobs = asMap(rootValue("jobs"), "Workflow must declare jobs");
    assertThat(jobs).containsKey(jobId);
    return asMap(jobs.get(jobId), "Job must be a mapping: " + jobId);
  }

  private Map<Object, Object> step(Map<Object, Object> job, String name) {
    for (Object stepValue : asList(job.get("steps"), "Job steps")) {
      Map<Object, Object> step = asMap(stepValue, "Every step must be a mapping");
      if (name.equals(step.get("name"))) {
        return step;
      }
    }
    throw new AssertionError("Missing step: " + name);
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

  private record DryRunResult(
      int exitCode, String output, String wrapperCalls, String forbiddenToolCalls) {}
}
