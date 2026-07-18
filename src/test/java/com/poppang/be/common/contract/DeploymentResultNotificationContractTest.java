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

class DeploymentResultNotificationContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/cicd.yml");
  private static final Path REPORT_SCRIPT = Path.of("scripts/ci/report-deployment-result.sh");
  private static final String COMMIT_SHA = "abcdef1234567890abcdef1234567890abcdef12";
  private static final String IMAGE_NAME = "poppang-prod:abcdef1";
  private static final String APPROVED_SEND_MAIL_ACTION =
      "dawidd6/action-send-mail@4226df7daafa6fc901a43789c49bf7ab309066e7";

  @TempDir Path tempDir;

  private Map<Object, Object> workflow;

  @BeforeEach
  void loadWorkflow() throws IOException {
    workflow =
        asMap(new Yaml().load(Files.readString(WORKFLOW_PATH)), "Workflow must be valid YAML");
  }

  @Test
  void capturesRemoteResultAndReportsItBeforeEnforcingDeploymentFailure() {
    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    List<Object> steps = asList(buildAndDeploy.get("steps"), "Build and deploy steps");

    Map<Object, Object> remoteDeploy = step(buildAndDeploy, "Remote deploy");
    assertThat(remoteDeploy.get("id")).isEqualTo("remote_deploy");
    assertThat(remoteDeploy.get("uses")).isEqualTo("appleboy/ssh-action@v1.2.5");
    assertThat(remoteDeploy.get("continue-on-error")).isEqualTo(true);

    Map<Object, Object> sshInputs = asMap(remoteDeploy.get("with"), "SSH action inputs");
    assertThat(sshInputs.get("capture_stdout")).isEqualTo(true);
    assertThat(String.valueOf(sshInputs.get("script")))
        .contains("remote_exit_code=${deploy_exit_code}", "exit 0");

    Map<Object, Object> report = step(buildAndDeploy, "Report deployment result");
    assertThat(report.get("if")).isEqualTo("always()");
    assertThat(report.get("run")).isEqualTo("scripts/ci/report-deployment-result.sh");
    assertThat(asMap(report.get("env"), "Deployment report environment"))
        .containsEntry("DEPLOYMENT_ACTION_OUTCOME", "${{ steps.remote_deploy.outcome }}")
        .containsEntry("DEPLOYMENT_OUTPUT", "${{ steps.remote_deploy.outputs.stdout }}")
        .containsEntry("DEPLOYMENT_COMMIT", "${{ github.sha }}")
        .containsEntry("DEPLOYMENT_IMAGE", "${{ env.IMAGE_NAME }}");

    assertThat(steps.indexOf(report))
        .as("The report step must be the final deployment-result gate")
        .isEqualTo(steps.size() - 1);
  }

  @Test
  void emailActionsAreNonBlockingAndCannotReplaceTheDeploymentResult() {
    Map<Object, Object> notify = job("notify");
    assertThat(needs(notify)).containsExactlyInAnyOrder("verify", "build-and-deploy");
    assertThat(String.valueOf(notify.get("if")))
        .contains("always()", "needs.verify.result == 'success'");

    List<Object> notificationSteps = asList(notify.get("steps"), "Notification steps");
    assertThat(notificationSteps).hasSize(2);
    for (Object stepValue : notificationSteps) {
      Map<Object, Object> notificationStep = asMap(stepValue, "Notification step");
      assertThat(notificationStep.get("uses")).isEqualTo(APPROVED_SEND_MAIL_ACTION);
      assertThat(notificationStep.get("continue-on-error"))
          .as("Email delivery is auxiliary and must be non-blocking")
          .isEqualTo(true);
    }
  }

  @Test
  void successfulDeploymentProducesSuccessfulSummaryAndExitCode() throws Exception {
    ReportResult result =
        runReport(
            "success",
            """
            new_health=UP attempts=1
            deployment_result=success
            remote_exit_code=0
            """);

    assertThat(result.exitCode()).isZero();
    assertThat(result.summary())
        .contains(
            "| Deployment | SUCCESS |",
            "| Rollback | NOT_REQUIRED |",
            "| Manual recovery | NO |",
            "| Commit | `" + COMMIT_SHA + "` |",
            "| Image | `" + IMAGE_NAME + "` |");
  }

  @Test
  void failedDeploymentWithSuccessfulRollbackIsSummarizedButStillFails() throws Exception {
    ReportResult result =
        runReport(
            "success",
            """
            new_health=UNHEALTHY attempts=12
            rollback_health=UP attempts=1
            rollback_result=success
            deployment_result=failed_new_release
            remote_exit_code=1
            """);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.summary())
        .contains(
            "| Deployment | FAILED |",
            "| Rollback | SUCCESS |",
            "| Manual recovery | NO |",
            "The previous image was restored, but the new release was rejected.");
  }

  @Test
  void failedDeploymentWithFailedRollbackRequiresManualRecoveryWithoutLeakingRawOutput()
      throws Exception {
    ReportResult result =
        runReport(
            "success",
            """
            rollback_result=failed manual_recovery=required
            deployment_result=failed_new_release
            remote_exit_code=1
            credential_should_not_appear=super-secret-value
            """);

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.summary())
        .contains(
            "| Deployment | FAILED |",
            "| Rollback | FAILED |",
            "| Manual recovery | YES |",
            "Automatic rollback failed. Immediate manual recovery is required.")
        .doesNotContain("super-secret-value", "credential_should_not_appear");
    assertThat(result.output()).doesNotContain("super-secret-value");
  }

  @Test
  void actionFailureWithoutStructuredResultIsReportedAsUnknownAndRequiresRecovery()
      throws Exception {
    ReportResult result = runReport("failure", "ssh_transport_failed=true\n");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.summary())
        .contains(
            "| Deployment | FAILED |",
            "| Rollback | UNKNOWN |",
            "| Manual recovery | YES |",
            "Deployment or result capture failed before rollback could be confirmed.")
        .doesNotContain("ssh_transport_failed");
  }

  private ReportResult runReport(String actionOutcome, String deploymentOutput) throws Exception {
    assertThat(REPORT_SCRIPT)
        .as("The deployment report helper must exist before its contract can pass")
        .exists();

    Path summary = tempDir.resolve("summary-" + actionOutcome + ".md");
    ProcessBuilder processBuilder =
        new ProcessBuilder("bash", REPORT_SCRIPT.toString()).redirectErrorStream(true);
    processBuilder.directory(Path.of(".").toFile());
    processBuilder.environment().put("DEPLOYMENT_ACTION_OUTCOME", actionOutcome);
    processBuilder.environment().put("DEPLOYMENT_OUTPUT", deploymentOutput);
    processBuilder.environment().put("DEPLOYMENT_COMMIT", COMMIT_SHA);
    processBuilder.environment().put("DEPLOYMENT_IMAGE", IMAGE_NAME);
    processBuilder.environment().put("GITHUB_STEP_SUMMARY", summary.toString());

    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes());
    int exitCode = process.waitFor();

    return new ReportResult(
        exitCode, output, Files.exists(summary) ? Files.readString(summary) : "");
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

  private List<String> needs(Map<Object, Object> job) {
    Object value = job.get("needs");
    if (value instanceof String dependency) {
      return List.of(dependency);
    }
    return asList(value, "Job needs").stream().map(String::valueOf).toList();
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

  private record ReportResult(int exitCode, String output, String summary) {}
}
