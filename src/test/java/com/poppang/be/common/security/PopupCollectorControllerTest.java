package com.poppang.be.common.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.exception.GlobalExceptionHandler;
import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.domain.popup.application.PopupAlertTargetService;
import com.poppang.be.domain.popup.application.PopupService;
import com.poppang.be.domain.popup.dto.app.response.PopupRegisterResponseDto;
import com.poppang.be.domain.popup.presentation.app.PopupAlertTargetController;
import com.poppang.be.domain.popup.presentation.app.PopupController;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = {PopupController.class, PopupAlertTargetController.class},
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "internal.worker.api-key=collector-controller-test-key-only"
    })
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PopupCollectorControllerTest {
  @Autowired private MockMvc mockMvc;
  @MockitoBean private PopupService popupService;
  @MockitoBean private PopupAlertTargetService alertTargetService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @Test
  void registrationKeepsAnonymousAccessAndReturnsWrappedUuidAndCreated() throws Exception {
    when(popupService.registerPopup(any()))
        .thenReturn(new PopupRegisterResponseDto("saved-uuid", true));
    mockMvc
        .perform(
            post("/api/v1/popup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"팝업\",\"instaPostId\":\"123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.popupUuid").value("saved-uuid"))
        .andExpect(jsonPath("$.data.created").value(true));
  }

  @Test
  void duplicateRegistrationReturnsCreatedFalse() throws Exception {
    when(popupService.registerPopup(any()))
        .thenReturn(new PopupRegisterResponseDto("existing", false));
    mockMvc
        .perform(post("/api/v1/popup").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.popupUuid").value("existing"))
        .andExpect(jsonPath("$.data.created").value(false));
  }

  @Test
  void targetsRequireWorkerKeyBeforeCallingBusinessLogic() throws Exception {
    for (String key : List.of("", "wrong-key")) {
      mockMvc
          .perform(
              post("/api/v1/popup/alert-targets")
                  .header("X-Worker-Api-Key", key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"popupUuids\":[]}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value(5009));
    }
    mockMvc
        .perform(
            post("/api/v1/popup/alert-targets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"popupUuids\":[]}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(alertTargetService);
  }

  @Test
  void targetsAllowWorkerKeyAndReturnEmptyArrayWhenNoMatches() throws Exception {
    when(alertTargetService.findTargets(any())).thenReturn(List.of());
    mockMvc
        .perform(
            post("/api/v1/popup/alert-targets")
                .header("X-Worker-Api-Key", "collector-controller-test-key-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"popupUuids\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void bearerOrQueryKeyCannotExposeTargets() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/popup/alert-targets")
                .header("Authorization", "Bearer arbitrary-token")
                .queryParam("X-Worker-Api-Key", "collector-controller-test-key-only")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"popupUuids\":[]}"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(jwtProvider, alertTargetService);
  }
}
