package com.poppang.be.domain.popup.presentation.app;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poppang.be.domain.popup.application.PopupUserService;
import com.poppang.be.domain.popup.dto.app.response.PopupScrollItemResponseDto;
import com.poppang.be.domain.popup.dto.app.response.PopupScrollResponseDto;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class PopupUserControllerScrollTest {

  @Mock private PopupUserService popupUserService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PopupUserController(popupUserService))
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    Jackson2ObjectMapperBuilder.json()
                        .modulesToInstall(new JavaTimeModule())
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
            .build();
  }

  @Test
  void getScrollPopupListReturnsMvpCursorContract() throws Exception {
    PopupScrollItemResponseDto item =
        new PopupScrollItemResponseDto(
            "popup-uuid",
            "/images/thumbnail.jpg",
            "서울",
            "성수 팝업",
            LocalDate.of(2026, 8, 1),
            LocalDate.of(2026, 8, 15),
            true);
    when(popupUserService.getScrollPopupList("user-uuid", 30L))
        .thenReturn(new PopupScrollResponseDto(List.of(item), 20L, true));

    mockMvc
        .perform(
            get("/api/v1/users/{userUuid}/popups/scroll", "user-uuid").queryParam("cursor", "30"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.items[0].popupUuid").value("popup-uuid"))
        .andExpect(jsonPath("$.items[0].thumbnailUrl").value("/images/thumbnail.jpg"))
        .andExpect(jsonPath("$.items[0].region").value("서울"))
        .andExpect(jsonPath("$.items[0].name").value("성수 팝업"))
        .andExpect(jsonPath("$.items[0].startDate").value("2026-08-01"))
        .andExpect(jsonPath("$.items[0].endDate").value("2026-08-15"))
        .andExpect(jsonPath("$.items[0].isFavorited").value(true))
        .andExpect(jsonPath("$.items[0].favorited").doesNotExist())
        .andExpect(jsonPath("$.nextCursor").value(20))
        .andExpect(jsonPath("$.hasNext").value(true));

    verify(popupUserService).getScrollPopupList("user-uuid", 30L);
  }

  @Test
  void getScrollPopupListPassesNullCursorOnFirstRequest() throws Exception {
    when(popupUserService.getScrollPopupList("user-uuid", null))
        .thenReturn(new PopupScrollResponseDto(List.of(), null, false));

    mockMvc
        .perform(get("/api/v1/users/{userUuid}/popups/scroll", "user-uuid"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.hasNext").value(false));

    verify(popupUserService).getScrollPopupList("user-uuid", null);
  }
}
