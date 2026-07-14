# Public Web Popup Search API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 인증 없는 `GET /api/v1/web/popup/search?q={검색어}`를 추가하여 공개 중이고 종료되지 않은 팝업을 이름·지역·설명 요약으로 검색하고 기존 Web 카드 형식으로 반환한다.

**Architecture:** 기존 회원·비회원 검색 endpoint와 Repository 메서드는 변경하지 않는다. `PopupWebController`와 `PopupWebService`에 Web 검색 유스케이스를 추가하고, Web 전용 native Projection 쿼리 한 번으로 검색·공개 필터·대표 이미지·안정적인 정렬을 처리한다.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring MVC, Spring Data JPA, MySQL native query, SpringDoc OpenAPI 2.7.0, JUnit 5, Mockito, AssertJ, MockMvc, Gradle 8.14.3, Spotless/google-java-format

## Global Constraints

- 기존 `GET /api/v1/popup/search`와 `GET /api/v1/users/{userUuid}/popups/search`의 URL, 요청, 응답, 검색 조건을 변경하지 않는다.
- 기존 `random`, `in-progress`, `favorite`, `upcoming`, 상세 Web API의 응답과 동작을 변경하지 않는다.
- 검색 대상은 이름 `name`, 지역 `region`, 설명 요약 `caption_summary`다.
- `q`는 trim 후 검색하며 null·빈 문자열·공백 문자열은 HTTP 400 공통 오류 응답으로 처리한다.
- 검색어 최대 길이를 추가하지 않고 `%`, `_`는 기존 검색과 동일하게 LIKE 와일드카드로 유지한다.
- `is_active = 1 AND end_date >= CURRENT_DATE`를 적용하여 진행 중·오늘 종료·모든 오픈 예정 팝업을 포함하고 종료·비활성 팝업을 제외한다.
- `popup_image.sort_order = 0` 이미지를 `LEFT JOIN`하고 이미지가 없으면 `thumbnailUrl = null`로 반환한다.
- 이름 완전 일치, 이름 시작 일치, 이름 부분 일치, 지역 일치, 설명 일치, `start_date DESC`, `uuid ASC` 순으로 정렬한다.
- 응답은 `ApiResponse<List<PopupWebSearchResponseDto>>`이며 페이지네이션을 추가하지 않는다.
- dependency, DB schema, 환경변수, 캐시, 프론트엔드, Security 전역 설정을 변경하지 않는다.
- 각 `git commit` 직전에 대상 파일과 메시지를 사용자에게 알리고 새 승인을 받는다.
- `git push`, main merge, 배포를 실행하지 않는다.

---

## File Structure

### 새 파일

- `src/main/java/com/poppang/be/domain/popup/dto/web/response/PopupWebSearchResponseDto.java`: Web 검색 카드의 여섯 응답 필드
- `src/main/java/com/poppang/be/domain/popup/infrastructure/projection/PopupWebSearchRow.java`: native query 결과 Projection

### 수정 파일

- `src/main/java/com/poppang/be/common/exception/ErrorCode.java`: 빈 검색어용 `INVALID_POPUP_SEARCH_QUERY`
- `src/main/java/com/poppang/be/domain/popup/application/PopupWebService.java`: Web 검색 서비스 계약
- `src/main/java/com/poppang/be/domain/popup/application/PopupWebServiceImpl.java`: 검색어 검증, trim, Projection-to-DTO 매핑
- `src/main/java/com/poppang/be/domain/popup/infrastructure/PopupRepository.java`: Web 전용 단일 검색 Projection 쿼리
- `src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java`: 공개 GET endpoint와 OpenAPI 정보
- `src/test/java/com/poppang/be/domain/popup/application/PopupWebServiceImplTest.java`: 검색어 검증과 DTO 매핑 단위 테스트
- `src/test/java/com/poppang/be/domain/popup/infrastructure/PopupRepositoryQueryTest.java`: 검색 SQL 계약과 기존 검색 회귀 테스트
- `src/test/java/com/poppang/be/domain/popup/presentation/web/PopupWebControllerTest.java`: 공개 API, 라우팅, 공통 응답, 오류 테스트
- `src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java`: 신규 path, parameter, schema, media type 계약

---

### Task 1: Web 검색 데이터 조회와 서비스 파이프라인

**Files:**
- Create: `src/main/java/com/poppang/be/domain/popup/dto/web/response/PopupWebSearchResponseDto.java`
- Create: `src/main/java/com/poppang/be/domain/popup/infrastructure/projection/PopupWebSearchRow.java`
- Modify: `src/main/java/com/poppang/be/common/exception/ErrorCode.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/application/PopupWebService.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/application/PopupWebServiceImpl.java`
- Modify: `src/main/java/com/poppang/be/domain/popup/infrastructure/PopupRepository.java`
- Test: `src/test/java/com/poppang/be/domain/popup/application/PopupWebServiceImplTest.java`
- Test: `src/test/java/com/poppang/be/domain/popup/infrastructure/PopupRepositoryQueryTest.java`
- Include in first approved implementation commit: `docs/superpowers/plans/2026-07-14-web-popup-search.md`

**Interfaces:**
- Consumes: 기존 `PopupRepository`, `ApiResponse` 오류 정책, `popup`과 `popup_image` 테이블
- Produces: `PopupWebService#getSearchPopupList(String)`, `PopupRepository#searchWebActiveWithThumbnail(String)`, `PopupWebSearchResponseDto`, `PopupWebSearchRow`, `ErrorCode.INVALID_POPUP_SEARCH_QUERY`

- [ ] **Step 1: 변경 전 OpenAPI 기준선 생성**

Run:

```bash
./gradlew test --tests com.poppang.be.common.config.OpenApiMediaTypeContractTest --rerun-tasks
cp build/openapi/openapi.json /tmp/poppang-openapi-before-web-search.json
```

Expected: Gradle `BUILD SUCCESSFUL`이고 `/tmp/poppang-openapi-before-web-search.json`이 현재 45개 JSON 응답 계약을 포함한다.

- [ ] **Step 2: Service 실패 테스트 작성**

`PopupWebServiceImplTest`에 다음 imports를 추가한다.

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebSearchRow;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
```

같은 테스트 클래스에 다음 테스트와 helper record를 추가한다.

```java
@Test
void getSearchPopupListTrimsQueryMapsEveryFieldAndPreservesRepositoryOrder() {
  PopupWebSearchRow exactName =
      searchRow(
          "popup-exact",
          "성수",
          "https://example.com/exact.jpg",
          "서울 성동구",
          LocalDate.of(2026, 8, 1),
          LocalDate.of(2026, 8, 31));
  PopupWebSearchRow regionMatch =
      searchRow(
          "popup-region",
          "여름 브랜드 전시",
          null,
          "서울 성수",
          LocalDate.of(2026, 7, 1),
          LocalDate.of(2026, 7, 31));
  given(popupRepository.searchWebActiveWithThumbnail("성수"))
      .willReturn(List.of(exactName, regionMatch));

  List<PopupWebSearchResponseDto> result = popupWebService.getSearchPopupList("  성수  ");

  assertThat(result)
      .extracting(PopupWebSearchResponseDto::getPopupUuid)
      .containsExactly("popup-exact", "popup-region");
  assertThat(result.get(0))
      .satisfies(
          popup -> {
            assertThat(popup.getName()).isEqualTo("성수");
            assertThat(popup.getThumbnailUrl()).isEqualTo("https://example.com/exact.jpg");
            assertThat(popup.getRegion()).isEqualTo("서울 성동구");
            assertThat(popup.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(popup.getEndDate()).isEqualTo(LocalDate.of(2026, 8, 31));
          });
  assertThat(result.get(1).getThumbnailUrl()).isNull();
  verify(popupRepository).searchWebActiveWithThumbnail("성수");
}

@Test
void getSearchPopupListReturnsEmptyListWhenRepositoryHasNoRows() {
  given(popupRepository.searchWebActiveWithThumbnail("없는검색어")).willReturn(List.of());

  List<PopupWebSearchResponseDto> result = popupWebService.getSearchPopupList("없는검색어");

  assertThat(result).isEmpty();
}

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = {" ", " \t "})
void getSearchPopupListRejectsMissingOrBlankQuery(String q) {
  assertThatThrownBy(() -> popupWebService.getSearchPopupList(q))
      .isInstanceOfSatisfying(
          BaseException.class,
          exception ->
              assertThat(exception.getErrorCode())
                  .isEqualTo(ErrorCode.INVALID_POPUP_SEARCH_QUERY));

  verifyNoInteractions(popupRepository);
}

private PopupWebSearchRow searchRow(
    String popupUuid,
    String popupName,
    String thumbnailUrl,
    String region,
    LocalDate startDate,
    LocalDate endDate) {
  return new TestPopupWebSearchRow(
      popupUuid, popupName, thumbnailUrl, region, startDate, endDate);
}

private record TestPopupWebSearchRow(
    String popupUuid,
    String popupName,
    String thumbnailUrl,
    String region,
    LocalDate startDate,
    LocalDate endDate)
    implements PopupWebSearchRow {

  @Override
  public String getPopupUuid() {
    return popupUuid;
  }

  @Override
  public String getPopupName() {
    return popupName;
  }

  @Override
  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  @Override
  public String getRegion() {
    return region;
  }

  @Override
  public LocalDate getStartDate() {
    return startDate;
  }

  @Override
  public LocalDate getEndDate() {
    return endDate;
  }
}
```

- [ ] **Step 3: Repository 쿼리 실패 테스트 작성**

`PopupRepositoryQueryTest`에 다음 테스트와 helper를 추가한다.

```java
@Test
void searchWebActiveWithThumbnailSearchesNameRegionAndCaptionSummary() throws Exception {
  String query = normalize(webSearchQuery().value());

  assertThat(query)
      .contains("lower(p.name) like lower(concat('%', :q, '%'))")
      .contains("lower(p.region) like lower(concat('%', :q, '%'))")
      .contains("lower(p.caption_summary) like lower(concat('%', :q, '%'))");
}

@Test
void searchWebActiveWithThumbnailIncludesActiveCurrentAndUpcomingPopups() throws Exception {
  String query = normalize(webSearchQuery().value());

  assertThat(webSearchQuery().nativeQuery()).isTrue();
  assertThat(query)
      .contains("p.is_active = 1")
      .contains("p.end_date >= current_date")
      .doesNotContain("p.start_date <= current_date");
}

@Test
void searchWebActiveWithThumbnailKeepsImageOptionalAndRejectsBlankIdentityFields()
    throws Exception {
  assertThat(normalize(webSearchQuery().value()))
      .contains("left join popup_image pi")
      .contains("pi.sort_order = 0")
      .contains("p.uuid is not null")
      .contains("trim(p.uuid) <> ''")
      .contains("p.name is not null")
      .contains("trim(p.name) <> ''");
}

@Test
void searchWebActiveWithThumbnailUsesStableRelevanceOrder() throws Exception {
  assertThat(normalize(webSearchQuery().value()))
      .containsSubsequence(
          "when lower(p.name) = lower(:q) then 0",
          "when lower(p.name) like lower(concat(:q, '%')) then 1",
          "when lower(p.name) like lower(concat('%', :q, '%')) then 2",
          "when lower(p.region) like lower(concat('%', :q, '%')) then 3",
          "else 4",
          "p.start_date desc",
          "p.uuid asc");
}

private Query webSearchQuery() throws Exception {
  Method method =
      PopupRepository.class.getDeclaredMethod("searchWebActiveWithThumbnail", String.class);
  Query queryAnnotation = method.getAnnotation(Query.class);

  assertThat(queryAnnotation).isNotNull();
  return queryAnnotation;
}
```

기존 `searchActivatedByKeywordIncludesActiveCurrentAndUpcomingPopups` 테스트는 수정하지 않는다. 이 테스트가 기존 비회원 검색에서 이름·설명 검색, 활성 상태, 종료일 조건을 계속 보장한다.

- [ ] **Step 4: 테스트가 신규 기능 부재 때문에 실패하는지 확인**

Run:

```bash
./gradlew test --tests com.poppang.be.domain.popup.application.PopupWebServiceImplTest --tests com.poppang.be.domain.popup.infrastructure.PopupRepositoryQueryTest --rerun-tasks
```

Expected: `PopupWebSearchRow`, `PopupWebSearchResponseDto`, `getSearchPopupList`, `searchWebActiveWithThumbnail`, `INVALID_POPUP_SEARCH_QUERY`가 없어서 compilation 또는 test failure가 발생한다.

- [ ] **Step 5: Web 검색 DTO와 Projection 구현**

`PopupWebSearchResponseDto.java`를 다음 내용으로 생성한다.

```java
package com.poppang.be.domain.popup.dto.web.response;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopupWebSearchResponseDto {

  private String popupUuid;
  private String name;
  private String thumbnailUrl;
  private String region;
  private LocalDate startDate;
  private LocalDate endDate;

  @Builder
  public PopupWebSearchResponseDto(
      String popupUuid,
      String name,
      String thumbnailUrl,
      String region,
      LocalDate startDate,
      LocalDate endDate) {
    this.popupUuid = popupUuid;
    this.name = name;
    this.thumbnailUrl = thumbnailUrl;
    this.region = region;
    this.startDate = startDate;
    this.endDate = endDate;
  }
}
```

`PopupWebSearchRow.java`를 다음 내용으로 생성한다.

```java
package com.poppang.be.domain.popup.infrastructure.projection;

import java.time.LocalDate;

public interface PopupWebSearchRow {
  String getPopupUuid();

  String getPopupName();

  String getThumbnailUrl();

  String getRegion();

  LocalDate getStartDate();

  LocalDate getEndDate();
}
```

- [ ] **Step 6: 오류 코드와 Service 계약 구현**

`ErrorCode`의 Popup 4300번 영역 마지막에 다음 값을 추가한다.

```java
INVALID_POPUP_SEARCH_QUERY(HttpStatus.BAD_REQUEST, 4313, "검색어는 필수입니다."),
```

`PopupWebService`에 import와 메서드를 추가한다.

```java
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
```

```java
List<PopupWebSearchResponseDto> getSearchPopupList(String q);
```

- [ ] **Step 7: Web 전용 단일 Projection 쿼리 구현**

`PopupRepository`에 import를 추가한다.

```java
import com.poppang.be.domain.popup.infrastructure.projection.PopupWebSearchRow;
```

기존 `searchActivatedByKeyword`는 그대로 두고 다음 메서드를 그 아래에 추가한다.

```java
@Query(
    value =
        """
          SELECT
              p.uuid AS popupUuid,
              p.name AS popupName,
              pi.image_url AS thumbnailUrl,
              p.region AS region,
              p.start_date AS startDate,
              p.end_date AS endDate
          FROM popup p
          LEFT JOIN popup_image pi
            ON pi.popup_id = p.id
           AND pi.sort_order = 0
          WHERE p.is_active = 1
            AND p.end_date >= CURRENT_DATE
            AND p.uuid IS NOT NULL
            AND TRIM(p.uuid) <> ''
            AND p.name IS NOT NULL
            AND TRIM(p.name) <> ''
            AND (
                 LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.region) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.caption_summary) LIKE LOWER(CONCAT('%', :q, '%'))
            )
          ORDER BY
            CASE
              WHEN LOWER(p.name) = LOWER(:q) THEN 0
              WHEN LOWER(p.name) LIKE LOWER(CONCAT(:q, '%')) THEN 1
              WHEN LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) THEN 2
              WHEN LOWER(p.region) LIKE LOWER(CONCAT('%', :q, '%')) THEN 3
              ELSE 4
            END ASC,
            p.start_date DESC,
            p.uuid ASC
          """,
    nativeQuery = true)
List<PopupWebSearchRow> searchWebActiveWithThumbnail(@Param("q") String q);
```

- [ ] **Step 8: 검색어 검증과 DTO 매핑 구현**

`PopupWebServiceImpl`에 import를 추가한다.

```java
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
```

같은 클래스에 다음 메서드를 추가한다.

```java
@Override
@Transactional(readOnly = true)
public List<PopupWebSearchResponseDto> getSearchPopupList(String q) {
  String term = q == null ? "" : q.trim();
  if (term.isEmpty()) {
    throw new BaseException(ErrorCode.INVALID_POPUP_SEARCH_QUERY);
  }

  return popupRepository.searchWebActiveWithThumbnail(term).stream()
      .map(
          row ->
              PopupWebSearchResponseDto.builder()
                  .popupUuid(row.getPopupUuid())
                  .name(row.getPopupName())
                  .thumbnailUrl(row.getThumbnailUrl())
                  .region(row.getRegion())
                  .startDate(row.getStartDate())
                  .endDate(row.getEndDate())
                  .build())
      .toList();
}
```

- [ ] **Step 9: Task 1 테스트 통과 확인**

Run:

```bash
./gradlew spotlessApply
./gradlew test --tests com.poppang.be.domain.popup.application.PopupWebServiceImplTest --tests com.poppang.be.domain.popup.infrastructure.PopupRepositoryQueryTest --rerun-tasks
```

Expected: 두 테스트 클래스가 모두 통과하고 기존 비회원 검색 쿼리 회귀 테스트도 통과한다.

- [ ] **Step 10: Task 1 커밋 승인 요청**

사용자에게 아래 커밋 대상과 메시지를 알리고 명시적 승인을 받는다.

Files:

```text
docs/superpowers/plans/2026-07-14-web-popup-search.md
src/main/java/com/poppang/be/common/exception/ErrorCode.java
src/main/java/com/poppang/be/domain/popup/application/PopupWebService.java
src/main/java/com/poppang/be/domain/popup/application/PopupWebServiceImpl.java
src/main/java/com/poppang/be/domain/popup/dto/web/response/PopupWebSearchResponseDto.java
src/main/java/com/poppang/be/domain/popup/infrastructure/PopupRepository.java
src/main/java/com/poppang/be/domain/popup/infrastructure/projection/PopupWebSearchRow.java
src/test/java/com/poppang/be/domain/popup/application/PopupWebServiceImplTest.java
src/test/java/com/poppang/be/domain/popup/infrastructure/PopupRepositoryQueryTest.java
```

Message:

```text
feat: Web 팝업 검색 서비스와 조회 쿼리 추가
```

승인받은 경우에만 다음 명령을 실행한다.

```bash
git add docs/superpowers/plans/2026-07-14-web-popup-search.md src/main/java/com/poppang/be/common/exception/ErrorCode.java src/main/java/com/poppang/be/domain/popup/application/PopupWebService.java src/main/java/com/poppang/be/domain/popup/application/PopupWebServiceImpl.java src/main/java/com/poppang/be/domain/popup/dto/web/response/PopupWebSearchResponseDto.java src/main/java/com/poppang/be/domain/popup/infrastructure/PopupRepository.java src/main/java/com/poppang/be/domain/popup/infrastructure/projection/PopupWebSearchRow.java src/test/java/com/poppang/be/domain/popup/application/PopupWebServiceImplTest.java src/test/java/com/poppang/be/domain/popup/infrastructure/PopupRepositoryQueryTest.java
git commit -m "feat: Web 팝업 검색 서비스와 조회 쿼리 추가"
```

---

### Task 2: 공개 Controller와 OpenAPI 계약

**Files:**
- Modify: `src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java`
- Test: `src/test/java/com/poppang/be/domain/popup/presentation/web/PopupWebControllerTest.java`
- Test: `src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java`

**Interfaces:**
- Consumes: Task 1의 `PopupWebService#getSearchPopupList(String)`과 `PopupWebSearchResponseDto`
- Produces: 인증 없는 `GET /api/v1/web/popup/search`, operationId `getWebSearchPopupList`, JSON `ApiResponse<List<PopupWebSearchResponseDto>>`

- [ ] **Step 1: Controller/API 실패 테스트 작성**

`PopupWebControllerTest`에 다음 imports를 추가한다.

```java
import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
```

같은 테스트 클래스에 다음 테스트와 helper를 추가한다.

```java
@Test
void getSearchPopupListIsPublicJsonApiWithCommonResponseAndCardFields() throws Exception {
  PopupWebSearchResponseDto popup =
      PopupWebSearchResponseDto.builder()
          .popupUuid("7ed187ad-4ff9-11f1-8ba8-46b388519c93")
          .name("성수 캐릭터 팝업")
          .thumbnailUrl("https://example.com/image.jpg")
          .region("서울 성수")
          .startDate(LocalDate.of(2026, 7, 1))
          .endDate(LocalDate.of(2026, 7, 31))
          .build();
  given(popupWebService.getSearchPopupList("성수")).willReturn(List.of(popup));

  mockMvc
      .perform(
          get("/api/v1/web/popup/search")
              .param("q", "성수")
              .accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.success").value(true))
      .andExpect(jsonPath("$.code").value(0))
      .andExpect(jsonPath("$.message").value("요청 성공!"))
      .andExpect(jsonPath("$.data").isArray())
      .andExpect(jsonPath("$.data[0].popupUuid").value(popup.getPopupUuid()))
      .andExpect(jsonPath("$.data[0].name").value(popup.getName()))
      .andExpect(jsonPath("$.data[0].thumbnailUrl").value(popup.getThumbnailUrl()))
      .andExpect(jsonPath("$.data[0].region").value(popup.getRegion()))
      .andExpect(jsonPath("$.data[0].startDate").value("2026-07-01"))
      .andExpect(jsonPath("$.data[0].endDate").value("2026-07-31"));

  verify(popupWebService).getSearchPopupList("성수");
}

@Test
void getSearchPopupListReturnsEmptyArrayWhenThereAreNoMatches() throws Exception {
  given(popupWebService.getSearchPopupList("없는검색어")).willReturn(List.of());

  mockMvc
      .perform(get("/api/v1/web/popup/search").param("q", "없는검색어"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.data").isArray())
      .andExpect(jsonPath("$.data").isEmpty());

  verify(popupWebService).getSearchPopupList("없는검색어");
}

@Test
void getSearchPopupListRejectsMissingQuery() throws Exception {
  assertInvalidSearchRequest(get("/api/v1/web/popup/search"), null);
}

@Test
void getSearchPopupListRejectsEmptyQuery() throws Exception {
  assertInvalidSearchRequest(get("/api/v1/web/popup/search").param("q", ""), "");
}

@Test
void getSearchPopupListRejectsWhitespaceQuery() throws Exception {
  assertInvalidSearchRequest(get("/api/v1/web/popup/search").param("q", "   "), "   ");
}

@Test
void popupUuidPathStillUsesDetailEndpoint() throws Exception {
  PopupWebDetailResponseDto detail =
      PopupWebDetailResponseDto.builder()
          .popupUuid("popup-uuid")
          .name("기존 상세 팝업")
          .build();
  given(popupWebService.getPopupDetail("popup-uuid")).willReturn(detail);

  mockMvc
      .perform(get("/api/v1/web/popup/popup-uuid"))
      .andExpect(status().isOk())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.data.popupUuid").value("popup-uuid"))
      .andExpect(jsonPath("$.data.name").value("기존 상세 팝업"));

  verify(popupWebService).getPopupDetail("popup-uuid");
}

private void assertInvalidSearchRequest(MockHttpServletRequestBuilder request, String q)
    throws Exception {
  given(popupWebService.getSearchPopupList(q))
      .willThrow(new BaseException(ErrorCode.INVALID_POPUP_SEARCH_QUERY));

  mockMvc
      .perform(request.accept(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.success").value(false))
      .andExpect(jsonPath("$.code").value(4313))
      .andExpect(jsonPath("$.message").value("검색어는 필수입니다."));

  verify(popupWebService).getSearchPopupList(q);
}
```

검색 성공 테스트는 Authorization 헤더를 설정하지 않으며 실제 `SecurityConfig`와 `JwtAuthenticationFilter`가 적용된 `@WebMvcTest`에서 공개 접근을 검증한다.

- [ ] **Step 2: OpenAPI 실패 테스트 작성**

`OpenApiMediaTypeContractTest#responseContentsUseApplicationJsonAndBodylessResponsesHaveNoContent`의 JSON 응답 개수 기대값을 신규 endpoint 한 개만큼 증가시킨다.

```java
assertThat(jsonResponseCount).isEqualTo(46);
```

`webPopupResponsesKeepJsonMediaTypeAndSchemaReferences`에 다음 assertion을 추가한다.

```java
assertJsonResponse(
    "/api/v1/web/popup/search",
    "#/components/schemas/ApiResponseListPopupWebSearchResponseDto");
```

다음 신규 계약 테스트를 추가한다.

```java
@Test
void webPopupSearchOpenApiContractIsExact() {
  JsonNode operation = openApi.path("paths").path("/api/v1/web/popup/search").path("get");

  assertThat(operation.path("tags").isArray()).isTrue();
  assertThat(operation.path("tags").size()).isEqualTo(1);
  assertThat(operation.path("tags").path(0).asText()).isEqualTo("[WEB] [POPUP]");
  assertThat(operation.path("summary").asText()).isEqualTo("[WEB] 팝업 검색");
  assertThat(operation.path("description").asText())
      .isEqualTo("검색어를 이용해 웹에 공개된 팝업스토어 목록을 검색합니다.");
  assertThat(operation.path("operationId").asText()).isEqualTo("getWebSearchPopupList");
  assertThat(operation.has("requestBody")).isFalse();

  JsonNode parameters = operation.path("parameters");
  assertThat(parameters.isArray()).isTrue();
  assertThat(parameters.size()).isEqualTo(1);
  JsonNode q = parameters.path(0);
  assertThat(q.path("name").asText()).isEqualTo("q");
  assertThat(q.path("in").asText()).isEqualTo("query");
  assertThat(q.path("required").asBoolean()).isTrue();
  assertThat(q.path("schema").path("type").asText()).isEqualTo("string");

  JsonNode properties =
      openApi
          .path("components")
          .path("schemas")
          .path("PopupWebSearchResponseDto")
          .path("properties");
  assertThat(fieldNames(properties))
      .containsExactly("popupUuid", "name", "thumbnailUrl", "region", "startDate", "endDate");
  assertStringSchema(properties.path("popupUuid"), null);
  assertStringSchema(properties.path("name"), null);
  assertStringSchema(properties.path("thumbnailUrl"), null);
  assertStringSchema(properties.path("region"), null);
  assertStringSchema(properties.path("startDate"), "date");
  assertStringSchema(properties.path("endDate"), "date");
}
```

`webPopupRuntimeResponsesAreJson`의 stubbing과 검증에 Web 검색을 추가한다.

```java
given(popupWebService.getSearchPopupList("성수")).willReturn(List.of());

mockMvc
    .perform(
        get("/api/v1/web/popup/search")
            .param("q", "성수")
            .accept(MediaType.APPLICATION_JSON))
    .andExpect(status().isOk())
    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
```

- [ ] **Step 3: 테스트가 endpoint 부재 때문에 실패하는지 확인**

Run:

```bash
./gradlew test --tests com.poppang.be.domain.popup.presentation.web.PopupWebControllerTest --tests com.poppang.be.common.config.OpenApiMediaTypeContractTest --rerun-tasks
```

Expected: `/api/v1/web/popup/search`가 아직 상세 route로 처리되거나 OpenAPI path가 없어서 새 Controller/OpenAPI 테스트가 실패한다.

- [ ] **Step 4: 공개 Web 검색 Controller 구현**

`PopupWebController`에 다음 imports를 추가한다.

```java
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import io.swagger.v3.oas.annotations.Parameter;
```

`/{popupUuid}` 메서드보다 위에 다음 메서드를 추가한다.

```java
@Operation(
    operationId = "getWebSearchPopupList",
    summary = "[WEB] 팝업 검색",
    description = "검색어를 이용해 웹에 공개된 팝업스토어 목록을 검색합니다.")
@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
public ApiResponse<List<PopupWebSearchResponseDto>> getWebSearchPopupList(
    @Parameter(description = "검색어", required = true)
        @RequestParam(name = "q", required = false)
        String q) {
  List<PopupWebSearchResponseDto> searchPopupList = popupWebService.getSearchPopupList(q);

  return ApiResponse.ok(searchPopupList);
}
```

- [ ] **Step 5: Controller와 OpenAPI 테스트 통과 확인**

Run:

```bash
./gradlew spotlessApply
./gradlew test --tests com.poppang.be.domain.popup.presentation.web.PopupWebControllerTest --tests com.poppang.be.common.config.OpenApiMediaTypeContractTest --rerun-tasks
```

Expected: 공개 접근, 정적 route, 상세 route 회귀, 빈 검색어 400, JSON wrapper, OpenAPI 필수 `q`와 schema 테스트가 모두 통과한다.

- [ ] **Step 6: OpenAPI 의도치 않은 변경 확인**

신규 OpenAPI를 생성한다.

```bash
cp build/openapi/openapi.json /tmp/poppang-openapi-after-web-search.json
```

변경 전 문서와 변경 후 문서를 정렬하되, 승인된 신규 path와 schema 두 개만 변경 후 문서에서 제거한다.

```bash
jq -S '.' /tmp/poppang-openapi-before-web-search.json > /tmp/poppang-openapi-before-normalized.json
jq -S 'del(.paths["/api/v1/web/popup/search"]) | del(.components.schemas.PopupWebSearchResponseDto) | del(.components.schemas.ApiResponseListPopupWebSearchResponseDto)' /tmp/poppang-openapi-after-web-search.json > /tmp/poppang-openapi-after-normalized.json
diff -u /tmp/poppang-openapi-before-normalized.json /tmp/poppang-openapi-after-normalized.json
```

Expected: `diff` 출력이 없고 exit code 0이다. 원본 두 문서의 실제 차이는 신규 `/api/v1/web/popup/search`, `PopupWebSearchResponseDto`, `ApiResponseListPopupWebSearchResponseDto`뿐이다.

- [ ] **Step 7: Task 2 커밋 승인 요청**

사용자에게 아래 커밋 대상과 메시지를 알리고 명시적 승인을 받는다.

Files:

```text
src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java
src/test/java/com/poppang/be/domain/popup/presentation/web/PopupWebControllerTest.java
src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java
```

Message:

```text
feat: 공개 Web 팝업 검색 API 추가
```

승인받은 경우에만 다음 명령을 실행한다.

```bash
git add src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java src/test/java/com/poppang/be/domain/popup/presentation/web/PopupWebControllerTest.java src/test/java/com/poppang/be/common/config/OpenApiMediaTypeContractTest.java
git commit -m "feat: 공개 Web 팝업 검색 API 추가"
```

---

### Task 3: 전체 회귀 검증과 완료 근거 수집

**Files:**
- Verify only: 전체 production/test source
- Inspect: `build/openapi/openapi.json`
- Inspect: `build/test-results/test/*.xml`

**Interfaces:**
- Consumes: Task 1과 Task 2의 구현 결과
- Produces: 전체 테스트·빌드·포맷 결과, 테스트 성공·실패 개수, OpenAPI diff 근거, 최종 변경 파일 목록

- [ ] **Step 1: 최종 diff와 포맷 오류 사전 확인**

Run:

```bash
git status --short
git diff --check
./gradlew spotlessCheck --rerun-tasks
```

Expected: 의도한 파일만 변경되어 있고 `git diff --check`와 `spotlessCheck`가 exit code 0이다.

- [ ] **Step 2: 요청된 전체 검증 명령 실행**

Run:

```bash
./gradlew test build spotlessCheck --rerun-tasks
```

Expected: Gradle `BUILD SUCCESSFUL`. 실패하면 성공으로 표현하지 않고 실패 task, 원인, 성공한 범위를 기록한다.

- [ ] **Step 3: 테스트 성공·실패 개수 수집**

Run:

```bash
rg '<testsuite ' build/test-results/test -g '*.xml'
```

각 XML의 `tests`, `failures`, `errors`, `skipped` 합계를 집계하여 완료 보고에 기록한다. `failures + errors = 0`일 때만 전체 테스트 성공으로 보고한다.

- [ ] **Step 4: 기존 API 무변경 확인**

Run:

```bash
git diff a85d24d -- src/main/java/com/poppang/be/domain/popup/presentation/app/PopupController.java src/main/java/com/poppang/be/domain/popup/presentation/app/PopupUserController.java
git diff a85d24d -- src/main/java/com/poppang/be/domain/popup/presentation/web/PopupWebController.java
git diff a85d24d -- src/main/java/com/poppang/be/domain/popup/infrastructure/PopupRepository.java
```

Expected:

- 기존 app Controller 두 파일에는 diff가 없다.
- Web Controller diff는 신규 `/search` 메서드와 imports뿐이다.
- Repository diff는 신규 import와 `searchWebActiveWithThumbnail`뿐이며 `searchActivatedByKeyword`는 바뀌지 않는다.

- [ ] **Step 5: 완료 보고 작성**

완료 보고에는 다음 근거를 포함한다.

- `GET /api/v1/web/popup/search?q={검색어}`
- 이름·지역·설명 요약의 대소문자 무시 부분 검색
- 진행 중·오늘 종료·모든 오픈 예정 포함, 종료·비활성 제외
- `sort_order = 0` 대표 이미지와 이미지가 없을 때 null
- 관련도, 시작일 내림차순, UUID 오름차순 정렬
- q 누락·빈 값·공백의 HTTP 400과 최대 길이 제한 없음
- Authorization 헤더 없는 MockMvc 성공 검증
- JSON 성공 응답 예시와 OpenAPI schema/media type
- 기존 회원·비회원 검색과 기존 Web API의 무변경 여부
- 실행한 명령과 테스트 성공·실패·skip 개수
- OpenAPI에서 승인된 path/schema 외 변경이 없다는 diff 결과
- push, main merge, 배포를 실행하지 않았다는 사실
