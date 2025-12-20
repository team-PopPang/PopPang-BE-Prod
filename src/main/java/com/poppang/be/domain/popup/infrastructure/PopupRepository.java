package com.poppang.be.domain.popup.infrastructure;

import com.poppang.be.domain.popup.entity.Popup;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PopupRepository extends JpaRepository<Popup, Long> {

  Optional<Popup> findByUuid(String popupUuid);

  @Query(
      """
            select p
            from Popup p
            where p.activated = true
              and p.startDate <= CURRENT_DATE
              and p.endDate >= CURRENT_DATE
              and (
                    lower(p.name) like lower(concat('%', :q, '%'))
                    or lower(p.captionSummary) like lower(concat('%', :q, '%'))
                  )
            """)
  List<Popup> searchActivatedByKeyword(@Param("q") String q);

  List<Popup> findByActivatedTrueAndStartDateBetween(LocalDate startDate, LocalDate endDate);

  @Query(
      """
            select p
            from Popup p
            where p.activated = true
            and p.startDate <= CURRENT_DATE
            and p.endDate >= CURRENT_DATE
            order by p.startDate asc
             """)
  List<Popup> findInProgressPopupList();

  @Query(
      value =
          """
            SELECT
              p.*,
              (6371 * ACOS(
                 COS(RADIANS(:lat)) * COS(RADIANS(p.latitude)) *
                 COS(RADIANS(p.longitude) - RADIANS(:lng)) +
                 SIN(RADIANS(:lat)) * SIN(RADIANS(p.latitude))
              )) AS distance_km
            FROM popup p
            WHERE p.is_active = 1
              AND p.start_date <= CURRENT_DATE
              AND p.end_date >= CURRENT_DATE
              AND (:region IS NULL OR SUBSTRING_INDEX(p.road_address, ' ', 1) = :region)
              AND (:district IS NULL OR p.road_address LIKE CONCAT('%', :district, '%'))
            ORDER BY distance_km ASC, p.created_at DESC
            """,
      nativeQuery = true)
  List<Popup> findActiveByClosest(
      @Param("region") String region,
      @Param("district") String district,
      @Param("lat") Double latitude,
      @Param("lng") Double longitude);

  @Query(
      value =
          """
            SELECT *
            FROM popup p
            WHERE p.is_active = 1
              AND p.start_date <= CURRENT_DATE
              AND p.end_date >= CURRENT_DATE
              AND (:excludeSize = 0 OR p.id NOT IN (:excludeIds))
            ORDER BY RAND()
            LIMIT :limit
            """,
      nativeQuery = true)
  List<Popup> findRandomActivePopupsExcluding(
      @Param("excludeIds") List<Long> excludeIds,
      @Param("excludeSize") int excludeSize,
      @Param("limit") int limit);

  interface RegionDistrictsRaw {
    String getRegion();

    String getDistricts();
  }

  @Query(
      """
            select p.uuid as uuid
            from Popup p
            where p.id in :ids
            """)
  List<String> findAllUuidByIdIn(@Param("ids") List<Long> ids);

  @Query(
      value =
          """
            SELECT
                region,
                CONCAT(
                    '[',
                    GROUP_CONCAT(
                        DISTINCT CONCAT('"', district, '"')
                        ORDER BY
                            CASE
                                WHEN district = '전체' THEN 0
                                ELSE 1
                            END,
                            district
                        SEPARATOR ','
                    ),
                    ']'
                ) AS districts
            FROM (
                -- 1) 전체(All) region: 활성 팝업이 하나라도 있을 때만 추가
                SELECT '전체' AS region, '전체' AS district
                FROM dual
                WHERE EXISTS (
                    SELECT 1
                    FROM popup
                    WHERE start_date <= CURRENT_DATE
                      AND end_date >= CURRENT_DATE
                )

                UNION ALL

                -- 2) 서울 전체: 서울에 활성 팝업이 있을 때만 '전체' 추가
                SELECT '서울' AS region, '전체' AS district
                FROM dual
                WHERE EXISTS (
                    SELECT 1
                    FROM popup
                    WHERE SUBSTRING_INDEX(road_address, ' ', 1) = '서울'
                      AND start_date <= CURRENT_DATE
                      AND end_date >= CURRENT_DATE
                )

                UNION ALL

                -- 3) 서울의 구 리스트 (마포구, 강남구 등) - 활성 팝업 기준
                SELECT
                    '서울' AS region,
                    CONCAT(
                        SUBSTRING_INDEX(SUBSTRING_INDEX(road_address, '구', 1), ' ', -1),
                        '구'
                    ) AS district
                FROM popup
                WHERE road_address LIKE '%구%'
                  AND SUBSTRING_INDEX(road_address, ' ', 1) = '서울'
                  AND start_date <= CURRENT_DATE
                  AND end_date >= CURRENT_DATE

                UNION ALL

                -- 4) 서울 이외 지역: 활성 팝업이 있는 "시"마다 '전체' 하나씩
                --   예) 부산 기장군, 부산 해운대구 → region='부산', district='전체' 한 줄
                --       대전 기장읍 → region='대전', district='전체' 한 줄
                SELECT DISTINCT
                    SUBSTRING_INDEX(road_address, ' ', 1) AS region,
                    '전체' AS district
                FROM popup
                WHERE SUBSTRING_INDEX(road_address, ' ', 1) <> '서울'
                  AND start_date <= CURRENT_DATE
                  AND end_date >= CURRENT_DATE
            ) t
            WHERE district <> '구'
            GROUP BY region
            ORDER BY
                CASE
                    WHEN region = '전체' THEN 0
                    ELSE 1
                END,
                region
            """,
      nativeQuery = true)
  List<RegionDistrictsRaw> findRegionDistrictsJson();

  @Query(
      value =
          """
            SELECT p.*, COALESCE(f.cnt, 0) AS likes
            FROM popup p
            LEFT JOIN (
                SELECT popup_id, COUNT(*) AS cnt
                FROM user_favorite
                GROUP BY popup_id
            ) f ON f.popup_id = p.id
            WHERE (:region IS NULL OR SUBSTRING_INDEX(p.road_address, ' ', 1) = :region)
              AND (:district IS NULL OR p.road_address LIKE CONCAT('%', :district, '%'))
            ORDER BY likes DESC, p.created_at DESC
            """,
      nativeQuery = true)
  List<Popup> findPopupListByRegionAndLikes(
      @Param("region") String region, @Param("district") String district);

  @Query(
      value =
          """
            SELECT
                p.*,
                (6371 * ACOS(
                    COS(RADIANS(:latitude)) * COS(RADIANS(p.latitude))
                    * COS(RADIANS(p.longitude) - RADIANS(:longitude))
                    + SIN(RADIANS(:latitude)) * SIN(RADIANS(p.latitude))
                )) AS distance
            FROM popup p
            WHERE (:region IS NULL OR SUBSTRING_INDEX(p.road_address, ' ', 1) = :region)
              AND (:district IS NULL OR p.road_address LIKE CONCAT('%', :district, '%'))
            ORDER BY distance ASC, p.created_at DESC
            """,
      nativeQuery = true)
  List<Popup> findPopupListByRegionAndDistance(
      String region, String district, Double latitude, Double longitude);

  @Query(
      """
            SELECT p
            FROM Popup p
            WHERE p.activated = true
              AND p.startDate <= CURRENT_DATE
              AND p.endDate >= CURRENT_DATE
              AND (:region IS NULL OR SUBSTRING_INDEX(p.roadAddress, ' ', 1) = :region)
              AND (:district IS NULL OR p.roadAddress LIKE CONCAT('%', :district, '%'))
            ORDER BY p.startDate DESC
            """)
  List<Popup> findActiveByNewest(
      @Param("region") String region, @Param("district") String district);

  @Query(
      value =
          """
            SELECT p.*
            FROM popup p
            WHERE p.is_active = 1
              AND p.start_date <= CURRENT_DATE
              AND p.end_date >= CURRENT_DATE
              AND (:region IS NULL OR SUBSTRING_INDEX(p.road_address, ' ', 1) = :region)
              AND (:district IS NULL OR p.road_address LIKE CONCAT('%', :district, '%'))
            ORDER BY p.end_date ASC, p.start_date ASC
            """,
      nativeQuery = true)
  List<Popup> findActiveByClosingSoon(
      @Param("region") String region, @Param("district") String district);

  @Query(
      value =
          """
            SELECT p.*
            FROM popup p
            LEFT JOIN (
                SELECT popup_id, COUNT(*) AS fav_cnt
                FROM user_favorite
                GROUP BY popup_id
            ) uf ON uf.popup_id = p.id
            WHERE p.is_active = 1
              AND p.start_date <= CURRENT_DATE
              AND p.end_date >= CURRENT_DATE
              AND (:region IS NULL OR SUBSTRING_INDEX(p.road_address, ' ', 1) = :region)
              AND (:district IS NULL OR p.road_address LIKE CONCAT('%', :district, '%'))
            ORDER BY COALESCE(uf.fav_cnt, 0) DESC, p.created_at DESC
            """,
      nativeQuery = true)
  List<Popup> findActiveByMostFavorited(
      @Param("region") String region, @Param("district") String district);

  @Query(
      value =
          """
            SELECT p.*
            FROM popup p
            LEFT JOIN popup_total_view_count v
                ON v.popup_uuid = p.uuid
            WHERE p.is_active = 1
              AND p.start_date <= CURRENT_DATE
              AND p.end_date >= CURRENT_DATE
              AND (:region IS NULL OR SUBSTRING_INDEX(p.road_address, ' ', 1) = :region)
              AND (:district IS NULL OR p.road_address LIKE CONCAT('%', :district, '%'))
            ORDER BY COALESCE(v.view_count, 0) DESC, p.created_at DESC
            """,
      nativeQuery = true)
  List<Popup> findActiveByMostViewed(
      @Param("region") String region, @Param("district") String district);

  @Query(
      value =
          """
                SELECT *
                FROM popup p
                WHERE p.is_active = 1
                AND p.start_date <= CURRENT_DATE
                AND p.end_date >= CURRENT_DATE
                ORDER BY RAND()
                LIMIT 10
            """,
      nativeQuery = true)
  List<Popup> findRandomActivePopups();
}
