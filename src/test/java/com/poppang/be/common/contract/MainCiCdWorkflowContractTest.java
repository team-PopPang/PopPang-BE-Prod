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

class MainCiCdWorkflowContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/cicd.yml");
  private static final String VALIDATION_COMMAND = "./gradlew clean test spotlessCheck --no-daemon";
  private static final String MAIN_REVISION_GUARD =
      "github.event_name == 'push' || (github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main')";

  private String workflowSource;
  private Map<Object, Object> workflow;

  @BeforeEach
  void loadWorkflow() throws IOException {
    workflowSource = Files.readString(WORKFLOW_PATH);
    workflow = asMap(new Yaml().load(workflowSource), "Workflow must be valid YAML");
  }

  @Test
  void keepsOnlyApprovedMainDeploymentTriggersAndGuardsManualRuns() {
    Map<Object, Object> triggers = asMap(rootValue("on"), "Workflow must declare triggers");
    assertThat(triggers.keySet()).containsExactlyInAnyOrder("push", "workflow_dispatch");

    Map<Object, Object> push = asMap(triggers.get("push"), "push trigger must be configured");
    assertThat(asList(push.get("branches"), "push branches")).containsExactly("main");
    assertThat(triggers.get("workflow_dispatch")).isNull();

    Map<Object, Object> permissions =
        asMap(rootValue("permissions"), "Workflow permissions must be explicit");
    assertThat(permissions).containsOnly(entry("contents", "read"));
    for (Object jobValue : jobs().values()) {
      assertThat(asMap(jobValue, "Every job must be a mapping"))
          .as("Jobs must not override the workflow's read-only permissions")
          .doesNotContainKey("permissions");
    }

    assertThat(job("verify").get("if"))
        .as("Manual deployments must be rejected unless the selected revision is main")
        .isEqualTo(MAIN_REVISION_GUARD);
  }

  @Test
  void scopesProductionCredentialsToOnlyTheStepsThatNeedThem() {
    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    Map<Object, Object> environment =
        asMap(buildAndDeploy.get("env"), "Production job must declare its environment");
    assertThat(environment)
        .containsOnlyKeys(
            "APP_NAME",
            "CONTAINER_NAME",
            "SERVER_DIR",
            "HEALTH_URL",
            "ROLLBACK_DIR",
            "PRIVATE_BASE_URL")
        .containsEntry("APP_NAME", "poppang-prod")
        .containsEntry("CONTAINER_NAME", "poppang-prod")
        .containsEntry("SERVER_DIR", "/home/poppang/opt/deploy")
        .containsEntry("HEALTH_URL", "http://localhost:4002/actuator/health")
        .containsEntry("ROLLBACK_DIR", "/home/poppang/opt/deploy/rollback")
        .containsEntry(
            "PRIVATE_BASE_URL",
            "https://raw.githubusercontent.com/team-PopPang/PopPang-Private/BE");

    Map<Object, Object> downloadPrivateConfigs = step(buildAndDeploy, "Download private configs");
    assertThat(asMap(downloadPrivateConfigs.get("env"), "Private download environment"))
        .containsOnly(entry("PERSONAL_ACCESS_TOKEN", "${{ secrets.PERSONAL_ACCESS_TOKEN }}"));

    int remoteActionCount = 0;
    for (Object stepValue : asList(buildAndDeploy.get("steps"), "Build and deploy steps")) {
      Map<Object, Object> productionStep = asMap(stepValue, "Every step must be a mapping");
      Object action = productionStep.get("uses");
      if ("appleboy/scp-action@v0.1.7".equals(action)
          || "appleboy/ssh-action@v1.2.5".equals(action)) {
        remoteActionCount++;
        assertThat(asMap(productionStep.get("with"), "Remote action inputs"))
            .containsEntry("host", "${{ secrets.SERVER_HOST }}")
            .containsEntry("username", "${{ secrets.SERVER_USER }}")
            .containsEntry("key", "${{ secrets.SERVER_SSH_KEY }}");
      } else if (!"Download private configs".equals(productionStep.get("name"))) {
        assertThat(String.valueOf(productionStep))
            .as("Unrelated build steps must not receive production credentials")
            .doesNotContain(
                "${{ secrets.PERSONAL_ACCESS_TOKEN }}",
                "${{ secrets.SERVER_HOST }}",
                "${{ secrets.SERVER_USER }}",
                "${{ secrets.SERVER_SSH_KEY }}");
      }
    }
    assertThat(remoteActionCount).isEqualTo(3);
    assertThat(workflowSource)
        .doesNotContain(
            "${{ env.SSH_HOST }}", "${{ env.SERVER_USER }}", "${{ env.SERVER_SSH_KEY }}");
  }

  @Test
  void hasIndependentIsolatedVerifyJobWithExactValidationCommand() {
    Map<Object, Object> verify = job("verify");
    assertThat(verify).doesNotContainKeys("needs", "env");
    assertThat(checkoutRef(verify)).isEqualTo("${{ github.sha }}");
    assertThat(gradleCommands(verify)).containsExactly(VALIDATION_COMMAND);

    String normalizedVerify = verify.toString().toLowerCase(Locale.ROOT);
    assertThat(normalizedVerify)
        .doesNotContain(
            "${{ secrets.",
            "private_base_url",
            "personal_access_token",
            "application.yml",
            "application-prod.yml",
            ".p8",
            "jdbc:",
            "redis://",
            "spring.datasource",
            "spring.data.redis",
            "docker",
            "appleboy",
            "ssh");
  }

  @Test
  void requiresSuccessfulVerifyBeforeEverySecretOrProductionJob() {
    Map<Object, Object> jobs = jobs();
    assertThat(jobs.keySet()).containsExactlyInAnyOrder("verify", "build-and-deploy", "notify");
    assertThat(rootValue("env"))
        .as("Operational values and secrets must not be visible to verify")
        .isNull();

    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    assertThat(needs(buildAndDeploy)).containsExactly("verify");
    assertThat(buildAndDeploy.get("if")).isEqualTo("needs.verify.result == 'success'");

    Map<Object, Object> notify = job("notify");
    assertThat(needs(notify)).containsExactlyInAnyOrder("verify", "build-and-deploy");
    assertThat(String.valueOf(notify.get("if")))
        .contains("always()", "needs.verify.result == 'success'");

    for (Map.Entry<Object, Object> entry : jobs.entrySet()) {
      String jobId = String.valueOf(entry.getKey());
      String jobSource = String.valueOf(entry.getValue()).toLowerCase(Locale.ROOT);
      if (jobSource.contains("${{ secrets.") || containsProductionOperation(jobSource)) {
        assertThat(needs(asMap(entry.getValue(), "Every job must be a mapping")))
            .as("Sensitive job %s must directly depend on verify", jobId)
            .contains("verify");
      }
    }
  }

  @Test
  void buildsAndDeploysTheVerifiedGithubShaWithProductionNames() {
    Map<Object, Object> verify = job("verify");
    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    assertThat(checkoutRef(verify)).isEqualTo("${{ github.sha }}");
    assertThat(checkoutRef(buildAndDeploy)).isEqualTo("${{ github.sha }}");

    Map<Object, Object> environment =
        asMap(buildAndDeploy.get("env"), "Production job must declare its environment");
    assertThat(environment.get("APP_NAME")).isEqualTo("poppang-prod");
    assertThat(environment.get("CONTAINER_NAME")).isEqualTo("poppang-prod");
    assertThat(workflowSource).doesNotContain("poppang-dev");

    String versionCommand = runStep(buildAndDeploy, "Set image version");
    assertThat(versionCommand)
        .contains(
            "VERSION=\"${GITHUB_SHA::7}\"",
            "IMAGE_NAME=\"${APP_NAME}:${VERSION}\"",
            "IMAGE_TAR=\"${APP_NAME}-${VERSION}.tar\"");

    Map<Object, Object> dockerBuild = step(buildAndDeploy, "Build Docker image");
    assertThat(asMap(dockerBuild.get("with"), "Docker build inputs").get("tags"))
        .isEqualTo("${{ env.IMAGE_NAME }}");

    String remoteDeploy = usesStep(buildAndDeploy, "appleboy/ssh-action@v1.2.5").toString();
    assertThat(remoteDeploy)
        .contains(
            "CONTAINER_NAME=${{ env.CONTAINER_NAME }}",
            "${{ env.IMAGE_TAR }}",
            "${{ env.IMAGE_NAME }}");
  }

  @Test
  void downloadsOnlyRequiredPrivateFilesWithFailClosedChecks() {
    Map<Object, Object> buildAndDeploy = job("build-and-deploy");
    String downloadCommand = runStep(buildAndDeploy, "Download private configs");

    assertThat(downloadCommand)
        .contains(
            "set -euo pipefail",
            "curl --fail --silent --show-error --location",
            "[[ ! -s \"${destination}\" ]]",
            "--quiet",
            "!doctype",
            "html",
            "not[[:space:]]+found",
            "src/main/resources/application.yml",
            "src/main/resources/application-prod.yml",
            "src/main/resources/auth/AuthKey_382T2TB4RW.p8")
        .doesNotContain(
            "application-dev.yml",
            "application-local.yml",
            "set -x",
            "cat ",
            "head ",
            "tail ",
            "sed ");

    for (Map.Entry<Object, Object> entry : jobs().entrySet()) {
      if (!"build-and-deploy".equals(String.valueOf(entry.getKey()))) {
        assertThat(String.valueOf(entry.getValue()))
            .as("Private downloads must exist only in the gated CD build job")
            .doesNotContain(
                "PRIVATE_BASE_URL",
                "PERSONAL_ACCESS_TOKEN",
                "application.yml",
                "application-prod.yml",
                "AuthKey_382T2TB4RW.p8");
      }
    }
  }

  private boolean containsProductionOperation(String source) {
    return source.contains("docker/build-push-action")
        || source.contains("docker save")
        || source.contains("appleboy/scp-action")
        || source.contains("appleboy/ssh-action")
        || source.contains("download private configs");
  }

  private List<String> gradleCommands(Map<Object, Object> job) {
    List<String> commands = new ArrayList<>();
    for (Object stepValue : asList(job.get("steps"), "Job steps")) {
      Map<Object, Object> step = asMap(stepValue, "Every step must be a mapping");
      Object run = step.get("run");
      if (run instanceof String command && command.contains("./gradlew")) {
        commands.add(command);
      }
    }
    return commands;
  }

  private String checkoutRef(Map<Object, Object> job) {
    Map<Object, Object> checkout = usesStep(job, "actions/checkout@v4");
    return String.valueOf(asMap(checkout.get("with"), "Checkout inputs").get("ref"));
  }

  private Map<Object, Object> usesStep(Map<Object, Object> job, String action) {
    for (Object stepValue : asList(job.get("steps"), "Job steps")) {
      Map<Object, Object> step = asMap(stepValue, "Every step must be a mapping");
      if (action.equals(step.get("uses"))) {
        return step;
      }
    }
    throw new AssertionError("Missing action step: " + action);
  }

  private String runStep(Map<Object, Object> job, String name) {
    return String.valueOf(step(job, name).get("run"));
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
    if (value == null) {
      return List.of();
    }
    if (value instanceof String dependency) {
      return List.of(dependency);
    }
    List<String> dependencies = new ArrayList<>();
    for (Object dependency : asList(value, "Job needs")) {
      dependencies.add(String.valueOf(dependency));
    }
    return dependencies;
  }

  private Map<Object, Object> job(String jobId) {
    assertThat(jobs()).containsKey(jobId);
    return asMap(jobs().get(jobId), "Job must be a mapping: " + jobId);
  }

  private Map<Object, Object> jobs() {
    return asMap(rootValue("jobs"), "Workflow must declare jobs");
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
