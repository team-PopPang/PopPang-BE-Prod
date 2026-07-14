package com.poppang.be.domain.popup.presentation.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.BaseException;
import com.poppang.be.common.exception.ErrorCode;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.security.JwtAuthenticationFilter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.popup.application.PopupWebService;
import com.poppang.be.domain.popup.dto.web.response.PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
import com.poppang.be.domain.popup.dto.web.response.PopupWebSearchResponseDto;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(
    controllers = PopupWebController.class,
    properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PopupWebControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PopupWebService popupWebService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;

  @Test
  void getInProgressPopupListIsPublicJsonApiWithCommonResponseAndCardFields() throws Exception {
    PopupWebInProgressResponseDto popup =
        PopupWebInProgressResponseDto.builder()
            .popupUuid("7ed187ad-4ff9-11f1-8ba8-46b388519c93")
            .name("팝업스토어 이름")
            .thumbnailUrl("https://example.com/image.jpg")
            .region("서울 성동구")
            .startDate(LocalDate.of(2026, 7, 1))
            .endDate(LocalDate.of(2026, 7, 31))
            .build();
    given(popupWebService.getInProgressPopupList()).willReturn(List.of(popup));

    mockMvc
        .perform(get("/api/v1/web/popup/in-progress").accept(MediaType.APPLICATION_JSON))
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

    verify(popupWebService).getInProgressPopupList();
  }

  @Test
  void getInProgressPopupListReturnsEmptyArrayWhenThereAreNoPopups() throws Exception {
    given(popupWebService.getInProgressPopupList()).willReturn(List.of());

    mockMvc
        .perform(get("/api/v1/web/popup/in-progress").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty());

    verify(popupWebService).getInProgressPopupList();
  }

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
            get("/api/v1/web/popup/search").param("q", "성수").accept(MediaType.APPLICATION_JSON))
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
        PopupWebDetailResponseDto.builder().popupUuid("popup-uuid").name("기존 상세 팝업").build();
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
}
