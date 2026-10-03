package com.jizhi.videomid.uniview.nvr;

import com.jizhi.videomid.config.ZlmProperties;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.media.ZlmClient;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用录像设备的 ONVIF 账号查询通道和历史录像。
 */
@Service
public class NvrRecordingService {
    private static final Logger log = LoggerFactory.getLogger(NvrRecordingService.class);
    private static final String PLAYBACK_APP = "playback";
    private static final int TCP = 1;
    /** NETDEV_DOWNLOAD_SPEED_EIGHT，按时间下载用 8 倍速，避免按实时速度等完整段。 */
    private static final int DOWNLOAD_SPEED_EIGHT = 3;
    private static final int MEDIA_FILE_MP4 = 0;
    private static final int PLAY_CTRL_GETPLAYTIME = 3;

    private final NvrProperties properties;
    private final RecordDeviceRepository recordDeviceRepository;
    private final ZlmClient zlmClient;
    private final ZlmProperties zlmProperties;
    private final ConcurrentHashMap<String, Pointer> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Pointer, Boolean> channelReady = new ConcurrentHashMap<>();

    public NvrRecordingService(NvrProperties properties,
                               RecordDeviceRepository recordDeviceRepository,
                               ZlmClient zlmClient,
                               ZlmProperties zlmProperties) {
        this.properties = properties;
        this.recordDeviceRepository = recordDeviceRepository;
        this.zlmClient = zlmClient;
        this.zlmProperties = zlmProperties;
    }

    public boolean isBound(Device device) {
        return device != null && device.getRecordDeviceId() != null && device.getRecordChannel() != null;
    }

    /** 用内网 IP 在录像设备上匹配唯一通道。0 路或多路都拒绝。 */
    public LanChannel matchLanIp(RecordDevice recorder, String lanIp) {
        String expected = normalizeIp(lanIp);
        Pointer user = login(recorder);
        NvrSdk.ChannelInfo[] list = loadChannels(user, recorder.getHost() + ":" + recorder.getPort());
        int n = list.length;
        LanChannel found = null;
        for (int i = 0; i < n; i++) {
            String ip = text(list[i].szIPAddr);
            if (!expected.equals(ip)) {
                continue;
            }
            if (found != null) {
                throw new IllegalArgumentException("内网 IP " + expected + " 在录像设备上对应了多个通道");
            }
            found = new LanChannel(list[i].dwChannelID, text(list[i].szChnName));
        }
        if (found == null) {
            throw new IllegalArgumentException("内网 IP " + expected + " 在录像设备上没有对应通道");
        }
        return found;
    }

    public List<String> listRecordingDays(Device device, int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month 必须在 1~12");
        }
        if (year < 1970 || year > 2100) {
            throw new IllegalArgumentException("year 非法");
        }
        Pointer user = login(requireRecorder(device));
        NvrSdk.MonthInfo info = new NvrSdk.MonthInfo();
        info.udwYear = year;
        info.udwMonth = month;
        NvrSdk.MonthStatus status = new NvrSdk.MonthStatus();
        NvrSdk.LibraryApi api = api();
        log.info("[宇视NVR] QuickSearch 请求 deviceId={} channel={} year={} month={}",
                device.getDeviceId(), device.getRecordChannel(), year, month);
        if (!api.NETDEV_QuickSearch(user, device.getRecordChannel(), info, status)) {
            int err = api.NETDEV_GetLastError();
            log.warn("[宇视NVR] QuickSearch 响应失败 deviceId={} error={}", device.getDeviceId(), err);
            if (err == NvrSdk.NO_RECORDING) {
                return List.of();
            }
            throw new IllegalStateException("查询录像日期失败，错误码 " + err);
        }
        status.read();
        log.info("[宇视NVR] QuickSearch 响应 deviceId={} dayNum={} status={}",
                device.getDeviceId(), status.udwDayNumInMonth, java.util.Arrays.toString(status.szVideoStatus));
        int days = status.udwDayNumInMonth;
        if (days <= 0 || days > status.szVideoStatus.length) {
            days = LocalDate.of(year, month, 1).lengthOfMonth();
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < days && i < status.szVideoStatus.length; i++) {
            if (status.szVideoStatus[i] != 0) {
                result.add(LocalDate.of(year, month, i + 1).toString());
            }
        }
        return result;
    }

    public List<Map<String, Object>> listRecordings(Device device, String from, String to) {
        ZoneId zone = ZoneId.systemDefault();
        long end = to == null || to.isBlank()
                ? Instant.now().getEpochSecond()
                : parseEpochSecond(to, zone);
        long begin = from == null || from.isBlank()
                ? LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
                : parseEpochSecond(from, zone);
        long now = Instant.now().getEpochSecond();
        if (end > now) {
            end = now;
        }
        if (begin > end) {
            return List.of();
        }
        begin = Math.max(0, begin - 24 * 60 * 60);
        return findFiles(device, begin, end);
    }

    /**
     * 查与 [beginMs, endMs] 相交的录像。录像文件常常在窗口开始前就已开录，
     * 查询起点向前扩 24 小时，再由调用方按窗口裁切。
     */
    public List<Map<String, Object>> listRecordingsBetween(Device device, long beginMs, long endMs) {
        long begin = epochSecond(beginMs);
        long end = epochSecond(endMs);
        long now = Instant.now().getEpochSecond();
        if (end > now) {
            end = now;
        }
        if (begin >= end) {
            return List.of();
        }
        begin = Math.max(0, begin - 24 * 60 * 60);
        return findFiles(device, begin, end);
    }

    private List<Map<String, Object>> findFiles(Device device, long begin, long end) {
        RecordDevice recorder = requireRecorder(device);
        Pointer user = login(recorder);
        loadChannels(user, recorder.getHost() + ":" + recorder.getPort());
        NvrSdk.FileCond cond = new NvrSdk.FileCond();
        cond.dwChannelID = device.getRecordChannel();
        cond.tBeginTime = begin;
        cond.tEndTime = end;
        NvrSdk.LibraryApi api = api();
        log.info("[宇视NVR] FindFile 请求 deviceId={} channel={} begin={} end={} fileType={} location={}",
                device.getDeviceId(), cond.dwChannelID, cond.tBeginTime, cond.tEndTime, cond.dwFileType, cond.dwRecordLocation);
        Pointer find = api.NETDEV_FindFile(user, cond);
        if (find == null) {
            int err = api.NETDEV_GetLastError();
            log.warn("[宇视NVR] FindFile 响应失败 deviceId={} error={}", device.getDeviceId(), err);
            if (err == NvrSdk.NO_RECORDING) {
                return List.of();
            }
            throw new IllegalStateException("查询录像文件失败，错误码 " + err);
        }
        log.info("[宇视NVR] FindFile 响应 deviceId={} handle={}", device.getDeviceId(), find);
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            NvrSdk.FindData data = new NvrSdk.FindData();
            while (api.NETDEV_FindNextFile(find, data)) {
                data.read();
                rows.add(toRow(device, data));
                if (rows.size() <= 20) {
                    log.info("[宇视NVR] FindNextFile 响应 deviceId={} begin={} end={} size={} name={}",
                            device.getDeviceId(), data.tBeginTime, data.tEndTime, data.udwFileSize,
                            Native.toString(data.szFileName, "UTF-8"));
                }
                data = new NvrSdk.FindData();
            }
            log.info("[宇视NVR] FindNextFile 结束 deviceId={} count={} lastError={}",
                    device.getDeviceId(), rows.size(), api.NETDEV_GetLastError());
        } finally {
            api.NETDEV_FindClose(find);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("recordTime"), (Long) a.get("recordTime")));
        return rows;
    }

    private Map<String, Object> toRow(Device device, NvrSdk.FindData data) {
        long startMs = data.tBeginTime * 1000L;
        long endMs = data.tEndTime * 1000L;
        String fileName = Native.toString(data.szFileName, "UTF-8");
        if (fileName == null || fileName.isBlank()) {
            fileName = device.getRecordChannel() + "_" + data.tBeginTime + "_" + data.tEndTime;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deviceId", device.getDeviceId());
        row.put("fileName", fileName);
        row.put("recordTime", startMs);
        row.put("endTime", endMs);
        row.put("size", data.udwFileSize);
        row.put("source", "nvr");
        return row;
    }

    /**
     * 按 Demo 的 GetReplayUrl_V30 取 RTSP，再把开始/结束时间写进宇视回放路径。
     * 只把地址交给 ZLM 时，录像机不会按条件里的时间定位，会从同一段旧录像开头送流。
     */
    public String openPlayback(Device device, long beginRaw, long endRaw) {
        long begin = epochSecond(beginRaw);
        long end = epochSecond(endRaw);
        if (begin >= end) {
            throw new IllegalArgumentException("回放开始时间必须早于结束时间");
        }
        RecordDevice recorder = requireRecorder(device);
        Pointer user = login(recorder);
        loadChannels(user, recorder.getHost() + ":" + recorder.getPort());
        int channel = device.getRecordChannel();
        String rtsp = withPlaybackRange(replayUrl(user, channel, begin, end), channel, begin, end);
        log.info("[宇视NVR] 回放地址 channel={} begin={} end={} url={}", channel, begin, end, redactUrl(rtsp));
        rtsp = withCredentials(recorder, rtsp);
        String stream = "pb" + device.getId() + "_" + begin + "_" + end;
        String playHeader = playbackPlayHeader(begin, end);
        zlmClient.closeStreams(PLAYBACK_APP);
        if (!zlmClient.addPlaybackProxy(PLAYBACK_APP, stream, rtsp, playHeader)) {
            sleepQuietly(1000);
            if (!zlmClient.addPlaybackProxy(PLAYBACK_APP, stream, rtsp, playHeader)) {
                throw new IllegalStateException("回放拉流失败");
            }
        }
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(zlmClient.isMediaOnline(PLAYBACK_APP, stream).orElse(false))) {
                break;
            }
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return playUrl(stream);
    }

    /** 浏览器断开后释放这一路，避免一直占着录像机的回放口。 */
    public void releasePlayback(String flvUrl) {
        if (flvUrl == null || flvUrl.isBlank()) {
            return;
        }
        int slash = flvUrl.lastIndexOf('/');
        if (slash < 0 || slash == flvUrl.length() - 1) {
            return;
        }
        String name = flvUrl.substring(slash + 1);
        if (name.endsWith(".live.flv")) {
            name = name.substring(0, name.length() - ".live.flv".length());
        }
        if (!name.isBlank()) {
            zlmClient.delStreamProxy(PLAYBACK_APP, name);
        }
    }

    /** 宇视回放的 PLAY 头。时间用 UTC，格式 yyyyMMddTHHmmssZ。 */
    private static String playbackPlayHeader(long beginSec, long endSec) {
        return "Scale=1.000000&Speed=1.000000&Range=clock="
                + utcClock(beginSec) + "-" + utcClock(endSec);
    }

    private static String utcClock(long epochSec) {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.ofEpochSecond(epochSec));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String replayUrl(Pointer user, int channel, long begin, long end) {
        NvrSdk.LibraryApi api = api();
        byte[] buf = new byte[512];
        NvrSdk.PlaybackCond cond = new NvrSdk.PlaybackCond();
        cond.dwChannelID = channel;
        cond.tBeginTime = begin;
        cond.tEndTime = end;
        cond.dwLinkMode = TCP;
        // 0 是 16 倍速后退。和按时间下载一样，取回放地址也要指定 1 倍速前进。
        cond.dwPlaySpeed = 9;
        // 0=所有存储。1 在头文件里是 VMS，不是录像机；查录像能命中的也是 0。
        cond.dwRecordLocation = 0;
        log.info("[宇视NVR] GetReplayUrl_V30 请求 channel={} begin={} end={} linkMode={} location={} playSpeed=1x",
                channel, begin, end, TCP, cond.dwRecordLocation);
        if (api.NETDEV_GetReplayUrl_V30(user, cond, buf)) {
            String url = cString(buf);
            log.info("[宇视NVR] GetReplayUrl_V30 响应 url={}", redactUrl(url));
            if (!url.isBlank()) {
                return url;
            }
        }
        int v30Err = api.NETDEV_GetLastError();
        log.warn("[宇视NVR] GetReplayUrl_V30 响应失败 error={}", v30Err);
        NvrSdk.RecordFindCond find = new NvrSdk.RecordFindCond();
        find.udwChannelID = channel;
        find.udwBegin = (int) begin;
        find.udwEnd = (int) end;
        find.udwPosition = 0;
        log.info("[宇视NVR] GetPlaybackUrl 请求 channel={} begin={} end={} position={}",
                channel, begin, end, find.udwPosition);
        if (api.NETDEV_GetPlaybackUrl(user, find, buf)) {
            String url = cString(buf);
            log.info("[宇视NVR] GetPlaybackUrl 响应 url={}", redactUrl(url));
            if (!url.isBlank()) {
                return url;
            }
        }
        int playbackErr = api.NETDEV_GetLastError();
        log.warn("[宇视NVR] GetPlaybackUrl 响应失败 error={}", playbackErr);
        throw new IllegalStateException("获取回放地址失败，错误码 " + v30Err + "/" + playbackErr);
    }

    /**
     * 按时间把录像下载到本地 MP4。时间由 SDK 定位，不经过 ZLM 的实时拉流。
     */
    public void downloadByTime(Device device, long beginRaw, long endRaw, Path output, int timeoutSeconds) {
        long begin = epochSecond(beginRaw);
        long end = epochSecond(endRaw);
        if (begin >= end) {
            throw new IllegalArgumentException("回放开始时间必须早于结束时间");
        }
        if (output == null) {
            throw new IllegalArgumentException("下载路径为空");
        }
        RecordDevice recorder = requireRecorder(device);
        Pointer user = login(recorder);
        loadChannels(user, recorder.getHost() + ":" + recorder.getPort());
        int channel = device.getRecordChannel();
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.deleteIfExists(output);
            Files.deleteIfExists(Path.of(output.toString() + ".mp4"));
        } catch (IOException e) {
            throw new IllegalStateException("准备录像下载目录失败: " + e.getMessage(), e);
        }
        NvrSdk.PlaybackCond cond = new NvrSdk.PlaybackCond();
        cond.dwChannelID = channel;
        cond.tBeginTime = begin;
        cond.tEndTime = end;
        cond.dwLinkMode = TCP;
        cond.dwDownloadSpeed = DOWNLOAD_SPEED_EIGHT;
        // 0 是 16 倍速后退。头文件写明不指定播放速度时会默认后退，这里指定 1 倍速前进。
        cond.dwPlaySpeed = 9;
        // 存储位置保持 0（所有）。1 是 VMS，不是录像机。
        String savePath = output.toAbsolutePath().normalize().toString();
        byte[] saveBytes = (savePath + "\u0000").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        NvrSdk.LibraryApi api = api();
        log.info("[宇视NVR] GetFileByTime 请求 channel={} begin={} end={} linkMode=TCP speed=8x path={}",
                channel, begin, end, savePath);
        Pointer handle = api.NETDEV_GetFileByTime(user, cond, saveBytes, MEDIA_FILE_MP4);
        if (handle == null) {
            int err = api.NETDEV_GetLastError();
            log.warn("[宇视NVR] GetFileByTime 响应失败 channel={} error={}", channel, err);
            throw new IllegalStateException("按时间下载录像失败，错误码 " + err);
        }
        DownloadWatch watch = new DownloadWatch();
        try {
            waitDownload(api, handle, begin, end, output, timeoutSeconds, watch);
        } finally {
            api.NETDEV_StopGetFile(handle);
        }
        try {
            Path saved = null;
            for (int i = 0; i < 6 && saved == null; i++) {
                saved = locateDownload(output);
                if (saved == null) {
                    Thread.sleep(500);
                }
            }
            if (saved == null) {
                throw new IllegalStateException("按时间下载录像失败 time=" + watch.time
                        + " size=" + watch.size + " err=" + watch.lastError
                        + " files=" + describeDownloadDir(output));
            }
            if (!saved.equals(output)) {
                Files.move(saved, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("[宇视NVR] GetFileByTime 完成 channel={} begin={} end={} size={} playTime={}",
                    channel, begin, end, Files.size(output), watch.time);
        } catch (IOException e) {
            throw new IllegalStateException("保存下载录像失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("按时间下载录像被中断");
        }
    }

    private void waitDownload(NvrSdk.LibraryApi api, Pointer handle, long begin, long end,
                              Path output, int timeoutSeconds, DownloadWatch watch) {
        long deadline = System.currentTimeMillis() + Math.max(10, timeoutSeconds) * 1000L;
        long lastTime = Long.MIN_VALUE;
        long lastSize = -1;
        int stall = 0;
        while (System.currentTimeMillis() < deadline) {
            LongByReference playTime = new LongByReference();
            boolean ok = api.NETDEV_PlayBackControl(handle, PLAY_CTRL_GETPLAYTIME, playTime.getPointer());
            long size = downloadSize(output);
            watch.size = size;
            if (ok) {
                watch.fail = 0;
                long time = playTime.getValue();
                watch.time = time;
                if (time != lastTime || size != lastSize) {
                    log.info("[宇视NVR] GetFileByTime 进度 time={} end={} size={}", time, end, size);
                }
                if (time >= end) {
                    return;
                }
                if (time == lastTime) {
                    stall++;
                    // 已经在收数据且进度不再变化时结束。刚开始时间不动不能停，否则 TCP 还没连上就被掐掉。
                    if (stall >= 5 && (size > 0 || time > begin)) {
                        return;
                    }
                } else {
                    stall = 0;
                }
                lastTime = time;
                lastSize = size;
            } else {
                watch.fail++;
                watch.lastError = api.NETDEV_GetLastError();
                if (watch.fail == 1 || watch.fail == 4) {
                    log.warn("[宇视NVR] GetFileByTime 进度读取失败 fail={} size={} error={}",
                            watch.fail, size, watch.lastError);
                }
                if (watch.fail > 3 && size > 0) {
                    return;
                }
                lastSize = size;
            }
            try {
                Thread.sleep(700);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("按时间下载录像被中断");
            }
        }
        watch.timedOut = true;
        log.warn("[宇视NVR] GetFileByTime 等待结束 time={} size={} err={}", watch.time, watch.size, watch.lastError);
    }

    private static long downloadSize(Path output) {
        Path found = null;
        try {
            found = locateDownload(output);
        } catch (IOException ignored) {
            return 0L;
        }
        return found == null ? 0L : fileSize(found);
    }

    private static long fileSize(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private static Path locateDownload(Path output) throws IOException {
        Path withSuffix = Path.of(output.toString() + ".mp4");
        Path cwd = Path.of("").toAbsolutePath().resolve(output.getFileName().toString());
        Path cwdSuffix = Path.of(cwd.toString() + ".mp4");
        for (Path candidate : new Path[] {output, withSuffix, cwd, cwdSuffix}) {
            if (fileSize(candidate) > 0) {
                return candidate;
            }
        }
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        String name = output.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        try (var list = Files.list(parent)) {
            return list.filter(path -> Files.isRegularFile(path) && fileSize(path) > 0)
                    .filter(path -> path.getFileName().toString().startsWith(stem))
                    .max((a, b) -> Long.compare(fileSize(a), fileSize(b)))
                    .orElse(null);
        }
    }

    private static String describeDownloadDir(Path output) {
        Path parent = output.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        try (var list = Files.list(parent)) {
            list.limit(12).forEach(path -> {
                if (sb.length() > 1) {
                    sb.append(',');
                }
                sb.append(path.getFileName()).append(':').append(fileSize(path));
            });
        } catch (IOException e) {
            return "[unreadable]";
        }
        sb.append(']');
        return sb.toString();
    }

    private static final class DownloadWatch {
        private long time = -1;
        private long size;
        private int fail;
        private int lastError;
        private boolean timedOut;
    }

    /**
     * 宇视 NVR 回放路径：/c通道/b开始秒/e结束秒/replay/，后面的 type、s 是码流，必须留下。
     * 时间已经一致时直接用接口原地址；只有时间不同才替换 b/e。
     */
    static String withPlaybackRange(String rtspUrl, int channel, long begin, long end) {
        if (rtspUrl == null || rtspUrl.isBlank()) {
            throw new IllegalStateException("回放地址为空");
        }
        String marker = "/c" + channel + "/b" + begin + "/e" + end + "/replay";
        if (rtspUrl.toLowerCase(java.util.Locale.ROOT).contains(marker.toLowerCase(java.util.Locale.ROOT))) {
            return rtspUrl;
        }
        String range = marker + "/";
        String replaced = rtspUrl.replaceFirst("(?i)/c\\d+/b\\d+/e\\d+/replay/?", range);
        if (!replaced.equals(rtspUrl)) {
            return replaced;
        }
        replaced = rtspUrl.replaceFirst("(?i)/b\\d+/e\\d+", "/b" + begin + "/e" + end);
        if (!replaced.equals(rtspUrl)) {
            return replaced;
        }
        int scheme = rtspUrl.indexOf("://");
        if (scheme < 0) {
            return rtspUrl;
        }
        int path = rtspUrl.indexOf('/', scheme + 3);
        String origin = path < 0 ? rtspUrl : rtspUrl.substring(0, path);
        return origin + range;
    }

    private String playUrl(String stream) {
        // 这个地址由本服务去拉 FLV，必须用本机 ZLM，不能用给浏览器的公网播放口。
        String base = zlmProperties.getBaseUrl();
        if (base == null || base.isBlank()) {
            base = "http://127.0.0.1:8080";
        }
        base = base.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + PLAYBACK_APP + "/" + stream + ".live.flv";
    }

    private static String withCredentials(RecordDevice recorder, String rtspUrl) {
        if (rtspUrl.contains("@")) {
            return rtspUrl;
        }
        String username = recorder.getUsername() == null ? "" : recorder.getUsername();
        String password = recorder.getPassword() == null ? "" : recorder.getPassword();
        String rest = rtspUrl.startsWith("rtsp://") ? rtspUrl.substring("rtsp://".length()) : rtspUrl;
        return "rtsp://" + username + ":" + password + "@" + rest;
    }

    private static long epochSecond(long raw) {
        return raw > 10_000_000_000L ? raw / 1000L : raw;
    }

    private static String redactUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.replaceAll("(rtsp://)([^/@]+)@", "$1***@");
    }

    private static String cString(byte[] raw) {
        int n = 0;
        while (n < raw.length && raw[n] != 0) {
            n++;
        }
        return new String(raw, 0, n, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    public RecordDevice requireRecorder(Long id) {
        return recordDeviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("录像设备不存在"));
    }

    private RecordDevice requireRecorder(Device device) {
        return requireRecorder(device.getRecordDeviceId());
    }

    /** FindFile 依赖通道视频源。只登录不拉通道时，ONVIF 会报 102。 */
    private NvrSdk.ChannelInfo[] loadChannels(Pointer user, String target) {
        int max = 256;
        IntByReference count = new IntByReference(max);
        NvrSdk.ChannelInfo[] list = (NvrSdk.ChannelInfo[]) new NvrSdk.ChannelInfo().toArray(max);
        NvrSdk.LibraryApi api = api();
        log.info("[宇视NVR] QueryVideoChlDetailListEx 请求 target={}", target);
        if (!api.NETDEV_QueryVideoChlDetailListEx(user, count, list)) {
            int err = api.NETDEV_GetLastError();
            log.warn("[宇视NVR] QueryVideoChlDetailListEx 响应失败 target={} error={}", target, err);
            throw new IllegalStateException("查询录像设备通道失败，错误码 " + err);
        }
        int n = Math.min(Math.max(count.getValue(), 0), max);
        log.info("[宇视NVR] QueryVideoChlDetailListEx 响应 target={} channelCount={}", target, n);
        channelReady.put(user, Boolean.TRUE);
        return list;
    }

    private Pointer login(RecordDevice recorder) {
        String key = recorder.getHost() + ":" + recorder.getPort()
                + ":" + recorder.getUsername() + ":" + recorder.getPassword();
        Pointer existing = sessions.get(key);
        if (existing != null) {
            return existing;
        }
        synchronized (sessions) {
            existing = sessions.get(key);
            if (existing != null) {
                return existing;
            }
            NvrSdk.LoginInfo info = new NvrSdk.LoginInfo();
            copy(recorder.getHost(), info.szIPAddr);
            copy(recorder.getUsername(), info.szUserName);
            copy(recorder.getPassword(), info.szPassword);
            info.dwPort = recorder.getPort();
            info.dwLoginProto = 0;
            info.dwDeviceType = 0;
            log.info("[宇视NVR] Login_V30 请求 host={}:{} user={} proto=ONVIF",
                    recorder.getHost(), recorder.getPort(), recorder.getUsername());
            Pointer user = api().NETDEV_Login_V30(info, new NvrSdk.SeLogInfo());
            if (user == null) {
                int err = api().NETDEV_GetLastError();
                log.warn("[宇视NVR] Login_V30 响应失败 host={}:{} error={}", recorder.getHost(), recorder.getPort(), err);
                throw new IllegalStateException("录像设备 ONVIF 登录失败，错误码 " + err);
            }
            log.info("[宇视NVR] Login_V30 响应成功 host={}:{}", recorder.getHost(), recorder.getPort());
            sessions.put(key, user);
            return user;
        }
    }

    private NvrSdk.LibraryApi api() {
        return NvrSdk.api(properties.getLibraryPath());
    }

    private static long parseEpochSecond(String raw, ZoneId zone) {
        String text = raw.trim();
        if (text.chars().allMatch(Character::isDigit)) {
            long value = Long.parseLong(text);
            return value > 10_000_000_000L ? value / 1000L : value;
        }
        LocalDateTime time = LocalDateTime.parse(text.replace(" ", "T"));
        return time.atZone(zone).toEpochSecond();
    }

    private static void copy(String text, byte[] dest) {
        byte[] src = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int n = Math.min(src.length, dest.length - 1);
        System.arraycopy(src, 0, dest, 0, n);
    }

    private static String text(byte[] raw) {
        int n = 0;
        while (n < raw.length && raw[n] != 0) {
            n++;
        }
        if (n == 0) {
            return "";
        }
        return new String(raw, 0, n, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private static String normalizeIp(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("内网 IP 不能为空");
        }
        return raw.trim();
    }

    public record LanChannel(int channelId, String name) {}
}
