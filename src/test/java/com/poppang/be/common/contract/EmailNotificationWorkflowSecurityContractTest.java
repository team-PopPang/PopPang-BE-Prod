package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

class EmailNotificationWorkflowSecurityContractTest {

  private static final Path WORKFLOW_PATH = Path.of(".github/workflows/email-notify.yml");
  private static final Pattern DIRECT_GITHUB_EXPRESSION =
      Pattern.compile("\\$\\{\\{\\s*(?:github\\.|toJson\\(github\\.)");

  @TempDir Path tempDir;

  private Map<Object, Object> workflow;

  @BeforeEach
  void loadWorkflow() throws IOException {
    workflow =
        asMap(new Yaml().load(Files.readString(WORKFLOW_PATH)), "Workflow must be valid YAML");
  }

  @Test
  void preservesApprovedTriggersEmailSettingsAndReadOnlyPermission() {
    Map<Object, Object> triggers = asMap(rootValue("on"), "Workflow must declare triggers");
    assertThat(triggers.keySet())
        .containsExactlyInAnyOrder(
            "issues",
            "pull_request",
            "issue_comment",
            "pull_request_review_comment",
            "pull_request_review");
    assertThat(eventTypes(triggers, "issues")).containsExactly("opened");
    assertThat(eventTypes(triggers, "pull_request")).containsExactly("opened", "closed");
    assertThat(eventTypes(triggers, "issue_comment")).containsExactly("created");
    assertThat(eventTypes(triggers, "pull_request_review_comment")).containsExactly("created");
    assertThat(eventTypes(triggers, "pull_request_review")).containsExactly("submitted");

    assertThat(asMap(rootValue("permissions"), "Workflow permissions must be explicit"))
        .containsOnly(entry("contents", "read"));

    Map<Object, Object> sendEmail = usesStep("dawidd6/action-send-mail@v3");
    Map<Object, Object> inputs = asMap(sendEmail.get("with"), "Email inputs must be configured");
    assertThat(inputs)
        .containsEntry("username", "${{ secrets.MAIL_USERNAME }}")
        .containsEntry("password", "${{ secrets.MAIL_PASSWORD }}")
        .containsEntry("to", "indextrown@gmail.com");
  }

  @Test
  void passesEveryUntrustedGithubValueThroughStepEnvironmentBeforeUsingShell() {
    Map<Object, Object> buildBody = step("Set Custom Event Name and Build Body");
    Map<Object, Object> environment =
        asMap(buildBody.get("env"), "Event values must enter the shell through step env");

    assertThat(environment)
        .containsEntry("EVENT_NAME", "${{ github.event_name }}")
        .containsEntry("EVENT_ACTION", "${{ github.event.action }}")
        .containsEntry("PULL_REQUEST_MERGED", "${{ github.event.pull_request.merged }}")
        .containsEntry("PULL_REQUEST_TITLE", "${{ github.event.pull_request.title }}")
        .containsEntry("PULL_REQUEST_URL", "${{ github.event.pull_request.html_url }}")
        .containsEntry("ISSUE_PULL_REQUEST_JSON", "${{ toJson(github.event.issue.pull_request) }}")
        .containsEntry("ISSUE_TITLE", "${{ github.event.issue.title }}")
        .containsEntry("ISSUE_URL", "${{ github.event.issue.html_url }}")
        .containsEntry("COMMENT_BODY", "${{ github.event.comment.body }}")
        .containsEntry("COMMENT_URL", "${{ github.event.comment.html_url }}")
        .containsEntry("COMMENT_PATH", "${{ github.event.comment.path }}")
        .containsEntry("COMMENT_LINE", "${{ github.event.comment.line }}")
        .containsEntry("REVIEW_BODY", "${{ github.event.review.body }}")
        .containsEntry("REVIEW_URL", "${{ github.event.review.html_url }}")
        .containsEntry("REPOSITORY", "${{ github.repository }}")
        .containsEntry("ACTOR", "${{ github.actor }}");

    for (Map<Object, Object> runStep : runSteps()) {
      String shell = String.valueOf(runStep.get("run"));
      assertThat(DIRECT_GITHUB_EXPRESSION.matcher(shell).find())
          .as("GitHub event expressions must never be interpolated into run blocks")
          .isFalse();
      assertThat(shell)
          .as("Shell must not log all environment variables or enable command tracing")
          .doesNotContain(
              "set -x", "printenv", "env |", "cat \"$GITHUB_OUTPUT\"", "eval ", "bash -c", "sh -c");
    }
  }

  @Test
  void maliciousReviewBodyCannotExecuteCommandOrCreateSentinel() throws Exception {
    Path sentinel = tempDir.resolve("review-body-command-was-executed");
    String payload = "before $(touch " + sentinel + ") after";

    ShellResult result = runBodyStep("pull_request_review", payload);

    assertThat(result.exitCode()).as(result.log()).isZero();
    assertThat(sentinel)
        .as("Review body command substitution must remain inert text")
        .doesNotExist();
    assertThat(readMultilineOutput(result.githubOutput(), "body")).contains(payload);
    assertThat(result.log()).doesNotContain(payload);
  }

  @Test
  void maliciousTitlesUrlsPathsLinesActorAndRepositoryRemainInert() throws Exception {
    Path sentinel = tempDir.resolve("event-metadata-command-was-executed");
    String payload = "$(touch " + sentinel + ")";

    for (String eventName :
        List.of(
            "pull_request",
            "issues",
            "issue_comment",
            "pull_request_review_comment",
            "pull_request_review")) {
      Map<String, String> values = eventValues(eventName, "body " + payload);
      values.put("PULL_REQUEST_TITLE", "PR title " + payload);
      values.put("PULL_REQUEST_URL", "https://github.com/pull/5/" + payload);
      values.put("ISSUE_PULL_REQUEST_JSON", "{\"url\":\"" + payload + "\"}");
      values.put("ISSUE_TITLE", "Issue title " + payload);
      values.put("ISSUE_URL", "https://github.com/issues/5/" + payload);
      values.put("COMMENT_URL", "https://github.com/comment/5/" + payload);
      values.put("COMMENT_PATH", "src/example/" + payload + ".java");
      values.put("COMMENT_LINE", "42 " + payload);
      values.put("REVIEW_URL", "https://github.com/review/5/" + payload);
      values.put("REPOSITORY", "owner/repository-" + payload);
      values.put("ACTOR", "actor-" + payload);

      ShellResult result = runBodyStep(eventName, values);

      assertThat(result.exitCode()).as(eventName + ": " + result.log()).isZero();
      assertThat(readMultilineOutput(result.githubOutput(), "body")).contains(payload);
      assertThat(result.log()).doesNotContain(payload);
    }

    assertThat(sentinel)
        .as("No event metadata may be re-evaluated as a shell command")
        .doesNotExist();
  }

  @Test
  void complexReviewCommentCannotBreakMultilineOutputProtocol() throws Exception {
    String workflowShell = String.valueOf(step("Set Custom Event Name and Build Body").get("run"));
    assertThat(DIRECT_GITHUB_EXPRESSION.matcher(workflowShell).find())
        .as("Complex payload dry run is safe only after direct interpolation is removed")
        .isFalse();

    String payload =
        "backtick: `not-a-command`\n"
            + "substitution: $(not-a-command)\n"
            + "quotes: \"double\" and 'single'\n"
            + "```bash\n"
            + "echo should-not-run\n"
            + "```\n"
            + "EOF\n"
            + "tail";

    ShellResult result = runBodyStep("pull_request_review_comment", payload);

    assertThat(result.exitCode()).as(result.log()).isZero();
    String output = Files.readString(result.githubOutput());
    assertThat(output).doesNotContain("body<<EOF\n");
    assertThat(readMultilineOutput(result.githubOutput(), "body")).contains(payload);
    assertThat(result.log()).doesNotContain(payload, "should-not-run");
  }

  private ShellResult runBodyStep(String eventName, String body) throws Exception {
    return runBodyStep(eventName, eventValues(eventName, body));
  }

  private ShellResult runBodyStep(String eventName, Map<String, String> values) throws Exception {
    Map<Object, Object> buildBody = step("Set Custom Event Name and Build Body");
    String script = String.valueOf(buildBody.get("run"));
    script = materializeLegacyExpressions(script, values);
    assertThat(script)
        .as("Test harness must resolve every legacy expression")
        .doesNotContain("${{");

    Path githubOutput = tempDir.resolve(eventName + "-github-output.txt");
    ProcessBuilder processBuilder =
        new ProcessBuilder("bash", "-c", script).redirectErrorStream(true);
    processBuilder.directory(tempDir.toFile());
    processBuilder.environment().clear();
    processBuilder.environment().put("PATH", "/usr/bin:/bin");
    processBuilder.environment().put("GITHUB_OUTPUT", githubOutput.toString());
    processBuilder.environment().put("GITHUB_RUN_ID", "29646436753");
    processBuilder.environment().put("GITHUB_RUN_ATTEMPT", "1");
    processBuilder.environment().putAll(values);

    Process process = processBuilder.start();
    boolean finished = process.waitFor(5, TimeUnit.SECONDS);
    if (!finished) {
      process.descendants().forEach(descendant -> descendant.destroyForcibly());
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS);
      throw new AssertionError("Workflow shell did not finish within five seconds");
    }
    String log = new String(process.getInputStream().readAllBytes());
    return new ShellResult(process.exitValue(), log, githubOutput);
  }

  private Map<String, String> eventValues(String eventName, String body) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("EVENT_NAME", eventName);
    values.put(
        "EVENT_ACTION",
        switch (eventName) {
          case "pull_request_review" -> "submitted";
          case "pull_request" -> "opened";
          default -> "created";
        });
    values.put("PULL_REQUEST_MERGED", "false");
    values.put("PULL_REQUEST_TITLE", "보안 리뷰 제목");
    values.put("PULL_REQUEST_URL", "https://github.com/team-PopPang/PopPang-BE-Prod/pull/5");
    values.put("ISSUE_PULL_REQUEST_JSON", "{\"url\":\"pull-request\"}");
    values.put("ISSUE_TITLE", "이슈 제목");
    values.put("ISSUE_URL", "https://github.com/team-PopPang/PopPang-BE-Prod/issues/5");
    values.put("COMMENT_BODY", body);
    values.put("COMMENT_URL", "https://github.com/comment/5");
    values.put("COMMENT_PATH", "src/example/`literal`.java");
    values.put("COMMENT_LINE", "42");
    values.put("REVIEW_BODY", body);
    values.put("REVIEW_URL", "https://github.com/review/5");
    values.put("REPOSITORY", "team-PopPang/PopPang-BE-Prod");
    values.put("ACTOR", "review-bot");
    return values;
  }

  private String materializeLegacyExpressions(String script, Map<String, String> values) {
    Map<String, String> legacyExpressions = new LinkedHashMap<>();
    legacyExpressions.put("${{ github.event_name }}", values.get("EVENT_NAME"));
    legacyExpressions.put("${{ github.event.action }}", values.get("EVENT_ACTION"));
    legacyExpressions.put("${{ github.event.action || 'N/A' }}", values.get("EVENT_ACTION"));
    legacyExpressions.put(
        "${{ github.event.pull_request.merged }}", values.get("PULL_REQUEST_MERGED"));
    legacyExpressions.put(
        "${{ github.event.pull_request.title }}", values.get("PULL_REQUEST_TITLE"));
    legacyExpressions.put(
        "${{ github.event.pull_request.html_url }}", values.get("PULL_REQUEST_URL"));
    legacyExpressions.put(
        "${{ toJson(github.event.issue.pull_request) }}", values.get("ISSUE_PULL_REQUEST_JSON"));
    legacyExpressions.put("${{ github.event.issue.title }}", values.get("ISSUE_TITLE"));
    legacyExpressions.put("${{ github.event.issue.html_url }}", values.get("ISSUE_URL"));
    legacyExpressions.put("${{ github.event.comment.body }}", values.get("COMMENT_BODY"));
    legacyExpressions.put("${{ github.event.comment.html_url }}", values.get("COMMENT_URL"));
    legacyExpressions.put("${{ github.event.comment.path }}", values.get("COMMENT_PATH"));
    legacyExpressions.put("${{ github.event.comment.line }}", values.get("COMMENT_LINE"));
    legacyExpressions.put("${{ github.event.review.body }}", values.get("REVIEW_BODY"));
    legacyExpressions.put("${{ github.event.review.html_url }}", values.get("REVIEW_URL"));
    legacyExpressions.put("${{ github.repository }}", values.get("REPOSITORY"));
    legacyExpressions.put("${{ github.actor }}", values.get("ACTOR"));

    String result = script;
    for (Map.Entry<String, String> expression : legacyExpressions.entrySet()) {
      result = result.replace(expression.getKey(), expression.getValue());
    }
    return result;
  }

  private String readMultilineOutput(Path githubOutput, String outputName) throws IOException {
    assertThat(githubOutput).exists();
    String output = Files.readString(githubOutput);
    Matcher declaration =
        Pattern.compile("(?m)^" + Pattern.quote(outputName) + "<<([^\\r\\n]+)\\R").matcher(output);
    assertThat(declaration.find()).as("Missing multiline output declaration").isTrue();
    String delimiter = declaration.group(1);
    assertThat(delimiter)
        .as("A fixed EOF delimiter is unsafe for event content")
        .isNotEqualTo("EOF");

    Matcher terminator =
        Pattern.compile("(?m)^" + Pattern.quote(delimiter) + "(?:\\R|$)").matcher(output);
    terminator.region(declaration.end(), output.length());
    assertThat(terminator.find()).as("Missing multiline output terminator").isTrue();
    return output.substring(declaration.end(), terminator.start());
  }

  private List<Object> eventTypes(Map<Object, Object> triggers, String eventName) {
    return asList(
        asMap(triggers.get(eventName), eventName + " trigger must be configured").get("types"),
        eventName + " event types");
  }

  private List<Map<Object, Object>> runSteps() {
    return steps().stream()
        .map(value -> asMap(value, "Every step must be a mapping"))
        .filter(value -> value.containsKey("run"))
        .toList();
  }

  private Map<Object, Object> usesStep(String action) {
    return steps().stream()
        .map(value -> asMap(value, "Every step must be a mapping"))
        .filter(value -> action.equals(value.get("uses")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing action step: " + action));
  }

  private Map<Object, Object> step(String name) {
    return steps().stream()
        .map(value -> asMap(value, "Every step must be a mapping"))
        .filter(value -> name.equals(value.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing step: " + name));
  }

  private List<Object> steps() {
    Map<Object, Object> jobs = asMap(rootValue("jobs"), "Workflow must declare jobs");
    Map<Object, Object> job =
        asMap(jobs.get("notify_by_email"), "Email notification job must be configured");
    return asList(job.get("steps"), "Email notification steps");
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

  private record ShellResult(int exitCode, String log, Path githubOutput) {}
}
