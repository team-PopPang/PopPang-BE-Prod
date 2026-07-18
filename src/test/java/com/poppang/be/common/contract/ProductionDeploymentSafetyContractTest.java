package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class ProductionDeploymentSafetyContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/cicd.yml");
  private static final Path DEPLOYMENT_SCRIPT =
      Path.of("scripts/ci/production-deploy-with-rollback.sh");
  private static final String HEALTH_URL = "http://poppang.co.kr:4002/actuator/health";
  private static final String NEW_IMAGE = "poppang-prod:abcdef1";
  private static final String PREVIOUS_IMAGE = "poppang-prod:1234567";
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
  void invokesTheTestedRollbackHelperOnlyAfterTheChunkFiveGate() {
    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    assertThat(buildAndDeploy.get("needs")).isEqualTo("verify");
    assertThat(buildAndDeploy.get("if")).isEqualTo("needs.verify.result == 'success'");

    Map<Object, Object> environment =
        asMap(buildAndDeploy.get("env"), "Production job environment");
    assertThat(environment.get("HEALTH_URL")).isEqualTo(HEALTH_URL);
    assertThat(environment.get("ROLLBACK_DIR")).isEqualTo("/home/poppang/opt/deploy/rollback");

    Map<Object, Object> copyHelper = step(buildAndDeploy, "Copy deployment helper to server");
    assertThat(copyHelper.get("uses")).isEqualTo("appleboy/scp-action@v0.1.7");
    assertThat(asMap(copyHelper.get("with"), "Helper copy inputs").get("source"))
        .isEqualTo("scripts/ci/production-deploy-with-rollback.sh");

    String remoteScript =
        String.valueOf(
            asMap(step(buildAndDeploy, "Remote deploy").get("with"), "SSH inputs").get("script"));
    assertThat(remoteScript)
        .contains(
            "${{ env.SERVER_DIR }}/scripts/ci/production-deploy-with-rollback.sh",
            "${{ env.SERVER_DIR }}/${{ env.IMAGE_TAR }}",
            "${{ env.IMAGE_NAME }}",
            "${{ env.CONTAINER_NAME }}",
            "${{ env.SERVER_DIR }}/deploy-prod.sh",
            "${{ env.HEALTH_URL }}",
            "${{ env.ROLLBACK_DIR }}",
            "${{ github.sha }}",
            "${{ github.run_id }}-${{ github.run_attempt }}");
  }

  @Test
  void deploymentHelperDefinesFailClosedHealthRollbackCleanupAndLoggingContracts()
      throws IOException {
    String source = deploymentScriptSource();

    assertThat(source)
        .contains(
            "HEALTH_TIMEOUT_SECONDS=60",
            "HEALTH_MAX_ATTEMPTS=12",
            "HEALTH_RETRY_INTERVAL_SECONDS=5",
            "docker inspect --type container --format '{{.Config.Image}}'",
            "docker image inspect",
            "docker save --output",
            "curl --fail --silent --show-error",
            "--max-time",
            "--write-out",
            "^2[0-9]{2}$",
            "local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))",
            "jq --exit-status '.status == \"UP\"'",
            "bash \"${deploy_script}\" \"${new_tar}\" \"${new_image}\"",
            "bash \"${deploy_script}\" \"${rollback_tar}\" \"${previous_image}\"",
            "trap cleanup_rollback_tar EXIT",
            "rm -f -- \"${rollback_tar}\"",
            "rollback_result=success",
            "deployment_result=failed_new_release",
            "manual_recovery=required")
        .doesNotContain(
            "docker system prune",
            "docker image prune",
            "docker image rm",
            "docker rmi",
            "rm -rf",
            "set -x",
            "printenv");
  }

  @Test
  void dryRunNewDeploymentHealthyEndsSuccessfully() throws Exception {
    DryRunResult result = runScenario("new-healthy");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output())
        .contains(
            "previous_image=" + PREVIOUS_IMAGE,
            "new_health=UP attempts=1",
            "deployment_result=success")
        .doesNotContain("manual_recovery=required");
    assertThat(result.deployCalls().lines()).hasSize(1);
    assertThat(result.deployCalls()).contains(NEW_IMAGE).doesNotContain(PREVIOUS_IMAGE);
    assertThat(result.dockerCalls())
        .contains("inspect --type container", "image inspect " + PREVIOUS_IMAGE, "save --output");
    assertThat(result.cleanupCalls()).contains("poppang-prod-rollback-new-healthy.tar");
  }

  @Test
  void dryRunUnhealthyDeploymentRollsBackButStillEndsFailed() throws Exception {
    DryRunResult result = runScenario("rollback-healthy");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains(
            "new_health=UNHEALTHY attempts=12",
            "rollback_health=UP attempts=1",
            "rollback_result=success",
            "deployment_result=failed_new_release")
        .doesNotContain("manual_recovery=required");
    assertThat(result.deployCalls().lines()).hasSize(2);
    assertThat(result.deployCalls())
        .contains(NEW_IMAGE, PREVIOUS_IMAGE, "poppang-prod-rollback-rollback-healthy.tar");
    assertThat(result.cleanupCalls()).contains("poppang-prod-rollback-rollback-healthy.tar");
  }

  @Test
  void dryRunUnhealthyRollbackRequiresManualRecovery() throws Exception {
    DryRunResult result = runScenario("rollback-unhealthy");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains(
            "new_health=UNHEALTHY attempts=12",
            "rollback_health=UNHEALTHY attempts=12",
            "rollback_result=failed",
            "manual_recovery=required",
            "deployment_result=failed_new_release");
    assertThat(result.deployCalls().lines()).hasSize(2);
    assertThat(result.deployCalls()).contains(NEW_IMAGE, PREVIOUS_IMAGE);
    assertThat(result.cleanupCalls()).contains("poppang-prod-rollback-rollback-unhealthy.tar");
  }

  @Test
  void dryRunWithoutPreviousImageRequiresManualRecovery() throws Exception {
    DryRunResult result = runScenario("no-previous-image");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains(
            "previous_image=none",
            "new_health=UNHEALTHY attempts=12",
            "rollback_result=unavailable",
            "manual_recovery=required",
            "deployment_result=failed_new_release");
    assertThat(result.deployCalls().lines()).hasSize(1);
    assertThat(result.dockerCalls()).doesNotContain("image inspect", "save --output");
    assertThat(result.cleanupCalls()).isBlank();
  }

  private DryRunResult runScenario(String scenario) throws Exception {
    assertThat(DEPLOYMENT_SCRIPT)
        .as("The production rollback helper must exist before its dry-run contract can pass")
        .exists();

    Path scenarioDirectory = Files.createDirectories(tempDir.resolve(scenario));
    Path binDirectory = Files.createDirectories(scenarioDirectory.resolve("bin"));
    Path stateDirectory = Files.createDirectories(scenarioDirectory.resolve("state"));
    Path rollbackDirectory = Files.createDirectories(scenarioDirectory.resolve("rollback"));

    writeExecutable(binDirectory.resolve("docker"), dockerStub());
    writeExecutable(binDirectory.resolve("curl"), curlStub());
    writeExecutable(binDirectory.resolve("jq"), jqStub());
    writeExecutable(binDirectory.resolve("sleep"), recordingStub("sleep-calls"));
    writeExecutable(binDirectory.resolve("rm"), recordingStub("cleanup-calls"));
    Path deployScript = scenarioDirectory.resolve("deploy-prod.sh");
    writeExecutable(deployScript, deployStub());

    ProcessBuilder processBuilder =
        new ProcessBuilder(
                "bash",
                DEPLOYMENT_SCRIPT.toString(),
                scenarioDirectory.resolve("new-image.tar").toString(),
                NEW_IMAGE,
                "poppang-prod",
                deployScript.toString(),
                HEALTH_URL,
                rollbackDirectory.toString(),
                COMMIT_SHA,
                scenario)
            .redirectErrorStream(true);
    processBuilder.directory(Path.of(".").toFile());
    processBuilder.environment().put("SCENARIO", scenario);
    processBuilder.environment().put("STATE_DIR", stateDirectory.toString());
    processBuilder
        .environment()
        .put("PATH", binDirectory + ":" + System.getenv().getOrDefault("PATH", ""));

    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();

    return new DryRunResult(
        exitCode,
        output,
        readIfPresent(stateDirectory.resolve("deploy-calls")),
        readIfPresent(stateDirectory.resolve("docker-calls")),
        readIfPresent(stateDirectory.resolve("cleanup-calls")));
  }

  private String deploymentScriptSource() throws IOException {
    assertThat(DEPLOYMENT_SCRIPT)
        .as("The production deployment helper is part of the rollback contract")
        .exists();
    return Files.readString(DEPLOYMENT_SCRIPT);
  }

  private void writeExecutable(Path path, String source) throws IOException {
    Files.writeString(path, source);
    assertThat(path.toFile().setExecutable(true)).isTrue();
  }

  private String readIfPresent(Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path) : "";
  }

  private String dockerStub() {
    return """
        #!/usr/bin/env bash
        set -euo pipefail
        printf '%s\n' "$*" >> "${STATE_DIR}/docker-calls"
        if [[ "$1" == "inspect" ]]; then
          if [[ "${SCENARIO}" == "no-previous-image" ]]; then
            exit 1
          fi
          printf '%s\n' 'poppang-prod:1234567'
          exit 0
        fi
        if [[ "$1" == "image" && "$2" == "inspect" ]]; then
          exit 0
        fi
        if [[ "$1" == "save" ]]; then
          exit 0
        fi
        exit 2
        """;
  }

  private String curlStub() {
    return """
        #!/usr/bin/env bash
        set -euo pipefail
        deploy_count=0
        if [[ -f "${STATE_DIR}/deploy-count" ]]; then
          deploy_count="$(<"${STATE_DIR}/deploy-count")"
        fi
        status=DOWN
        if [[ "${SCENARIO}" == "new-healthy" ]]; then
          status=UP
        elif [[ "${SCENARIO}" == "rollback-healthy" && "${deploy_count}" -ge 2 ]]; then
          status=UP
        fi
        printf '{"status":"%s"}\n200\n' "${status}"
        """;
  }

  private String jqStub() {
    return """
        #!/usr/bin/env bash
        set -euo pipefail
        payload="$(cat)"
        [[ "${payload}" == '{"status":"UP"}' ]]
        """;
  }

  private String recordingStub(String fileName) {
    return """
        #!/usr/bin/env bash
        set -euo pipefail
        printf '%s\n' "$*" >> "${STATE_DIR}/__FILE_NAME__"
        """
        .replace("__FILE_NAME__", fileName);
  }

  private String deployStub() {
    return """
        #!/usr/bin/env bash
        set -euo pipefail
        count=0
        if [[ -f "${STATE_DIR}/deploy-count" ]]; then
          count="$(<"${STATE_DIR}/deploy-count")"
        fi
        count=$((count + 1))
        printf '%s' "${count}" > "${STATE_DIR}/deploy-count"
        printf '%s|%s\n' "$1" "$2" >> "${STATE_DIR}/deploy-calls"
        """;
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
      int exitCode, String output, String deployCalls, String dockerCalls, String cleanupCalls) {}
}
