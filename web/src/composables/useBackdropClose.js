/**
 * 弹层遮罩关闭：仅当按下与松开都在遮罩上时才关闭。
 * 避免拖拽进度条到遮罩区域松开时误关弹层。
 */
export function useBackdropClose(onClose) {
  let armed = false

  function onBackdropDown(e) {
    if (e.target === e.currentTarget) armed = true
  }

  function onBackdropUp(e) {
    if (armed && e.target === e.currentTarget) onClose()
    armed = false
  }

  function cancelBackdrop() {
    armed = false
  }

  return { onBackdropDown, onBackdropUp, cancelBackdrop }
}
