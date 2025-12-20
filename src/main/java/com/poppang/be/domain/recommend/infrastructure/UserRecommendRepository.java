package com.poppang.be.domain.recommend.infrastructure;

import com.poppang.be.domain.recommend.entity.UserRecommend;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRecommendRepository extends JpaRepository<UserRecommend, Long> {

  List<UserRecommend> findAllByUser_Uuid(String userUuid);
}
