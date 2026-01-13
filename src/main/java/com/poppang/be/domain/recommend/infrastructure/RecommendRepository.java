package com.poppang.be.domain.recommend.infrastructure;

import com.poppang.be.domain.recommend.entity.Recommend;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendRepository extends JpaRepository<Recommend, Long> {

  List<Recommend> findAllByIdIn(List<Integer> featuredRecommendIds);
}
