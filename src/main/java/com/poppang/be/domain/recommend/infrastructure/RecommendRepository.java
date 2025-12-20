package com.poppang.be.domain.recommend.infrastructure;

import com.poppang.be.domain.recommend.entity.Recommend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecommendRepository extends JpaRepository<Recommend, Long> {}
