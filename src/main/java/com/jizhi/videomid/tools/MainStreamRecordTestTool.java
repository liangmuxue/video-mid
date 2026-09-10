package com.jizhi.videomid.tools;

import com.jizhi.videomid.device.DeviceStream;
import com.jizhi.videomid.device.DeviceStreamRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 【临时测试数据工具 — 用完请删除整个 tools 包】
 * <p>
 * 扫描码流表主码流（main），按播放地址检测是否有推流；
 * 有推流则每隔 intervalSeconds 自动录制一段 clipSeconds 的视频，
 * 保存到以 device_id 命名的文件夹，文件名用时间戳，便于按设备查历史录像。
 * <p>
 * 默认关闭。开启配置：{@code testdata.main-stream-record.enabled=true}
 */
@Component
@ConditionalOnProperty(prefix = "testdata.main-stream-record", name = "enabled", havingValue = "true")
public class MainStreamRecordTestTool {

    private static final Logger log = LoggerFactory.getLogger(MainStreamRecordTestTool.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final OutputStream DEV_NULL = new OutputStream() {
        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    };

    private final DeviceStreamRepository streamRepository;

    @Value("${testdata.main-stream-record.output-dir:data/testdata-records}")
    private String outputDir;

    @Value("${testdata.main-stream-record.interval-seconds:300}")
    private int intervalSeconds;

    @Value("${testdata.main-stream-record.clip-seconds:300}")
    private int clipSeconds;

    @Value("${testdata.main-stream-record.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    /** 从 RTMP/RTSP 推导 HTTP-FLV 探测地址时使用的 ZLM HTTP 端口 */
    @Value("${testdata.main-stream-record.zlm-http-port:8080}")
    private int zlmHttpPort;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "main-stream-record-test");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService recordPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "main-stream-record-worker");
        t.setDaemon(true);
        return t;
    });

    /** 同一设备正在录制则跳过本轮，避免重叠 */
    private final Map<String, Boolean> recording = new ConcurrentHashMap<>();

    public MainStreamRecordTestTool(DeviceStreamRepository streamRepository) {
        this.streamRepository = streamRepository;
    }

    @PostConstruct
    public void start() {
        int interval = Math.max(30, intervalSeconds);
        log.warn("[TEST-DATA] MainStreamRecordTestTool 已启用：每 {}s 检测主码流并录制 {}s，输出目录={}",
                interval, Math.max(5, clipSeconds), outputDir);
        scheduler.scheduleWithFixedDelay(this::tick, 10, interval, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdownNow();
        recordPool.shutdownNow();
        log.warn("[TEST-DATA] MainStreamRecordTestTool 已停止");
    }

    private void tick() {
        try {
            List<DeviceStream> streams = streamRepository.findAll();
            for (DeviceStream s : streams) {
                if (!"main".equalsIgnoreCase(s.getStreamType())) {
                    continue;
                }
                String url = s.getStreamUrl();
                if (url == null || url.isBlank()) {
                    continue;
                }
                if (!isPushing(url.trim())) {
                    log.debug("[TEST-DATA] 无推流，跳过 deviceId={}", s.getDeviceId());
                    continue;
                }
                String deviceId = s.getDeviceId();
                if (recording.putIfAbsent(deviceId, Boolean.TRUE) != null) {
                    log.debug("[TEST-DATA] 设备 {} 上一片仍在录制，跳过", deviceId);
                    continue;
                }
                String folderName = sanitizeFolderName(deviceId);
                String playUrl = url.trim();
                recordPool.submit(() -> {
                    try {
                        recordOne(deviceId, folderName, playUrl);
                    } finally {
                        recording.remove(deviceId);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[TEST-DATA] 本轮扫描失败: {}", e.getMessage());
        }
    }

    private void recordOne(String deviceId, String folderName, String streamUrl) {
        int duration = Math.max(5, clipSeconds);
        Path dir = Path.of(outputDir, folderName).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            String fileName = TS.format(LocalDateTime.now()) + ".mp4";
            Path out = dir.resolve(fileName);
            log.info("[TEST-DATA] 开始录制 deviceId={} -> {}", deviceId, out);

            ProcessBuilder pb = new ProcessBuilder(
                    ffmpegPath,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-rw_timeout", "5000000",
                    "-i", streamUrl,
                    "-t", String.valueOf(duration),
                    "-c", "copy",
                    "-bsf:a", "aac_adtstoasc",
                    "-movflags", "+faststart",
                    out.toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream in = p.getInputStream()) {
                in.transferTo(DEV_NULL);
            }
            boolean finished = p.waitFor(duration + 60L, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.warn("[TEST-DATA] 录制超时 deviceId={} file={}", deviceId, out);
                return;
            }
            long size = Files.exists(out) ? Files.size(out) : 0L;
            if (p.exitValue() == 0 && size > 0) {
                log.info("[TEST-DATA] 录制完成 deviceId={} file={} size={}", deviceId, out, size);
            } else {
                log.warn("[TEST-DATA] 录制失败 deviceId={} exit={} size={}", deviceId, p.exitValue(), size);
                Files.deleteIfExists(out);
            }
        } catch (Exception e) {
            log.warn("[TEST-DATA] 录制异常 deviceId={}: {}", deviceId, e.getMessage());
        }
    }

    /** 根据播放地址检测是否有推流 */
    boolean isPushing(String streamUrl) {
        String probeUrl = toHttpFlvProbeUrl(streamUrl);
        if (probeUrl != null && probeHttpFlv(probeUrl)) {
            return true;
        }
        if (streamUrl.toLowerCase().startsWith("http")) {
            return probeHttpFlv(streamUrl);
        }
        return probeWithFfprobe(streamUrl);
    }

    private String toHttpFlvProbeUrl(String streamUrl) {
        try {
            String lower = streamUrl.toLowerCase();
            if (lower.startsWith("rtmp://") || lower.startsWith("rtmps://")
                    || lower.startsWith("rtsp://") || lower.startsWith("rtsps://")) {
                URI u = URI.create(streamUrl);
                String host = u.getHost();
                if (host == null) {
                    return null;
                }
                String path = u.getPath() == null ? "" : u.getPath().replaceAll("^/+|/+$", "");
                String[] parts = path.split("/");
                if (parts.length < 2) {
                    return null;
                }
                String stream = parts[parts.length - 1];
                StringBuilder app = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) {
                        app.append('/');
                    }
                    app.append(parts[i]);
                }
                return "http://" + host + ":" + zlmHttpPort + "/" + app + "/" + stream + ".live.flv";
            }
            if (lower.contains(".live.flv") || lower.endsWith(".flv")) {
                return streamUrl;
            }
        } catch (Exception ignored) {
            // ignore
        }
        return null;
    }

    private boolean probeHttpFlv(String httpUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = URI.create(httpUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Connection", "close");
            if (conn.getResponseCode() != 200) {
                return false;
            }
            try (InputStream in = conn.getInputStream()) {
                byte[] buf = in.readNBytes(13);
                return buf.length >= 3 && buf[0] == 'F' && buf[1] == 'L' && buf[2] == 'V';
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean probeWithFfprobe(String streamUrl) {
        try {
            String probeBin = toFfprobePath(ffmpegPath);
            ProcessBuilder pb = new ProcessBuilder(
                    probeBin,
                    "-v", "error",
                    "-rw_timeout", "3000000",
                    "-show_entries", "stream=codec_type",
                    "-of", "csv=p=0",
                    streamUrl
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return false;
            }
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes());
            }
            return p.exitValue() == 0 && (out.contains("video") || out.contains("audio"));
        } catch (Exception e) {
            return false;
        }
    }

    static String toFfprobePath(String ffmpeg) {
        if (ffmpeg == null || ffmpeg.isBlank() || "ffmpeg".equals(ffmpeg)) {
            return "ffprobe";
        }
        if (ffmpeg.endsWith("ffmpeg.exe")) {
            return ffmpeg.substring(0, ffmpeg.length() - "ffmpeg.exe".length()) + "ffprobe.exe";
        }
        if (ffmpeg.endsWith("ffmpeg")) {
            return ffmpeg.substring(0, ffmpeg.length() - "ffmpeg".length()) + "ffprobe";
        }
        return "ffprobe";
    }

    static String sanitizeFolderName(String name) {
        String s = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return s.isEmpty() ? "unknown" : s;
    }
}
