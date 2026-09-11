/**
 * 按需拉流：只加载「当前正在播放」的那一个时段视频。
 * 禁止一次性预取/下载全天录像文件。
 *
 * 说明：时间轴元数据（文件名/时间戳列表）仍可按天查询；
 * 真正的视频字节仅在 play/seek 到对应时段时才请求。
 */

/**
 * 卸载当前视频，释放网络与缓冲，避免堆积。
 * @param {HTMLVideoElement | null | undefined} video
 */
export function unloadVideo(video) {
  if (!video) return
  try {
    video.pause()
  } catch (_) {
    /* ignore */
  }
  try {
    video.removeAttribute('src')
    video.load()
  } catch (_) {
    /* ignore */
  }
}

/**
 * 仅加载并播放单个 URL（按需）。
 * @param {HTMLVideoElement} video
 * @param {{ url: string, seekSeconds?: number }} opts
 * @returns {Promise<void>}
 */
export function loadAndPlayOne(video, { url, seekSeconds = 0 }) {
  if (!video || !url) return Promise.resolve()

  // 同一文件内拖拽：只改 currentTime，避免反复 unload/reload
  const attrSrc = video.getAttribute('src') || ''
  const sameFile =
    !!attrSrc &&
    (attrSrc === url ||
      video.currentSrc === url ||
      video.src === url ||
      (typeof window !== 'undefined' &&
        (() => {
          try {
            const abs = new URL(url, window.location.href).href
            return video.currentSrc === abs || video.src === abs
          } catch {
            return false
          }
        })()))

  if (sameFile && video.readyState >= 1) {
    return new Promise((resolve) => {
      try {
        const t = Number.isFinite(seekSeconds) ? Math.max(0, seekSeconds) : 0
        video.currentTime = t
        video.play().then(() => resolve()).catch(() => resolve())
      } catch (_) {
        resolve()
      }
    })
  }

  unloadVideo(video)

  return new Promise((resolve, reject) => {
    let settled = false
    const done = (err) => {
      if (settled) return
      settled = true
      video.removeEventListener('error', onError)
      if (err) reject(err)
      else resolve()
    }
    const onError = () => done(new Error('视频加载失败'))

    video.preload = 'none'
    video.addEventListener('error', onError, { once: true })

    const applySeekAndPlay = () => {
      try {
        if (seekSeconds > 0 && Number.isFinite(seekSeconds)) {
          video.currentTime = seekSeconds
        }
        video.play().then(() => done()).catch(() => done())
      } catch (e) {
        done(e)
      }
    }

    video.src = url
    // 显式 load，开始拉「这一段」
    try {
      video.load()
    } catch (_) {
      /* ignore */
    }

    if (video.readyState >= 1) {
      applySeekAndPlay()
    } else {
      video.addEventListener('loadedmetadata', applySeekAndPlay, { once: true })
    }
  })
}

/**
 * 是否允许预取下一段：按需模式下恒为 false（仅 ended 后再请求下一段）。
 */
export function shouldPrefetchNext() {
  return false
}
