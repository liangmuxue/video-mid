#!/usr/bin/env bash
# =============================================================================
# ZLM 演示推流（mock 宇视 / 国标页面用）
#
# 与 mock-devices.json 中 previewKey 一致，推成功后可在浏览器播放：
#   http://<ZLM_HTTP_HOST>:8080/live/<stream>.live.flv
#
# 用法：
#   chmod +x scripts/zlm-demo-push.sh
#   ./scripts/zlm-demo-push.sh              # 启动全部演示流
#   ./scripts/zlm-demo-push.sh stop         # 停止
#   ZLM_HOST=8.130.74.232 ./scripts/zlm-demo-push.sh
#
# 依赖：ffmpeg、ZLM 已开启 RTMP（默认 1935）
# =============================================================================

set -euo pipefail

ZLM_HOST="${ZLM_HOST:-127.0.0.1}"
ZLM_RTMP_PORT="${ZLM_RTMP_PORT:-1935}"
FFMPEG="${FFMPEG:-ffmpeg}"
PID_FILE="${PID_FILE:-/tmp/video-mid-zlm-demo.pids}"
APP="${ZLM_APP:-live}"

# name|label|color(visible/thermal)
STREAMS=(
  "tic7632_01_visible_main|TIC7632-01 可见光主|visible"
  "tic7632_01_visible_sub|TIC7632-01 可见光子|visible"
  "tic7632_01_thermal_main|TIC7632-01 热成像|thermal"
  "tic7632_02_visible_main|TIC7632-02 可见光主|visible"
  "tic7632_02_thermal_main|TIC7632-02 热成像|thermal"
  "cam01_main|东门主码流(旧演示)|visible"
  "cam01_sub|东门子码流(旧演示)|visible"
  "cam02_main|岗卡主码流(旧演示)|visible"
  "cam02_sub|岗卡子码流(旧演示)|visible"
  "cam03_sub|停车场(旧演示)|visible"
)

stop_all() {
  if [[ -f "$PID_FILE" ]]; then
    while read -r pid; do
      [[ -n "$pid" ]] && kill "$pid" 2>/dev/null || true
    done < "$PID_FILE"
    rm -f "$PID_FILE"
    echo "已停止演示推流"
  else
    echo "无运行中的演示推流（$PID_FILE 不存在）"
  fi
}

start_one() {
  local name="$1"
  local label="$2"
  local kind="$3"
  local rtmp="rtmp://${ZLM_HOST}:${ZLM_RTMP_PORT}/${APP}/${name}"
  local vf

  if [[ "$kind" == "thermal" ]]; then
    vf="format=gray,drawtext=text='${label}':fontsize=28:fontcolor=white:x=20:y=20,drawtext=text='MOCK THERMAL':fontsize=18:fontcolor=orange:x=20:y=56"
  else
    vf="drawtext=text='${label}':fontsize=28:fontcolor=white:x=20:y=20,drawtext=text='MOCK VISIBLE':fontsize=18:fontcolor=cyan:x=20:y=56"
  fi

  $FFMPEG -hide_banner -loglevel warning -re \
    -f lavfi -i "testsrc=size=1280x720:rate=25" \
    -f lavfi -i "sine=frequency=440:sample_rate=44100" \
    -vf "$vf" \
    -c:v libx264 -preset ultrafast -tune zerolatency -pix_fmt yuv420p -g 50 \
    -c:a aac -b:a 48k -ar 44100 \
    -f flv "$rtmp" &
  echo $! >> "$PID_FILE"
  echo "  → $name  ($rtmp)"
}

if [[ "${1:-}" == "stop" ]]; then
  stop_all
  exit 0
fi

if [[ "${1:-}" == "status" ]]; then
  if [[ -f "$PID_FILE" ]]; then
    echo "运行中 PID:"
    cat "$PID_FILE"
  else
    echo "未运行"
  fi
  exit 0
fi

command -v "$FFMPEG" >/dev/null || { echo "未找到 ffmpeg"; exit 1; }

stop_all
: > "$PID_FILE"

echo "ZLM RTMP: rtmp://${ZLM_HOST}:${ZLM_RTMP_PORT}/${APP}/<stream>"
echo "HTTP-FLV: http://${ZLM_HOST}:8080/${APP}/<stream>.live.flv"
echo "启动演示推流..."

for row in "${STREAMS[@]}"; do
  IFS='|' read -r name label kind <<< "$row"
  start_one "$name" "$label" "$kind"
done

echo ""
echo "已启动 ${#STREAMS[@]} 路。停止: $0 stop"
echo "验证: curl -I http://${ZLM_HOST}:8080/${APP}/tic7632_01_visible_sub.live.flv"
