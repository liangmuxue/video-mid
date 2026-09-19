package com.jizhi.videomid.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * mock Catalog 对账规则：库里的业务设备（CAM_* 等）不参与「多余设备」判定。
 * live 模式本 Bean 不注册，对账走全量 ID。
 */
@Component
@ConditionalOnExpression(MockSupport.FIXTURES_ENABLED)
public class MockCatalogSyncRules {

    public boolean countDbDeviceAsExtra(String deviceId) {
        return MockSupport.isFixtureDeviceId(deviceId);
    }
}
