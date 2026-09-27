package com.jizhi.videomid.uniview.nvr;

import com.jizhi.videomid.config.ZlmProperties;
import com.jizhi.videomid.device.Device;
import com.jizhi.videomid.media.ZlmClient;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
     * 按 Demo 的 GetReplayUrl_V30 取这段录像的 RTSP，再交给 ZLM 转成 HTTP-FLV。
     * begin/end 用查询录像时同一套秒级时间。
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
        String rtsp = replayUrl(user, device.getRecordChannel(), begin, end);
        rtsp = withCredentials(recorder, rtsp);
        String stream = "pb" + device.getId() + "_" + begin;
        if (!zlmClient.addPlaybackProxy(PLAYBACK_APP, stream, rtsp)) {
            throw new IllegalStateException("回放拉流失败");
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

    private String replayUrl(Pointer user, int channel, long begin, long end) {
        NvrSdk.LibraryApi api = api();
        byte[] buf = new byte[512];
        NvrSdk.PlaybackCond cond = new NvrSdk.PlaybackCond();
        cond.dwChannelID = channel;
        cond.tBeginTime = begin;
        cond.tEndTime = end;
        cond.dwLinkMode = TCP;
        cond.dwRecordLocation = 1;
        log.info("[宇视NVR] GetReplayUrl_V30 请求 channel={} begin={} end={} linkMode={} location={}",
                channel, begin, end, TCP, 1);
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
        find.udwPosition = 1;
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

    private String playUrl(String stream) {
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
