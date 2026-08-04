package com.poppang.be.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class V1ToV2MigrationMatrixContractTest {

  private static final String V1_INVENTORY = "contracts/v1-endpoints.txt";
  private static final String V2_INVENTORY = "contracts/v2-endpoints.txt";
  private static final String MIGRATION_MATRIX = "contracts/v1-v2-migration-matrix.txt";
  private static final Path DESIGN_DOCUMENT =
      Path.of("docs/superpowers/specs/2026-07-15-jwt-v2-migration-design.md");
  private static final Set<String> APPROVED_DELETIONS =
      Set.of("POST /api/v1/auth/refresh", "POST /api/v1/auth/token/test");

  @Test
  void matrixCoversEveryV1EndpointExactlyOnceAndReferencesOnlyRealV2Endpoints() throws Exception {
    List<String> v1Inventory = readLines(V1_INVENTORY);
    Set<String> v2Inventory = Set.copyOf(readLines(V2_INVENTORY));
    List<MatrixRow> matrix = readLines(MIGRATION_MATRIX).stream().map(this::parseRow).toList();

    Set<String> expectedV1 =
        v1Inventory.stream().map(this::v1InventoryKey).collect(Collectors.toSet());
    List<String> actualV1 = matrix.stream().map(MatrixRow::v1Key).toList();

    assertThat(v1Inventory).hasSize(79);
    assertThat(matrix).hasSize(79);
    assertThat(actualV1).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expectedV1);
    assertThat(matrix).filteredOn(row -> row.v1Status().equals("KEEP")).hasSize(77);
    assertThat(matrix).filteredOn(row -> row.v1Status().equals("DELETE_APPROVED")).hasSize(2);

    assertThat(matrix).filteredOn(row -> row.treatment().equals("V2_TWIN")).hasSize(70);
    assertThat(matrix).filteredOn(row -> row.treatment().equals("REPLACED_FLOW")).hasSize(2);
    assertThat(matrix).filteredOn(row -> row.treatment().equals("V1_ONLY_KEEP")).hasSize(5);
    assertThat(matrix).filteredOn(row -> row.treatment().equals("DELETE_APPROVED")).hasSize(2);

    assertThat(
            matrix.stream().filter(MatrixRow::referencesV2).map(MatrixRow::v2InventoryKey).toList())
        .allMatch(v2Inventory::contains);

    Set<String> referencedV2 =
        matrix.stream()
            .filter(MatrixRow::referencesV2)
            .map(MatrixRow::v2InventoryKey)
            .collect(Collectors.toSet());
    assertThat(v2Inventory).hasSize(72);
    assertThat(v2Inventory).containsAll(referencedV2);
    assertThat(v2Inventory)
        .filteredOn(endpoint -> !referencedV2.contains(endpoint))
        .containsExactly("ACCESS|POST /api/v2/auth/logout");
  }

  @Test
  void matrixKeepsUnverifiedRolloutAndDeletionStateFailClosed() throws Exception {
    List<MatrixRow> matrix = readLines(MIGRATION_MATRIX).stream().map(this::parseRow).toList();

    assertThat(matrix)
        .allMatch(row -> row.beTest().equals("COMPLETE"))
        .allMatch(row -> row.productionSmoke().equals("UNVERIFIED"))
        .allMatch(row -> row.recentV1Traffic().equals("NO_ROUTE_DATA"));

    assertThat(matrix)
        .filteredOn(row -> row.v1Status().equals("KEEP"))
        .allMatch(row -> row.actualTransition().equals("NOT_STARTED"))
        .allMatch(row -> row.deletionAllowed().equals("NO"));
    assertThat(matrix)
        .filteredOn(row -> row.v1Status().equals("DELETE_APPROVED"))
        .allMatch(row -> row.actualTransition().equals("NOT_APPLICABLE"))
        .allMatch(row -> row.deletionAllowed().equals("APPROVED"));
  }

  @Test
  void specialLegacyFlowsKeepTheirApprovedMeaning() throws Exception {
    List<MatrixRow> matrix = readLines(MIGRATION_MATRIX).stream().map(this::parseRow).toList();
    Set<String> deleteApproved =
        matrix.stream()
            .filter(row -> row.treatment().equals("DELETE_APPROVED"))
            .map(MatrixRow::operation)
            .collect(Collectors.toSet());

    assertThat(deleteApproved).isEqualTo(APPROVED_DELETIONS);
    assertThat(row(matrix, "GET /api/v1/auth/apple/login").treatment()).isEqualTo("V1_ONLY_KEEP");
    assertThat(row(matrix, "GET /api/v1/auth/google/login").treatment()).isEqualTo("V1_ONLY_KEEP");
    assertThat(row(matrix, "GET /api/v1/auth/kakao/login").treatment()).isEqualTo("V1_ONLY_KEEP");
    assertThat(row(matrix, "POST /api/v1/auth/autoLogin").treatment()).isEqualTo("REPLACED_FLOW");
    assertThat(row(matrix, "DELETE /api/v1/user/{userUuid}/hard-delete").treatment())
        .isEqualTo("V1_ONLY_KEEP");
    assertThat(row(matrix, "PATCH /api/v1/user/{userUuid}/resotre").treatment())
        .isEqualTo("V1_ONLY_KEEP");
    assertThat(row(matrix, "GET /api/v1/user/{userUuid}/fcm-token/duplicate-check").treatment())
        .isEqualTo("REPLACED_FLOW");

    MatrixRow workerAlert = row(matrix, "POST /api/v1/users/{userUuid}/alert");
    assertThat(workerAlert.v2InventoryKey())
        .isEqualTo("WORKER|POST /api/v2/internal/users/{userUuid}/alert");
    assertThat(workerAlert.actor()).isEqualTo("WORKER");
    assertThat(workerAlert.target()).isEqualTo("RECIPIENT_USER_UUID_AND_POPUP_UUID");
  }

  @Test
  void designDocumentContainsTheSameSeventyNineEndpointRowsAsTheContractResource()
      throws Exception {
    List<MatrixRow> matrix = readLines(MIGRATION_MATRIX).stream().map(this::parseRow).toList();
    List<String> documentLines = Files.readAllLines(DESIGN_DOCUMENT, StandardCharsets.UTF_8);
    int headerIndex =
        documentLines.indexOf(
            "| v1 method/path | 처리 | v2 method/path | actor | target | 인증 | DTO 변경 | 소비자 | BE test | 실제 전환 | 운영 smoke | 최근 v1 호출 | 삭제 가능 |");
    assertThat(headerIndex).isGreaterThanOrEqualTo(0);

    List<String> documentRows =
        documentLines.subList(headerIndex + 2, documentLines.size()).stream()
            .takeWhile(line -> line.startsWith("| `"))
            .map(this::documentProjection)
            .toList();
    List<String> contractRows = matrix.stream().map(MatrixRow::documentProjection).toList();

    assertThat(documentRows)
        .hasSize(79)
        .doesNotHaveDuplicates()
        .containsExactlyElementsOf(contractRows);
  }

  private MatrixRow row(List<MatrixRow> rows, String operation) {
    return rows.stream().filter(row -> row.operation().equals(operation)).findFirst().orElseThrow();
  }

  private String v1InventoryKey(String line) {
    String[] fields = line.split("\\|", -1);
    return String.join("|", fields[0], fields[1], fields[2]);
  }

  private MatrixRow parseRow(String line) {
    String[] fields = line.split("\\|", -1);
    assertThat(fields).as("matrix row field count: %s", line).hasSize(16);
    return new MatrixRow(
        fields[0],
        fields[1],
        fields[2],
        fields[3],
        fields[4],
        fields[5],
        fields[6],
        fields[7],
        fields[8],
        fields[9],
        fields[10],
        fields[11],
        fields[12],
        fields[13],
        fields[14],
        fields[15]);
  }

  private String documentProjection(String line) {
    String[] fields = line.split("\\|", -1);
    assertThat(fields).as("document matrix row field count: %s", line).hasSize(15);
    return java.util.stream.IntStream.rangeClosed(1, 13)
        .mapToObj(index -> fields[index].trim().replace("`", ""))
        .collect(Collectors.joining("|"));
  }

  private List<String> readLines(String resourcePath) throws Exception {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
      return reader
          .lines()
          .map(String::trim)
          .filter(line -> !line.isBlank())
          .filter(line -> !line.startsWith("#"))
          .toList();
    }
  }

  private record MatrixRow(
      String v1Status,
      String v1Method,
      String v1Path,
      String treatment,
      String v2Security,
      String v2Method,
      String v2Path,
      String actor,
      String target,
      String dtoChange,
      String consumer,
      String beTest,
      String actualTransition,
      String productionSmoke,
      String recentV1Traffic,
      String deletionAllowed) {

    String v1Key() {
      return String.join("|", v1Status, v1Method, v1Path);
    }

    String operation() {
      return v1Method + " " + v1Path;
    }

    boolean referencesV2() {
      return !v2Method.equals("-") && !v2Path.equals("-");
    }

    String v2InventoryKey() {
      return v2Security + "|" + v2Method + " " + v2Path;
    }

    String documentProjection() {
      String v2Operation = referencesV2() ? v2Method + " " + v2Path : "-";
      return String.join(
          "|",
          operation(),
          treatment,
          v2Operation,
          actor,
          target,
          v2Security,
          dtoChange,
          consumer,
          beTest,
          actualTransition,
          productionSmoke,
          recentV1Traffic,
          deletionAllowed);
    }
  }
}
