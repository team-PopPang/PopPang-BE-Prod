package com.poppang.be.common.util;

import java.text.Normalizer;
import org.springframework.util.StringUtils;

public final class StringNormalizer {

  private StringNormalizer() {}

  // 모든 문자열 공통 정규화 (공백·유니코드 등)
  public static String normalizeBasic(String input) {
    if (!StringUtils.hasText(input)) return null;
    String s = Normalizer.normalize(input, Normalizer.Form.NFC);
    return s.trim();
  }

  // 지역 이름 정규화
  public static String normalizeRegion(String region) {
    String s = normalizeBasic(region);
    if (s == null) return null;
    if ("전체".equals(s) || "all".equalsIgnoreCase(s)) return null;

    s = s.replaceAll("[^가-힣0-9]", "");

    // 예: 서울특별시 → 서울, 부산광역시 → 부산, 세종특별자치시 → 세종
    s = s.replaceAll("(특별|광역|자치)?시$", "");

    // 흔한 표기 표준화
    if (s.equals("서울특별") || s.equals("서울시")) s = "서울";
    if (s.equals("부산광역")) s = "부산";
    if (s.equals("세종특별자치")) s = "세종";

    return s;
  }

  // 구 이름 정규화
  public static String normalizeDistrict(String district) {
    String s = normalizeBasic(district);
    if (s == null) return null;
    if ("전체".equals(s) || "all".equalsIgnoreCase(s)) return null;
    // 특수문자 제거 + “구” 자동 보정
    s = s.replaceAll("[^가-힣0-9]", "");
    if (!s.endsWith("구") && s.length() <= 4) {
      s += "구";
    }
    return s;
  }
}
