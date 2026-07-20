package com.poppang.be.domain.users.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UsersDatabaseMigrationContractTest {

  private static final Path MIGRATION_DIR = Path.of("docs/database/jwt-v2");

  @Test
  void auditCoversIdentitySignupAndDeletionRisks() throws IOException {
    String sql = read("01-users-audit.sql");

    assertThat(sql)
        .contains("SELECT VERSION()")
        .contains("column_default")
        .contains("information_schema.statistics")
        .contains("uuid IS NULL")
        .contains("GROUP BY uuid")
        .contains("provider IS NULL")
        .contains("role IS NULL")
        .contains("GROUP BY provider, uid")
        .contains("nickname IS NULL")
        .contains("is_deleted IS NULL")
        .contains("GROUP BY is_deleted, signup_status");
  }

  @Test
  void expandIsNullableAndContractAppliesStrictConstraintsLater() throws IOException {
    String expand = read("02-users-expand.sql");
    String contract = read("03-users-contract.sql");

    assertThat(expand)
        .contains("signup_status ENUM('COMPLETED', 'PENDING') NULL")
        .contains("uq_users_uuid")
        .contains("ddl-auto: validate")
        .doesNotContain("ADD UNIQUE INDEX uk_users_uuid")
        .doesNotContain("signup_status ENUM('COMPLETED', 'PENDING') NOT NULL");
    assertThat(contract)
        .contains("ADD UNIQUE INDEX uq_users_provider_uid (provider, uid)")
        .contains("DROP INDEX uq_users_uid")
        .contains("MODIFY COLUMN signup_status ENUM('COMPLETED', 'PENDING') NOT NULL");
    assertThat(Files.readString(MIGRATION_DIR.resolve("ROLLBACK.md")))
        .contains("ddl-auto: validate")
        .contains("백업")
        .contains("마지막 되돌림 지점");
  }

  @Test
  void migrationNotesRecordLocalAndOperationalEvidenceAndKeepUnverifiedGates() throws IOException {
    String rollback = Files.readString(MIGRATION_DIR.resolve("ROLLBACK.md"));
    String checklist =
        Files.readString(Path.of("docs/superpowers/plans/2026-07-15-jwt-v2-migration.md"));
    String design =
        Files.readString(Path.of("docs/superpowers/specs/2026-07-15-jwt-v2-migration-design.md"));
    String normalizedRollback = rollback.replaceAll("\\s+", " ");
    String normalizedChecklist = checklist.replaceAll("\\s+", " ");
    String normalizedDesign = design.replaceAll("\\s+", " ");

    assertThat(rollback)
        .contains("사용자 제공 수동 검증 증거")
        .contains("users 총 303건")
        .contains("DB 트랜잭션 COMMIT")
        .contains("signup_status NULL 건수: 0")
        .contains("활성 COMPLETED 182건")
        .contains("활성 PENDING 1건")
        .contains("탈퇴 PENDING 120건")
        .contains("COLUMN_TYPE: enum('COMPLETED','PENDING')")
        .contains("IS_NULLABLE: YES")
        .contains("COLUMN_DEFAULT: PENDING")
        .contains("로컬 MySQL은 9.2.0")
        .contains("운영 MySQL 8.0.43")
        .contains("poppang_prod_db")
        .contains("users 307건")
        .contains("PENDING: 122")
        .contains("COMPLETED: 185")
        .contains("불일치는 0건")
        .contains("구·신규 binary")
        .contains("metadata lock")
        .contains("운영 DB 적용");
    assertThat(normalizedRollback).contains("동일-major 재연습 차단 항목에서 제외");
    assertThat(normalizedChecklist)
        .contains("로컬 DB migration 연습, 운영 DB-E1 expand 적용은 완료")
        .contains("MySQL 9.2.0")
        .contains("버전 차이는 사용자 결정으로 수용")
        .contains("users 307건")
        .contains("PENDING 122건")
        .contains("COMPLETED 185건")
        .contains("production merge 전 코드 검증");
    assertThat(normalizedDesign)
        .contains("DB-E1에 한해서는 로컬 MySQL 9.2.0")
        .contains("이후 DB wave의 동일-major 검증을 생략하는 승인으로 확대하지 않는다");
  }

  private String read(String filename) throws IOException {
    return Files.readString(MIGRATION_DIR.resolve(filename));
  }
}
