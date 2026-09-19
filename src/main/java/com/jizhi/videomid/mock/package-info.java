/**
 * 开发期模拟夹具，与正式业务隔离。
 * <p>
 * 上线删除范围：本包、{@code uniview.mock}、{@code gb28181.mock}、{@code sip.mock}、
 * {@code resources/mock/}、{@code sql/init.sql} 中 mock 数据段、{@code web/src/mock/}、
 * {@code scripts/zlm-demo-push.*}。
 * 业务表结构、{@code Uniview*Port}、{@code Gb28181*Port}、设备/预览接口不要依赖本包。
 */
package com.jizhi.videomid.mock;
