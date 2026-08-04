package com.poppang.be.domain.popup.presentation.v2;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.popup.application.V2PopupWebService;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebDetailResponseDto;
import com.poppang.be.domain.popup.dto.v2.web.V2PopupWebUpcomingResponseDto;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2PopupWebController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false",
      "internal.worker.api-key=${random.uuid}${random.uuid}"
    })
@Import(SecurityConfig.class)
class V2PopupWebControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2PopupWebService popupWebService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @Test
  void allSixGetEndpointsArePublicWithoutAToken() throws Exception {
    given(popupWebService.getRandomPopupList()).willReturn(List.of());
    given(popupWebService.getFavoritePopupList()).willReturn(List.of());
    given(popupWebService.getInProgressPopupList(null, null, null)).willReturn(List.of());
    given(popupWebService.getUpcomingPopupList()).willReturn(List.of());
    given(popupWebService.getSearchPopupList("성수")).willReturn(List.of());
    given(popupWebService.getPopupDetail("popup-detail")).willReturn(detail());

    mockMvc.perform(get("/api/v2/web/popup/random")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/popup/favorite")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/popup/in-progress")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/popup/search").param("q", "성수")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/popup/upcoming")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/popup/popup-detail")).andExpect(status().isOk());

    verifyNoInteractions(jwtProvider, usersRepository);
  }

  @Test
  void invalidBearerIsIgnoredAndHeadRemainsPublic() throws Exception {
    given(popupWebService.getRandomPopupList()).willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v2/web/popup/random")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isOk());
    mockMvc.perform(head("/api/v2/web/popup/random")).andExpect(status().isOk());

    verifyNoInteractions(jwtProvider, usersRepository);
  }

  @Test
  void upcomingUsesTheExactDDayJsonKeyAndCommonEnvelope() throws Exception {
    given(popupWebService.getUpcomingPopupList())
        .willReturn(
            List.of(
                new V2PopupWebUpcomingResponseDto(
                    "popup-upcoming",
                    "예정 팝업",
                    "upcoming.jpg",
                    "서울",
                    LocalDate.of(2026, 8, 7),
                    LocalDate.of(2026, 8, 20),
                    3)));

    mockMvc
        .perform(get("/api/v2/web/popup/upcoming").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.message").value("요청 성공!"))
        .andExpect(jsonPath("$.data[0].popupUuid").value("popup-upcoming"))
        .andExpect(jsonPath("$.data[0].dDay").value(3))
        .andExpect(jsonPath("$.data[0].dday").doesNotExist())
        .andExpect(jsonPath("$.data[0].DDay").doesNotExist());
  }

  @Test
  void optionalFiltersAndTargetUuidArePassedWithoutCallerIdentity() throws Exception {
    given(popupWebService.getInProgressPopupList("서울", "성동구", "MOST_VIEWED")).willReturn(List.of());
    given(popupWebService.getPopupDetail("target-popup")).willReturn(detail());

    mockMvc
        .perform(
            get("/api/v2/web/popup/in-progress")
                .param("region", "서울")
                .param("district", "성동구")
                .param("sort", "MOST_VIEWED"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v2/web/popup/target-popup")).andExpect(status().isOk());

    verify(popupWebService).getInProgressPopupList("서울", "성동구", "MOST_VIEWED");
    verify(popupWebService).getPopupDetail("target-popup");
  }

  @Test
  void allWriteMethodsUnderThePublicWebPathRemainProtected() throws Exception {
    mockMvc.perform(post("/api/v2/web/popup/random")).andExpect(status().isUnauthorized());
    mockMvc.perform(put("/api/v2/web/popup/random")).andExpect(status().isUnauthorized());
    mockMvc.perform(patch("/api/v2/web/popup/random")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/v2/web/popup/random")).andExpect(status().isUnauthorized());
  }

  private V2PopupWebDetailResponseDto detail() {
    return new V2PopupWebDetailResponseDto(
        "popup-detail",
        "상세 팝업",
        LocalDate.of(2026, 8, 1),
        LocalDate.of(2026, 8, 31),
        LocalTime.of(10, 30),
        LocalTime.of(20, 0),
        "지번",
        "도로명",
        "서울",
        "https://instagram.example",
        "요약",
        List.of("image.jpg"),
        List.of("캐릭터"),
        4L,
        8L);
  }
}
