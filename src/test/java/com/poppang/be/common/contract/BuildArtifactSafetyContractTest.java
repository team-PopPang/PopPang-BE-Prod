package com.poppang.be.common.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildArtifactSafetyContractTest {

  private static final Path SCRIPT = Path.of("scripts/ci/verify-build-artifacts.sh");
  private static final String COMMON_TEMPLATE = "spring:\n  profiles:\n    default: prod\n";
  private static final String PROD_TEMPLATE = "jwt:\n  secret: ${JWT_SECRET}\n";
  private static final String FAKE_SECRET = "FAKE-REAL-SECRET-VALUE-0001";
  // 소스에 키 모양 문자열이 그대로 남지 않도록 실행 중에 조합한다.
  private static final String FAKE_AWS_KEY_ID = "AKIA" + "ABCDEFGHIJKLMNOP";

  @TempDir Path tempDir;

  private Path repoRoot;

  @BeforeEach
  void writeTemplates() throws IOException {
    repoRoot = tempDir.resolve("repo");
    Path resources = Files.createDirectories(repoRoot.resolve("src/main/resources"));
    Files.writeString(resources.resolve("application.yml"), COMMON_TEMPLATE);
    Files.writeString(resources.resolve("application-prod.yml"), PROD_TEMPLATE);
  }

  @Test
  void passesBootJarThatCarriesOnlyCommittedTemplates() throws Exception {
    Map<String, byte[]> entries = cleanEntries();
    entries.put("BOOT-INF/lib/third-party.jar", nestedJarWith("test/fixture.pem"));

    Result result = verifyJar(jar(entries), "worktree");

    assertThat(result.exitCode()).isZero();
    assertThat(result.output()).contains("artifact_check=passed mode=jar");
  }

  @Test
  void rejectsAppleKeyAndOtherKeyFilesWithoutPrintingNames() throws Exception {
    Map<String, byte[]> entries = cleanEntries();
    entries.put("BOOT-INF/classes/auth/AuthKey_FAKE1234.p8", bytes("key"));
    entries.put("BOOT-INF/classes/.env", bytes("A=B"));

    Result result = verifyJar(jar(entries), "worktree");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains("artifact_check=failed reason=forbidden_file_type count=2")
        .doesNotContain("AuthKey_FAKE1234");
  }

  @Test
  void rejectsPrivateProfileConfigFiles() throws Exception {
    Map<String, byte[]> entries = cleanEntries();
    entries.put("BOOT-INF/classes/application-local.yml", bytes("a: b\n"));
    entries.put("BOOT-INF/classes/application-dev.properties", bytes("a=b\n"));

    Result result = verifyJar(jar(entries), "worktree");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output()).contains("reason=unexpected_config_file count=2");
  }

  @Test
  void rejectsConfigThatDiffersFromCommittedTemplateWithoutPrintingValues() throws Exception {
    Map<String, byte[]> entries = cleanEntries();
    entries.put(
        "BOOT-INF/classes/application-prod.yml", bytes("jwt:\n  secret: " + FAKE_SECRET + "\n"));

    Result result = verifyJar(jar(entries), "worktree");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains("reason=config_not_committed_template name=application-prod.yml")
        .doesNotContain(FAKE_SECRET);
  }

  @Test
  void rejectsMissingTemplateAndSecretPatternsInResources() throws Exception {
    Map<String, byte[]> entries = cleanEntries();
    entries.remove("BOOT-INF/classes/application-prod.yml");
    entries.put(
        "BOOT-INF/classes/static/notes.txt",
        bytes("-----BEGIN PRIVATE KEY-----\n" + FAKE_SECRET + "\n-----END PRIVATE KEY-----\n"));
    entries.put("META-INF/leak.json", bytes("{\"key\":\"" + FAKE_AWS_KEY_ID + "\"}"));

    Result result = verifyJar(jar(entries), "worktree");

    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.output())
        .contains(
            "reason=config_template_missing name=application-prod.yml",
            "reason=secret_pattern count=2")
        .doesNotContain(FAKE_SECRET, FAKE_AWS_KEY_ID);
  }

  @Test
  void comparesWithGitHeadSoUncommittedTemplateEditsFail() throws Exception {
    git("init", "--quiet");
    git("add", "src/main/resources/application.yml", "src/main/resources/application-prod.yml");
    git("-c", "user.name=t", "-c", "user.email=t@example.invalid", "commit", "--quiet", "-m", "t");
    String edited = "jwt:\n  secret: " + FAKE_SECRET + "\n";
    Files.writeString(repoRoot.resolve("src/main/resources/application-prod.yml"), edited);
    Map<String, byte[]> entries = cleanEntries();
    entries.put("BOOT-INF/classes/application-prod.yml", bytes(edited));
    Path jar = jar(entries);

    assertThat(verifyJar(jar, "worktree").exitCode()).isZero();
    Result gitResult = verifyJar(jar, "git");
    assertThat(gitResult.exitCode()).isEqualTo(1);
    assertThat(gitResult.output())
        .contains("reason=config_not_committed_template name=application-prod.yml")
        .doesNotContain(FAKE_SECRET);
  }

  @Test
  void rejectsInvalidArgumentsAndMissingJar() throws Exception {
    assertThat(run(List.of("jar"), "worktree").output()).contains("reason=invalid_arguments");
    assertThat(run(List.of("jar", "a.jar", "b.jar"), "worktree").exitCode()).isEqualTo(1);
    assertThat(run(List.of("zip", "a.jar"), "worktree").output())
        .contains("reason=invalid_arguments");
    Result missing = run(List.of("jar", tempDir.resolve("none.jar").toString()), "worktree");
    assertThat(missing.exitCode()).isEqualTo(1);
    assertThat(missing.output()).contains("reason=jar_missing");
  }

  private Map<String, byte[]> cleanEntries() {
    Map<String, byte[]> entries = new LinkedHashMap<>();
    entries.put("META-INF/MANIFEST.MF", bytes("Manifest-Version: 1.0\n"));
    entries.put("BOOT-INF/classes/application.yml", bytes(COMMON_TEMPLATE));
    entries.put("BOOT-INF/classes/application-prod.yml", bytes(PROD_TEMPLATE));
    entries.put("BOOT-INF/classes/redis/refresh-rotate.lua", bytes("return 1\n"));
    entries.put("BOOT-INF/classes/com/poppang/be/App.class", new byte[] {(byte) 0xca, 0x00});
    return entries;
  }

  private Path jar(Map<String, byte[]> entries) throws IOException {
    Path jar = Files.createTempFile(tempDir, "app-", ".jar");
    Files.write(jar, zip(entries));
    return jar;
  }

  private static byte[] nestedJarWith(String name) throws IOException {
    return zip(Map.of(name, bytes("-----BEGIN PRIVATE KEY-----\nlibrary-fixture\n")));
  }

  private static byte[] zip(Map<String, byte[]> entries) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (ZipOutputStream output = new ZipOutputStream(buffer)) {
      for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
        output.putNextEntry(new ZipEntry(entry.getKey()));
        output.write(entry.getValue());
        output.closeEntry();
      }
    }
    return buffer.toByteArray();
  }

  private Result verifyJar(Path jar, String templateSource) throws Exception {
    return run(List.of("jar", jar.toString()), templateSource);
  }

  private Result run(List<String> arguments, String templateSource) throws Exception {
    assertThat(SCRIPT).as("The artifact safety checker must exist").exists();
    List<String> command = new ArrayList<>(List.of("bash", SCRIPT.toString()));
    command.addAll(arguments);
    ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
    processBuilder.environment().put("ARTIFACT_REPO_ROOT", repoRoot.toString());
    processBuilder.environment().put("ARTIFACT_TEMPLATE_SOURCE", templateSource);
    Process process = processBuilder.start();
    String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(process.waitFor(), output);
  }

  private void git(String... arguments) throws Exception {
    List<String> command = new ArrayList<>(List.of("git", "-C", repoRoot.toString()));
    command.addAll(List.of(arguments));
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    process.getInputStream().readAllBytes();
    assertThat(process.waitFor()).isZero();
  }

  private static byte[] bytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private record Result(int exitCode, String output) {}
}
