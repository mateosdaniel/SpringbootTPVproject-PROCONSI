package com.proconsi.electrobazar.repository;

import com.proconsi.electrobazar.model.UserFavoriteProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFavoriteProductRepository extends JpaRepository<UserFavoriteProduct, Long> {

    @Query("SELECT u.productId FROM UserFavoriteProduct u WHERE u.userId = :userId")
    List<Long> findProductIdsByUserId(Long userId);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
