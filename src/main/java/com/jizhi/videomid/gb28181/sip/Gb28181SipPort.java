package com.jizhi.videomid.gb28181.sip;

import java.util.Map;

/** 国标 SIP 上级生命周期（live 模式实现真实监听） */
public interface Gb28181SipPort {

    Map<String, Object> status();

    void ensureStarted();
}
