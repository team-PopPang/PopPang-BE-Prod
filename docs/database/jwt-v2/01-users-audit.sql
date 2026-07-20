-- JWT v2 users 사전 점검 (읽기 전용)
-- 모든 결과를 보관하고 이상 건수가 0인지 승인받기 전에는 02/03 SQL을 실행하지 않는다.
--
-- 2026-07-17 사용자 제공 로컬 복원 DB 후속 검증:
-- users 303건에 02-users-expand.sql의 backfill/default 변경을 실행하고 COMMIT했다.
-- signup_status NULL 0건, 활성 COMPLETED 182건, 활성 PENDING 1건, 탈퇴 PENDING 120건이며
-- column은 enum('COMPLETED','PENDING') / nullable / default PENDING으로 확인됐다.
-- 로컬 MySQL은 9.2.0, DB는 poppang_restore_test_20260716으로 확인됐다.
-- 운영 MySQL 8.0.43과의 버전 차이는 2026-07-19 사용자 결정으로 DB-E1의 재연습 차단
-- 항목에서 제외했다. 이 기록은 로컬 복원 DB에만 해당하며 운영 DB 적용을 증명하지 않는다.
--
-- 2026-07-20 사용자 제공 운영 DB 수동 검증:
-- poppang_prod_db users 307건에 nickname 기준 backfill과 default PENDING 변경을 적용했다.
-- signup_status NULL 0건, PENDING 122건, COMPLETED 185건, nickname 기준 불일치 0건이며
-- column은 enum('COMPLETED','PENDING') / nullable / default PENDING으로 확인됐다.
-- 03-users-contract.sql은 실행하지 않았다. DDL 실행 당시 metadata lock 대기와 트래픽 영향은
-- 별도 계측 증거가 없어 확인되지 않은 항목으로 유지한다.

SELECT VERSION() AS mysql_version, DATABASE() AS database_name;

SELECT table_rows, data_length, index_length
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'users';

SELECT column_name, column_type, is_nullable, column_default, collation_name
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
ORDER BY ordinal_position;

-- expand 이후에는 null/enum 범위를 함께 확인한다.
-- COLUMN_DEFAULT와 null 건수는 이 결과를 직접 보관하기 전에는 확인 완료로 간주하지 않는다.
SELECT
  SUM(signup_status IS NULL) AS signup_status_null_count,
  SUM(signup_status IS NOT NULL AND signup_status NOT IN ('PENDING', 'COMPLETED'))
    AS signup_status_invalid_count,
  SUM(signup_status = 'PENDING') AS pending_count,
  SUM(signup_status = 'COMPLETED') AS completed_count
FROM users;

-- 실제 저장된 삭제 여부/SignupStatus 조합. nickname 기반 예상 분류와 별도로 확인한다.
SELECT is_deleted, signup_status, COUNT(*) AS user_count
FROM users
GROUP BY is_deleted, signup_status
ORDER BY is_deleted, signup_status;

-- 실제 uid unique index 이름과 현재 uuid index 존재 여부를 이 결과에서 확인한다.
SELECT index_name, non_unique, seq_in_index, column_name, index_type
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'users'
ORDER BY index_name, seq_in_index;

-- null, 공백, enum 범위, 삭제 상태 이상 건수
SELECT
  COUNT(*) AS total_count,
  SUM(uuid IS NULL) AS uuid_null_count,
  SUM(uuid IS NOT NULL AND TRIM(uuid) = '') AS uuid_blank_count,
  SUM(uid IS NULL) AS uid_null_count,
  SUM(uid IS NOT NULL AND TRIM(uid) = '') AS uid_blank_count,
  SUM(provider IS NULL) AS provider_null_count,
  SUM(provider IS NOT NULL AND provider NOT IN ('KAKAO', 'GOOGLE', 'APPLE'))
    AS provider_invalid_count,
  SUM(role IS NULL) AS role_null_count,
  SUM(role IS NOT NULL AND role NOT IN ('MEMBER', 'ADMIN')) AS role_invalid_count,
  SUM(is_deleted IS NULL) AS deleted_null_count,
  SUM(is_deleted IS NOT NULL AND is_deleted NOT IN (0, 1)) AS deleted_invalid_count,
  SUM(nickname IS NOT NULL AND TRIM(nickname) = '') AS nickname_blank_count
FROM users;

-- JWT sub 후보인 uuid 중복. 결과가 없어야 한다.
SELECT uuid, COUNT(*) AS duplicate_count
FROM users
WHERE uuid IS NOT NULL
GROUP BY uuid
HAVING COUNT(*) > 1;

-- 소셜 identity인 (provider, uid) 중복. 결과가 없어야 한다.
SELECT provider, uid, COUNT(*) AS duplicate_count
FROM users
WHERE provider IS NOT NULL
  AND uid IS NOT NULL
GROUP BY provider, uid
HAVING COUNT(*) > 1;

-- nickname 기반 SignupStatus 예상 분류 건수. null만 PENDING 후보로 본다.
SELECT
  CASE WHEN nickname IS NULL THEN 'PENDING' ELSE 'COMPLETED' END AS expected_signup_status,
  COUNT(*) AS user_count
FROM users
GROUP BY CASE WHEN nickname IS NULL THEN 'PENDING' ELSE 'COMPLETED' END;

-- 삭제 여부와 예상 SignupStatus 조합을 검토한다. 삭제 사용자를 자동 복구하거나 병합하지 않는다.
SELECT
  is_deleted,
  CASE WHEN nickname IS NULL THEN 'PENDING' ELSE 'COMPLETED' END AS expected_signup_status,
  COUNT(*) AS user_count
FROM users
GROUP BY is_deleted, CASE WHEN nickname IS NULL THEN 'PENDING' ELSE 'COMPLETED' END
ORDER BY is_deleted, expected_signup_status;
