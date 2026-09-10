/**
 * 业务侧码流地址使用 RTMP（如 rtmp://8.130.74.232/live/cam01_sub）。
 * 浏览器不能直连 RTMP，预览时按 ZLMediaKit 规则推导 HTTP 播放地址（默认 HTTP 口 8080）。
 *
 * RTMP  rtmp://host[:1935]/app/stream
 * HLS   http://host:8080/app/stream/hls.m3u8
 * FLV   http://host:8080/app/stream.live.flv
 */
const ZLM_HTTP_PORT = Number(import.meta.env.VITE_ZLM_HTTP_PORT || 8080)

export function parseRtmpUrl(rawUrl) {
  const sourceUrl = String(rawUrl || '').trim()
  if (!sourceUrl.toLowerCase().startsWith('rtmp')) {
    return null
  }
  try {
    const u = new URL(sourceUrl)
    const host = u.hostname || '127.0.0.1'
    const parts = u.pathname.replace(/^\/+|\/+$/g, '').split('/').filter(Boolean)
    if (parts.length < 2) return null
    const stream = parts.pop()
    const app = parts.join('/')
    const httpBase = `http://${host}:${ZLM_HTTP_PORT}`
    return {
      sourceUrl,
      host,
      app,
      stream,
      httpPort: ZLM_HTTP_PORT,
      hlsUrl: `${httpBase}/${app}/${stream}/hls.m3u8`,
      flvUrl: `${httpBase}/${app}/${stream}.live.flv`
    }
  } catch {
    return null
  }
}

export function isRtmp(url) {
  return String(url || '').trim().toLowerCase().startsWith('rtmp')
}

export function isHttpPlayable(url) {
  const s = String(url || '').trim().toLowerCase()
  return s.startsWith('http://') || s.startsWith('https://')
}

export function isHttpFlv(url) {
  return /\.live\.flv(\?|$)/i.test(String(url || '')) || /\.flv(\?|$)/i.test(String(url || ''))
}

/** 将错误指向 80 口的 ZLM 播放地址改到配置的 HTTP 口（常见 nginx:80 / zlm:8080） */
export function normalizeZlmHttpUrl(rawUrl) {
  const s = String(rawUrl || '').trim()
  try {
    const u = new URL(s)
    if (u.protocol !== 'http:') return s
    if (!u.port || u.port === '80') {
      u.port = String(ZLM_HTTP_PORT)
      return u.href
    }
    return s
  } catch {
    return s
  }
}

