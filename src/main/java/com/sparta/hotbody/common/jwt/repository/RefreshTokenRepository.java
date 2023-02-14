package com.sparta.hotbody.common.jwt.repository;

import com.sparta.hotbody.common.jwt.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  Optional<String> findByRefreshToken(String token);
}