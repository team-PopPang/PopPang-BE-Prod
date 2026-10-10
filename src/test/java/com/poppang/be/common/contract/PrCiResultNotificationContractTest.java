package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PrCiResultNotificationContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/pr-ci-notify.yml");
  private Map<Object, Object> workflow;

  @BeforeEach
  void loadWorkflow() throws IOException {
    assertThat(WORKFLOW_PATH).as("PR CI completion notification workflow must exist").exists();
    workflow = asMap(new Yaml().load(Files.readString(WORKFLOW_PATH)));
  }

  @Test
  void observesEveryCompletedPrCiRunIncludingFailuresCancellationsAndManualRuns()
      throws IOException {
    Map<Object, Object> triggers =
        asMap(workflow.containsKey("on") ? workflow.get("on") : workflow.get(Boolean.TRUE));
    assertThat(triggers).containsOnlyKeys("workflow_run");
    Map<Object, Object> trigger = asMap(triggers.get("workflow_run"));
    Map<Object, Object> prCi =
        asMap(new Yaml().load(Files.readString(Path.of(".github/workflows/build-test.yml"))));
    assertThat(trigger)
        .containsOnlyKeys("workflows", "types")
        .containsEntry("workflows", List.of(prCi.get("name")))
        .containsEntry("types", List.of("completed"));
    assertThat(asMap(workflow.get("jobs"))).containsOnlyKeys("notify");
    assertThat(job())
        .as("No event or conclusion filter may omit manual, failed or cancelled CI runs")
        .doesNotContainKey("if");
  }

  @Test
  void privilegedNotificationUsesNoRepositoryCodeArtifactsOrTokenPermissions() {
    assertThat(asMap(workflow.get("permissions"))).isEmpty();
    assertThat(workflow).doesNotContainKeys("env", "defaults");
    assertThat(job()).doesNotContainKeys("permissions", "env", "uses", "needs", "defaults");
    Map<Object, Object> mail = mailStep();
    assertThat(mail)
        .containsEntry("uses", "dawidd6/action-send-mail@4226df7daafa6fc901a43789c49bf7ab309066e7")
        .doesNotContainKeys("run", "env", "if");
    assertThat(asMap(mail.get("with"))).doesNotContainKeys("attachments", "html_body");
  }

  @Test
  void sendsTheCompletedRunResultToTheApprovedRecipientWithoutBlockingCi() {
    Map<Object, Object> mail = mailStep();
    assertThat(mail).containsEntry("continue-on-error", true);
    Map<Object, Object> inputs = asMap(mail.get("with"));
    assertThat(inputs)
        .containsEntry("to", "devsong42@gmail.com")
        .containsEntry("username", "${{ secrets.MAIL_USERNAME }}")
        .containsEntry("password", "${{ secrets.MAIL_PASSWORD }}")
        .doesNotContainKeys("cc", "bcc");
    assertThat(String.valueOf(inputs.get("subject")))
        .contains("PR CI 결과", "${{ github.event.workflow_run.conclusion }}");
    assertThat(String.valueOf(inputs.get("body")))
        .contains(
            "${{ github.event.workflow_run.conclusion }}",
            "${{ github.event.workflow_run.head_branch }}",
            "${{ github.event.workflow_run.head_sha }}",
            "${{ github.event.workflow_run.run_attempt }}",
            "${{ github.event.workflow_run.html_url }}");
  }

  private Map<Object, Object> job() {
    return asMap(asMap(workflow.get("jobs")).get("notify"));
  }

  private Map<Object, Object> mailStep() {
    assertThat(job().get("steps")).isInstanceOf(List.class);
    List<?> steps = (List<?>) job().get("steps");
    assertThat(steps).as("Only the pinned mail action may execute").hasSize(1);
    return asMap(steps.get(0));
  }

  @SuppressWarnings("unchecked")
  private Map<Object, Object> asMap(Object value) {
    assertThat(value).isInstanceOf(Map.class);
    return (Map<Object, Object>) value;
  }
}
