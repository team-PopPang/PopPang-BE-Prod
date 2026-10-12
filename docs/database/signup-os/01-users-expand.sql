-- 최초 가입 완료 OS를 기록하는 nullable 컬럼 확장
-- 서버 배포 전에 대상 DB, 백업, metadata lock 영향을 확인하고 별도 승인 후 실행한다.
-- 이 파일은 자동 실행되지 않는다. 운영 ddl-auto: validate는 유지한다.
-- 사전 조회에서 signup_os가 이미 있으면 ALTER를 재실행하지 말고 타입/NULL/default를 확인한다.
-- DDL은 implicit commit을 수반한다. 트랜잭션 ROLLBACK으로 되돌릴 수 있다고 가정하지 않는다.

SELECT DATABASE() AS target_database, VERSION() AS mysql_version;

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'signup_os';

ALTER TABLE users
  ADD COLUMN signup_os ENUM('IOS', 'ANDROID') NULL DEFAULT NULL
    COMMENT '최초 회원가입 완료 기기의 OS';

-- 기대: enum('IOS','ANDROID'), IS_NULLABLE=YES, COLUMN_DEFAULT=NULL
SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'users'
  AND column_name = 'signup_os';

SELECT signup_os, COUNT(*) AS user_count
FROM users
GROUP BY signup_os;

-- 기존 회원은 NULL로 유지한다. provider나 현재 기기로 backfill하지 않는다.
-- 서버 롤백 시에도 이 nullable 컬럼은 유지한다. DROP COLUMN은 수집 데이터가 유실되므로
-- 자동 롤백으로 제공하지 않는다. 구버전 서버는 컬럼을 몰라도 기존 INSERT/UPDATE를 수행한다.
