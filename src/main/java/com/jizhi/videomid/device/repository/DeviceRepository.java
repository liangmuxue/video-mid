package com.jizhi.videomid.device.repository;

import com.jizhi.videomid.device.entity.DeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    Optional<DeviceEntity> findByDeviceId(String deviceId);

    List<DeviceEntity> findAllByOrderByDeviceIdAsc();

    List<DeviceEntity> findByPlatformId(String platformId);

    List<DeviceEntity> findByParentId(String parentId);
}
