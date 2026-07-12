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
}
