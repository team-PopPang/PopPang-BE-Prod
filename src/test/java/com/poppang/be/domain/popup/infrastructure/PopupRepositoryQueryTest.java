package com.poppang.be.domain.popup.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
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

  @Test
  void everyHomeSortQueryExcludesInactiveEndedAndUpcomingPopups() throws Exception {
    for (String methodName :
        List.of(
            "findActiveByNewest",
            "findActiveByClosingSoon",
            "findActiveByMostFavorited",
            "findActiveByMostViewed")) {
      Method method =
          PopupRepository.class.getDeclaredMethod(methodName, String.class, String.class);
      Query queryAnnotation = method.getAnnotation(Query.class);

      assertThat(queryAnnotation).as(methodName).isNotNull();
      String query = canonical(queryAnnotation.value());
      assertThat(query)
          .as(methodName)
          .containsAnyOf("p.isactive = 1", "p.activated = true")
          .contains("p.startdate <= currentdate")
          .contains("p.enddate >= currentdate");
    }
  }

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
  void searchWebActiveWithThumbnailSelectsLowestPrimaryImageIdPerPopup() throws Exception {
    assertThat(normalize(webSearchQuery().value()))
        .containsSubsequence(
            "left join popup_image pi",
            "on pi.popup_id = p.id",
            "and pi.sort_order = 0",
            "and pi.id = ( select min(primary_image.id) from popup_image primary_image where"
                + " primary_image.popup_id = p.id and primary_image.sort_order = 0 )",
            "where p.is_active = 1");
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

  private Query inProgressQuery() throws Exception {
    Method method = PopupRepository.class.getDeclaredMethod("findInProgressActiveWithThumbnail");
    Query queryAnnotation = method.getAnnotation(Query.class);

    assertThat(queryAnnotation).isNotNull();
    return queryAnnotation;
  }

  private Query webSearchQuery() throws Exception {
    Method method =
        PopupRepository.class.getDeclaredMethod("searchWebActiveWithThumbnail", String.class);
    Query queryAnnotation = method.getAnnotation(Query.class);

    assertThat(queryAnnotation).isNotNull();
    return queryAnnotation;
  }

  private String normalize(String query) {
    return query.replaceAll("\\s+", " ").trim().toLowerCase();
  }

  private String canonical(String query) {
    return normalize(query).replace("_", "");
  }
}
