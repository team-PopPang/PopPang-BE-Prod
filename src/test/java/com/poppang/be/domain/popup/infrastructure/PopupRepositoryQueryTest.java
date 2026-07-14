package com.poppang.be.domain.popup.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

class PopupRepositoryQueryTest {

  @Test
  void searchActivatedByKeywordIncludesActiveCurrentAndUpcomingPopups() throws Exception {
    Method searchMethod =
        PopupRepository.class.getDeclaredMethod("searchActivatedByKeyword", String.class);
    Query queryAnnotation = searchMethod.getAnnotation(Query.class);

    assertThat(queryAnnotation).isNotNull();
    assertThat(queryAnnotation.value())
        .contains("p.activated = true")
        .contains("p.endDate >= CURRENT_DATE")
        .doesNotContain("p.startDate <= CURRENT_DATE")
        .contains("lower(p.name) like lower(concat('%', :q, '%'))")
        .contains("lower(p.captionSummary) like lower(concat('%', :q, '%'))");
  }

  @Test
  void findInProgressActiveWithThumbnailUsesInclusiveCurrentDateAndActiveFilter() throws Exception {
    Query queryAnnotation = inProgressQuery();

    assertThat(queryAnnotation.nativeQuery()).isTrue();
    assertThat(normalize(queryAnnotation.value()))
        .contains("where p.is_active = 1")
        .contains("p.start_date <= current_date")
        .contains("p.end_date >= current_date");
  }

  @Test
  void findInProgressActiveWithThumbnailKeepsImageOptionalAndRejectsBlankIdentityFields()
      throws Exception {
    assertThat(normalize(inProgressQuery().value()))
        .contains("left join popup_image pi")
        .contains("pi.sort_order = 0")
        .contains("p.uuid is not null")
        .contains("trim(p.uuid) <> ''")
        .contains("p.name is not null")
        .contains("trim(p.name) <> ''");
  }

  @Test
  void findInProgressActiveWithThumbnailUsesStableClosingSoonOrder() throws Exception {
    assertThat(normalize(inProgressQuery().value()))
        .contains("order by p.end_date asc, p.start_date desc, p.uuid asc");
  }

  private Query inProgressQuery() throws Exception {
    Method method = PopupRepository.class.getDeclaredMethod("findInProgressActiveWithThumbnail");
    Query queryAnnotation = method.getAnnotation(Query.class);

    assertThat(queryAnnotation).isNotNull();
    return queryAnnotation;
  }

  private String normalize(String query) {
    return query.replaceAll("\\s+", " ").trim().toLowerCase();
  }
}
