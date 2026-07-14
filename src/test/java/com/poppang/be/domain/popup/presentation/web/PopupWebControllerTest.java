package com.poppang.be.domain.popup.presentation.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.security.JwtAuthenticationFilter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.popup.application.PopupWebService;
import com.poppang.be.domain.popup.dto.web.response.PopupWebInProgressResponseDto;
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
}
