package com.jizhi.videomid.device.repository;

import com.jizhi.videomid.device.entity.SipPlatformEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SipPlatformRepository extends JpaRepository<SipPlatformEntity, Long> {

    Optional<SipPlatformEntity> findByPlatformId(String platformId);

    List<SipPlatformEntity> findByStatus(String status);
}
