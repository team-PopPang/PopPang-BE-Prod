package com.poppang.be.domain.popup.dto.app.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegionDistrictsResponse {

  private String region;
  private List<String> districtList;

  @Builder
  public RegionDistrictsResponse(String region, List<String> districtList) {
    this.region = region;
    this.districtList = districtList;
  }
}
