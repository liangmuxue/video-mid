# SQL 脚本说明

## 常用（按场景）

| 场景 | 脚本 |
|------|------|
| **已有库，重建设备/目录/码流，保留 admin 用户** | `rebuild_business_tables.sql` |
| 全新空库（仅建表，不含演示数据） | `../src/main/resources/init.sql` |
| 全新空库（建表 + 演示数据） | `init.sql` 后执行 `rebuild_business_tables.sql` |
| 旧库 status 从中文/VARCHAR 迁到 INT | `device_status_migrate.sql` |
| sys_user 时间字段迁到毫秒时间戳 | `migrate_sys_user_timestamp.sql` |

## 表结构约定

| 表 | 说明 |
|----|------|
| `sys_user` | 登录用户；时间字段为 **BIGINT 毫秒时间戳** |
| `device_folder` | 设备目录树；`created_at` / `updated_at` 为毫秒时间戳 |
| `device` | 设备；`status` **INT**：0=不可用 1=已启用 2=已停用 |
| `device_stream` | 码流；`live_enabled` = 业务端直播（默认 sub） |

## 演示数据（rebuild_business_tables.sql）

**目录**

- 园区 → 东门区域、地下车库

**设备**

- CAM_EAST_01 东门球机（status=1 已启用）
- CAM_GATE_02 岗卡枪机（status=1 已启用）
- CAM_PARK_03 停车场半球（status=2 已停用，业务端灰色不可播）

**码流**

- 每设备 main + sub（停车场仅 sub）
- 默认 `live_enabled=1` 在 sub 码流

## 已废弃 / 仅补丁

- `device_folder.sql`、`device_stream_live_enabled.sql`、`patch_biz_portal_*.sql`：旧库增量补丁，新环境直接用 `rebuild_business_tables.sql`
