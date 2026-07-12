package com.poppang.be.domain.popup.presentation.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poppang.be.domain.popup.application.PopupSubmissionService;
import com.poppang.be.domain.popup.dto.app.request.PopupSubmissionCreateRequestDto;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class PopupSubmissionControllerTest {

  @Mock private PopupSubmissionService popupSubmissionService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    ObjectMapper strictObjectMapper =
        Jackson2ObjectMapperBuilder.json()
            .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new PopupSubmissionController(popupSubmissionService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(strictObjectMapper))
            .build();
  }

  @Test
  void createPopupSubmissionIgnoresOnlyLegacyImageListAndPreservesMultipartImageOrder()
      throws Exception {
    MockMultipartFile request =
        jsonRequestPart(
            """
            {
              "userUuid": "11111111-1111-1111-1111-111111111111",
              "imageList": [
                {"imageUrl": "https://legacy.example/image.jpg", "sortOrder": 9}
              ]
            }
            """);
    MockMultipartFile firstImage = imagePart("first.jpg", "first");
    MockMultipartFile secondImage = imagePart("second.jpg", "second");

    mockMvc
        .perform(
            multipart("/api/v1/popup-submissions").file(request).file(firstImage).file(secondImage))
        .andExpect(status().isOk());

    ArgumentCaptor<PopupSubmissionCreateRequestDto> requestCaptor =
        ArgumentCaptor.forClass(PopupSubmissionCreateRequestDto.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MultipartFile>> imagesCaptor = ArgumentCaptor.forClass(List.class);
    verify(popupSubmissionService)
        .createPopupSubmission(requestCaptor.capture(), imagesCaptor.capture());

    assertThat(requestCaptor.getValue().getUserUuid())
        .isEqualTo("11111111-1111-1111-1111-111111111111");
    assertThat(imagesCaptor.getValue())
        .extracting(MultipartFile::getOriginalFilename)
        .containsExactly("first.jpg", "second.jpg");
  }

  @Test
  void createPopupSubmissionRejectsUnregisteredUnknownRequestProperty() throws Exception {
    MockMultipartFile request =
        jsonRequestPart(
            """
            {
              "userUuid": "11111111-1111-1111-1111-111111111111",
              "unexpectedField": "must fail"
            }
            """);

    mockMvc
        .perform(
            multipart("/api/v1/popup-submissions")
                .file(request)
                .file(imagePart("popup.jpg", "image")))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(popupSubmissionService);
  }

  @Test
  void modelConverterSchemaDoesNotExposeLegacyImageList() {
    ResolvedSchema resolvedSchema =
        ModelConverters.getInstance()
            .resolveAsResolvedSchema(
                new AnnotatedType(PopupSubmissionCreateRequestDto.class).resolveAsRef(true));

    assertThat(resolvedSchema.schema.get$ref()).isNotBlank();
    Schema<?> requestSchema = resolveSchemaReference(resolvedSchema);

    assertThat(requestSchema).isNotNull();
    assertThat(requestSchema.getProperties())
        .containsKeys("userUuid", "recommendIdList")
        .doesNotContainKey("imageList");
  }

  private Schema<?> resolveSchemaReference(ResolvedSchema resolvedSchema) {
    Schema<?> schema = resolvedSchema.schema;
    if (schema == null || schema.get$ref() == null) {
      return schema;
    }

    String reference = schema.get$ref();
    String schemaName = reference.substring(reference.lastIndexOf('/') + 1);
    return resolvedSchema.referencedSchemas.get(schemaName);
  }

  private MockMultipartFile jsonRequestPart(String json) {
    return new MockMultipartFile(
        "request",
        "request.json",
        MediaType.APPLICATION_JSON_VALUE,
        json.getBytes(StandardCharsets.UTF_8));
  }

  private MockMultipartFile imagePart(String filename, String content) {
    return new MockMultipartFile(
        "images", filename, MediaType.IMAGE_JPEG_VALUE, content.getBytes(StandardCharsets.UTF_8));
  }
}
