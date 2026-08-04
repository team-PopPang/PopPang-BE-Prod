package com.poppang.be.domain.recommend.presentation.v2;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.poppang.be.common.jwt.JwtProvider;
import com.poppang.be.common.ratelimit.V2AuthRateLimiter;
import com.poppang.be.common.security.SecurityConfig;
import com.poppang.be.domain.recommend.application.V2WebRecommendService;
import com.poppang.be.domain.recommend.dto.v2.V2WebRecommendResponseDto;
import com.poppang.be.domain.users.infrastructure.UsersRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = V2WebRecommendController.class,
    properties = {
      "spring.config.location=classpath:/application-test.yml",
      "springdoc.api-docs.enabled=false",
      "springdoc.swagger-ui.enabled=false",
      "internal.worker.api-key=${random.uuid}${random.uuid}"
    })
@Import(SecurityConfig.class)
class V2WebRecommendControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private V2WebRecommendService recommendService;
  @MockitoBean private JwtProvider jwtProvider;
  @MockitoBean private UsersRepository usersRepository;
  @MockitoBean private V2AuthRateLimiter authRateLimiter;

  @Test
  void getAndHeadArePublicWithoutAuthenticationAndKeepTheCommonEnvelope() throws Exception {
    given(recommendService.getAllRecommendList())
        .willReturn(List.of(new V2WebRecommendResponseDto(1L, "전시")));

    mockMvc
        .perform(get("/api/v2/web/recommend"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data[0].id").value(1))
        .andExpect(jsonPath("$.data[0].recommendName").value("전시"));
    mockMvc.perform(head("/api/v2/web/recommend")).andExpect(status().isOk());

    verifyNoInteractions(jwtProvider, usersRepository);
  }

  @Test
  void invalidBearerIsIgnoredForThePublicGet() throws Exception {
    given(recommendService.getAllRecommendList()).willReturn(List.of());

    mockMvc
        .perform(
            get("/api/v2/web/recommend").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
        .andExpect(status().isOk());

    verifyNoInteractions(jwtProvider, usersRepository);
  }

  @Test
  void allWriteMethodsRemainProtected() throws Exception {
    mockMvc.perform(post("/api/v2/web/recommend")).andExpect(status().isUnauthorized());
    mockMvc.perform(put("/api/v2/web/recommend")).andExpect(status().isUnauthorized());
    mockMvc.perform(patch("/api/v2/web/recommend")).andExpect(status().isUnauthorized());
    mockMvc.perform(delete("/api/v2/web/recommend")).andExpect(status().isUnauthorized());
  }
}
