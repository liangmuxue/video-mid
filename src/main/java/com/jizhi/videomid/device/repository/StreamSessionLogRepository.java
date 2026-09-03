package com.jizhi.videomid.device.repository;

import com.jizhi.videomid.device.entity.StreamSessionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StreamSessionLogRepository extends JpaRepository<StreamSessionLogEntity, Long> {

    Optional<StreamSessionLogEntity> findBySessionId(String sessionId);
}
