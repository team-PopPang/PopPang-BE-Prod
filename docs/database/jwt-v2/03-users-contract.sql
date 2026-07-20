-- JWT v2 users contract 단계 (no-return 승인 뒤 실행)
-- 이 파일은 운영 audit 결과로 placeholder를 교체하기 전에는 실행 대상이 아니다.
-- 운영 audit에서 기존 단일 uid unique index가 uq_users_uid임을 확인했다.
-- 같은 MySQL 8.0.43 격리 환경에서 lock 영향을 검증한 뒤 ALGORITHM/LOCK 옵션을 별도 승인한다.
-- v1 포함 모든 provider 로그인 조회가 (provider, uid)를 사용하고 구 binary가 완전히 종료된 뒤에만
-- 단일 uid unique를 제거한다.

-- contract 직전 구 binary가 만든 null row까지 다시 분류한다.
UPDATE users
SET signup_status = CASE
  WHEN nickname IS NULL THEN 'PENDING'
  ELSE 'COMPLETED'
END
WHERE signup_status IS NULL;

-- 아래 결과는 모두 0이어야 한다. 아니라면 즉시 중단한다.
SELECT
  SUM(signup_status IS NULL) AS signup_status_null_count,
  SUM(signup_status NOT IN ('PENDING', 'COMPLETED')) AS signup_status_invalid_count,
  SUM(provider IS NULL) AS provider_null_count,
  SUM(uid IS NULL) AS uid_null_count
FROM users;

SELECT provider, uid, COUNT(*) AS duplicate_count
FROM users
WHERE provider IS NOT NULL
  AND uid IS NOT NULL
GROUP BY provider, uid
HAVING COUNT(*) > 1;

-- 복합 unique를 먼저 추가해 identity 보호 공백을 만들지 않는다.
ALTER TABLE users
  ADD UNIQUE INDEX uq_users_provider_uid (provider, uid);

ALTER TABLE users
  DROP INDEX uq_users_uid;

ALTER TABLE users
  MODIFY COLUMN signup_status ENUM('COMPLETED', 'PENDING') NOT NULL DEFAULT 'PENDING';
