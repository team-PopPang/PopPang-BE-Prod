package com.poppang.be.common.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.domain.favorite.application.UserFavoriteService;
import com.poppang.be.domain.favorite.presentation.UserFavoriteController;
import com.poppang.be.domain.popup.application.PopupAdminService;
import com.poppang.be.domain.popup.application.PopupService;
import com.poppang.be.domain.popup.presentation.app.PopupAdminController;
import com.poppang.be.domain.popup.presentation.app.PopupController;
import com.poppang.be.domain.users.application.UsersService;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import com.poppang.be.domain.users.presentation.UsersController;
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
    controllers = {
      UsersController.class,
      UserFavoriteController.class,
      PopupAdminController.class,
      PopupController.class
    },
    properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class V1SecurityCompatibilityTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UsersService usersService;
  @MockitoBean private UserFavoriteService userFavoriteService;
  @MockitoBean private PopupAdminService popupAdminService;
  @MockitoBean private PopupService popupService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;

  @Test
  void legacyUserReadRemainsPublicWithoutBearerToken() throws Exception {
    mockMvc.perform(get("/api/v1/user/legacy-user")).andExpect(status().isOk());

    verify(usersService).getUserInfo("legacy-user");
  }

  @Test
  void legacyFavoriteWriteRemainsPublicWithoutBearerToken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/favorite")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userUuid": "legacy-user",
                      "popupUuid": "legacy-popup"
                    }
                    """))
        .andExpect(status().isOk());

    verify(userFavoriteService).registerFavorite(any());
  }

  @Test
  void legacyAdminWriteRemainsPublicWithoutBearerToken() throws Exception {
    mockMvc
        .perform(patch("/api/v1/admin/popup/legacy-popup/deactivate"))
        .andExpect(status().isOk());

    verify(popupAdminService).deactivatePopup(null, "legacy-popup");
  }

  @Test
  void legacyWorkerWriteRemainsPublicWithoutBearerToken() throws Exception {
    mockMvc
        .perform(post("/api/v1/popup").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isOk());

    verify(popupService).registerPopup(any());
  }
}
