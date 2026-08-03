# JWT v2 users DB 변경 실행·되돌림 기준

이 문서는 운영 DDL을 자동 실행하는 문서가 아니다. `01-users-audit.sql` 결과와 운영 MySQL
버전, 실제 index 이름, 테이블 크기를 확인한 뒤 DB 담당자가 실행한다.

## 2026-07-16 확인된 현재 상태

- 운영 DB는 MySQL 8.0.43이며 users는 303 row다.
- `uq_users_uuid`와 `uq_users_uid`가 각각 단일-column unique index로 존재한다.
- 격리되지 않은 `PoppangBeApplicationTests`가 `ddl-auto: update`로 provider/role ENUM MODIFY와
  nullable `signup_status ENUM('COMPLETED','PENDING')` ADD COLUMN을 실행했다.
- 직후 읽기 전용 감사에서 303 row 모두 `signup_status IS NULL`이었다. uuid/uid/provider/role
  null·공백·중복·enum 이상은 0건이었다.
- nickname 기준 예상 분류는 PENDING 121건, COMPLETED 182건이다. 아직 backfill하지 않았다.
- context smoke test는 DB/Redis auto-configuration을 제외하도록 수정해 같은 경로의 재발을
  차단했다.
- 이후 운영 설정의 `ddl-auto`를 `update`에서 `validate`로 변경하고 CD 성공을 확인했다.

## 2026-07-20 운영 DB expand 적용 결과

아래는 사용자가 `poppang_prod_db`에서 직접 실행하고 제공한 수동 검증 증거다.

- users 307건의 `signup_status` NULL row를 nickname 기준으로 backfill했다.
- `signup_status NULL: 0`, `PENDING: 122`, `COMPLETED: 185`를 확인했다.
- nickname 기준 예상 상태와 실제 상태의 불일치는 0건이었다.
- `COLUMN_TYPE: enum('COMPLETED','PENDING')`, `IS_NULLABLE: YES`,
  `COLUMN_DEFAULT: PENDING`을 확인했다.
- `03-users-contract.sql`은 실행하지 않았다. 따라서 nullable expand 상태와 기존 uid 단일 unique를
  계속 유지한다.
- 실행 성공은 확인했지만 당시 metadata lock 대기 시간과 트래픽 영향은 별도 계측하지 않았다.

## 로컬 복원 DB migration 연습 결과

아래는 2026-07-17 사용자 제공 수동 검증 증거다. 운영 백업을 복원한 로컬 DB에만 해당하며
운영 DB 적용 결과로 간주하지 않는다.

- users 총 303건을 확인했다.
- `02-users-expand.sql`의 backfill과 기본값 변경을 실행한 뒤 DB 트랜잭션 COMMIT을 완료했다.
- `signup_status NULL 건수: 0`을 확인했다.
- 활성 COMPLETED 182건, 활성 PENDING 1건, 탈퇴 PENDING 120건을 확인했다.
- `COLUMN_TYPE: enum('COMPLETED','PENDING')`, `IS_NULLABLE: YES`, `COLUMN_DEFAULT: PENDING`을
  확인했다.
- 로컬 MySQL은 9.2.0이고 DB 이름은 `poppang_restore_test_20260716`으로 확인했다.
- 운영 DB에는 실행하지 않았고 `03-users-contract.sql`도 실행하지 않았다.

이 증거로 로컬 DB migration 절차의 backfill/default 결과는 확인됐다. 운영 MySQL 8.0.43과 로컬
MySQL 9.2.0의 버전 차이는 2026-07-19 사용자 결정으로 DB-E1의 동일-major 재연습 차단 항목에서
제외한다. 운영 DB expand 적용은 위 2026-07-20 결과로 확인됐다. 다만 구·신규 binary의 실제 DB
연결 호환성과 ALTER 당시 metadata lock 영향은 확인되지 않았다.

## 실행 전 필수 조건

- 실행 주체: 운영 DB DDL 권한을 가진 담당자 1명, 애플리케이션 담당자 1명이 함께 확인한다.
- 백업: users 및 연관 테이블의 시점 복구 가능한 snapshot을 만들고, 격리 환경에서 복구 여부를
  확인한다.
- 잠금: 운영 MySQL major와 users 크기를 기준으로 각 ALTER의 algorithm, 예상 lock 시간 및
  중단 기준을 정한다. 확인 전에는 online DDL 옵션을 추측하지 않는다.
- 애플리케이션: 현재 private 설정의 `ddl-auto: validate`를 유지한다. `update`로 되돌리거나
  migration SQL을 실행하려면 대상 DB와 변경·영향·롤백을 먼저 보고하고 별도 승인을 받는다.
- 기록: `SELECT VERSION()`, column/index 목록, audit 건수, 시작·종료 시각을 변경 기록에 남긴다.

## 실행 순서

1. `01-users-audit.sql`을 읽기 전용 계정으로 실행한다.
2. 이상 건수를 사람이 검토한다. uuid 또는 `(provider, uid)` 중복은 자동 병합하지 않는다.
3. DB-E1은 로컬 MySQL 9.2.0의 복원 연습 결과를 사용한다. 운영과 같은 major의 재연습은
   2026-07-19 사용자 결정으로 생략한다. 이 예외는 이후 DB wave에 자동 적용하지 않는다.
4. 운영 backup과 lock 계획 승인 후 expand를 실행한다.
5. 신규 binary와 모든 writer가 `signup_status`를 기록하는 것을 확인한다.
6. v1을 포함한 모든 provider 로그인 조회가 `(provider, uid)`를 사용하고 구 binary가 완전히
   종료됐는지 확인한다.
7. no-return 승인을 받은 뒤에만 `03-users-contract.sql`의 contract를 실행한다.

## 단계별 되돌림

### expand 후, 신규 binary 배포 전

현재 uuid unique index는 기존 `uq_users_uuid`이므로 제거하지 않는다. `signup_status` column을
제거하려면 신규 binary가 배포되지 않았고 backfill도 실행되지 않았음을 먼저 확인한다.

### expand 후, 신규 binary 배포 후

먼저 구 binary로 rollback한다. 그 뒤 신규 binary가 생성한 사용자와 상태 변경분을 확인한 후에만
column/index 제거를 판단한다. `signup_status`를 즉시 제거하면 가입 상태 정보가 유실된다.

### contract에서 uid 단일 unique 제거 전

여기가 자동으로 되돌릴 수 있는 마지막 되돌림 지점이다. 복합 unique가 추가됐더라도 기존 uid
단일 unique가 남아 있으면 구 binary의 전역 uid 가정을 아직 보존한다.

### uid 단일 unique 제거 후

provider가 다른 동일 uid가 저장될 수 있으므로 자동 rollback하지 않는다. 먼저 전역 uid 중복을
감사하고, 중복이 없을 때만 기존 index 이름으로 단일 unique를 다시 만든다. 중복이 있으면 데이터
소유자를 확인해 별도 정리 승인을 받아야 하며 임의 병합·삭제하지 않는다.

## 현재 확인되지 않은 항목

- 실행 당시 DDL metadata lock 대기 시간과 운영 트래픽 영향
- 구·신규 binary의 실제 DB 연결 호환

로컬 MySQL 버전은 9.2.0으로 확인됐고 운영 MySQL 8.0.43과의 차이는 DB-E1에 한해 수용했다.
운영 DB expand 적용은 2026-07-20 수동 검증 결과로 확인됐다. 위 두 항목은 후속 contract 단계와
rollback 판단에서 별도로 확인하거나 명시적으로 위험을 수용해야 한다.
