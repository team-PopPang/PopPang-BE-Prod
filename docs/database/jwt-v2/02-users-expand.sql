-- JWT v2 users expand 단계
-- 전제: 01-users-audit.sql 결과, backup, lock 계획이 승인되어야 한다.
-- 현재 운영 ddl-auto: validate다. 애플리케이션이 이 파일의 DDL/DML을 자동 실행하지 않는다.
-- 2026-07-17 사용자 제공 증거로 로컬 복원 DB에서 아래 backfill/default 변경과 COMMIT을 완료했다.
-- 2026-07-20 사용자 제공 증거로 운영 poppang_prod_db에도 아래 backfill/default 변경을 완료했다.
-- 두 환경 모두 03-users-contract.sql은 실행하지 않았다.
-- 다른 DB에 다시 실행하려면 대상·영향·롤백을 먼저 보고하고 별도 승인을 받는다.

-- 2026-07-16 현재 운영 상태
-- MySQL 8.0.43에서 격리되지 않은 context test가 아래 nullable column DDL을 이미 실행했다.
-- 운영에는 이 ADD COLUMN을 다시 실행하지 않는다.
-- ALTER TABLE users
--   ADD COLUMN signup_status ENUM('COMPLETED', 'PENDING') NULL;

-- 기존 uuid unique는 uq_users_uuid로 이미 존재한다. 새 uuid index를 중복 생성하지 않는다.
SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND index_name = 'uq_users_uuid'
ORDER BY seq_in_index;

-- 로컬 복원 DB에서는 사용자 승인 아래 실행 완료했다. 운영을 포함한 다른 대상은 별도 승인이 필요하다.
-- 감사에서 승인한 nickname 기준으로 null row만 분류하며 이상 row는 자동 병합·수정하지 않는다.
UPDATE users
SET signup_status = CASE
  WHEN nickname IS NULL THEN 'PENDING'
  ELSE 'COMPLETED'
END
WHERE signup_status IS NULL;

SELECT signup_status, COUNT(*) AS user_count
FROM users
GROUP BY signup_status;

-- 구 binary가 column을 생략해 insert하는 동안 신규 사용자는 PENDING이 되게 한다.
ALTER TABLE users
  ALTER COLUMN signup_status SET DEFAULT 'PENDING';

-- 로컬 복원 DB 실행 후 사용자 확인 결과
-- users: 303, signup_status NULL: 0
-- is_deleted=0 / COMPLETED: 182, is_deleted=0 / PENDING: 1
-- is_deleted=1 / PENDING: 120
-- COLUMN_TYPE: enum('COMPLETED','PENDING'), IS_NULLABLE: YES, COLUMN_DEFAULT: PENDING

-- 운영 DB 실행 후 사용자 확인 결과 (2026-07-20)
-- users: 307, signup_status NULL: 0, PENDING: 122, COMPLETED: 185
-- nickname 기준 SignupStatus 불일치: 0
-- COLUMN_TYPE: enum('COMPLETED','PENDING'), IS_NULLABLE: YES, COLUMN_DEFAULT: PENDING
-- 실행 당시 metadata lock 대기와 트래픽 영향은 별도 계측하지 못했다.
