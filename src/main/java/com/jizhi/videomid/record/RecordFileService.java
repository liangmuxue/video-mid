package com.jizhi.videomid.record;

import com.jizhi.videomid.util.FfprobeUtil;
import com.jizhi.videomid.util.TsUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 device_id 目录查询本地录制文件，筛选项为文件名中的时间戳。
 */
@Service
public class RecordFileService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern FILE_PATTERN = Pattern.compile("^(\\d{8}_\\d{6})\\.mp4$", Pattern.CASE_INSENSITIVE);

    @Value("${testdata.main-stream-record.output-dir:data/testdata-records}")
    private String outputDir;

    @Value("${record.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${record.probe-timeout-seconds:10}")
    private int probeTimeoutSeconds;

    @Value("${record.default-clip-seconds:300}")
    private int defaultClipSeconds;

    /** path + mtime → durationMs，避免重复 ffprobe */
    private final ConcurrentHashMap<String, CachedDuration> durationCache = new ConcurrentHashMap<>();

    public List<Map<String, Object>> list(String deviceId, String from, String to) {
        String id = requireDeviceId(deviceId);
        LocalDateTime fromTs = parseOptional(from);
        LocalDateTime toTs = parseOptional(to);
        if (fromTs != null && toTs != null && fromTs.isAfter(toTs)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }

        Path dir = deviceDir(id);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        long fromMs = fromTs != null ? TsUtil.toMillis(fromTs) : Long.MIN_VALUE;
        long toMs = toTs != null ? TsUtil.toMillis(toTs) : Long.MAX_VALUE;
        long clipEstimateMs = Math.max(1, defaultClipSeconds) * 1000L;

        List<ScannedFile> candidates = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mp4")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                Matcher m = FILE_PATTERN.matcher(name);
                if (!m.matches()) {
                    continue;
                }
                LocalDateTime ts = LocalDateTime.parse(m.group(1), FILE_TS);
                long startMillis = TsUtil.toMillis(ts);
                // 先用文件名时间戳粗筛，避免对目录内全部历史文件 ffprobe
                if (startMillis > toMs) {
                    continue;
                }
                if (fromMs != Long.MIN_VALUE && startMillis + clipEstimateMs < fromMs) {
                    continue;
                }
                candidates.add(new ScannedFile(id, name, file, startMillis));
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取录像目录失败: " + e.getMessage(), e);
        }

        List<Map<String, Object>> result = candidates.parallelStream()
                .map(sf -> {
                    try {
                        return buildRow(sf.deviceId, sf.fileName, sf.file, sf.startMillis);
                    } catch (IOException e) {
                        throw new IllegalStateException("读取录像文件失败: " + sf.fileName, e);
                    }
                })
                .filter(row -> overlapsRange(row, fromMs, toMs, clipEstimateMs))
                .sorted(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("recordTime")).reversed())
                .toList();
        return result;
    }

    /** 某月内有录像的日期列表（yyyy-MM-dd，升序）。 */
    public List<String> listRecordingDays(String deviceId, int year, int month) {
        String id = requireDeviceId(deviceId);
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month 必须在 1~12");
        }
        if (year < 1970 || year > 2100) {
            throw new IllegalArgumentException("year 非法");
        }
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDateTime fromTs = monthStart.atStartOfDay();
        LocalDateTime toTs = monthEnd.atTime(23, 59, 59);

        Path dir = deviceDir(id);
        TreeSet<String> days = new TreeSet<>();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mp4")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                Matcher m = FILE_PATTERN.matcher(name);
                if (!m.matches()) {
                    continue;
                }
                LocalDateTime ts = LocalDateTime.parse(m.group(1), FILE_TS);
                if (ts.isBefore(fromTs) || ts.isAfter(toTs)) {
                    continue;
                }
                days.add(ts.toLocalDate().toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取录像目录失败: " + e.getMessage(), e);
        }
        return new ArrayList<>(days);
    }

    public List<Map<String, Object>> listWithVideoUrls(String deviceId, String from, String to, String publicBaseUrl) {
        String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        List<Map<String, Object>> list = list(deviceId, from, to);
        for (Map<String, Object> row : list) {
            String fileName = String.valueOf(row.get("fileName"));
            String id = String.valueOf(row.get("deviceId"));
            row.put("videoUrl", base + "/api/open/recordings/" + encodePath(id) + "/" + encodePath(fileName));
        }
        return list;
    }

    private static String encodePath(String raw) {
        try {
            return java.net.URLEncoder.encode(raw, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20");
        } catch (Exception e) {
            return raw;
        }
    }

    public Resource openFile(String deviceId, String fileName) {
        return new FileSystemResource(resolveFilePath(deviceId, fileName));
    }

    /** 解析录像文件路径（含存在性校验）。 */
    public Path resolveFilePath(String deviceId, String fileName) {
        String id = requireDeviceId(deviceId);
        String name = requireFileName(fileName);
        Path file = deviceDir(id).resolve(name).normalize();
        Path root = deviceDir(id).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("录像文件不存在");
        }
        return file;
    }

    private Map<String, Object> buildRow(String deviceId, String fileName, Path file, long startMillis) throws IOException {
        Map<String, Object> row = new HashMap<>();
        row.put("deviceId", deviceId);
        row.put("fileName", fileName);
        row.put("recordTime", startMillis);
        row.put("size", Files.size(file));
        row.put("path", file.toAbsolutePath().normalize().toString());
        attachDuration(row, file, startMillis);
        return row;
    }

    /** ffprobe 读取真实时长，写入 endTime / durationSeconds（毫秒级结束时间）。 */
    private void attachDuration(Map<String, Object> row, Path file, long startMillis) {
        OptionalLong durMs = probeDurationCached(file);
        if (durMs.isEmpty() || durMs.getAsLong() <= 0) {
            return;
        }
        long durationMs = durMs.getAsLong();
        row.put("durationSeconds", Math.round(durationMs / 1000.0));
        row.put("endTime", startMillis + durationMs);
    }

    private OptionalLong probeDurationCached(Path file) {
        try {
            long mtime = Files.getLastModifiedTime(file).toMillis();
            String key = file.toAbsolutePath().normalize().toString();
            CachedDuration cached = durationCache.get(key);
            if (cached != null && cached.mtime == mtime) {
                return cached.durationMs <= 0 ? OptionalLong.empty() : OptionalLong.of(cached.durationMs);
            }
            OptionalLong durMs = FfprobeUtil.probeDurationMillis(ffprobePath, file, probeTimeoutSeconds);
            long stored = durMs.isEmpty() ? -1L : durMs.getAsLong();
            durationCache.put(key, new CachedDuration(mtime, stored));
            return durMs;
        } catch (IOException e) {
            return FfprobeUtil.probeDurationMillis(ffprobePath, file, probeTimeoutSeconds);
        }
    }

    private static boolean overlapsRange(Map<String, Object> row, long fromMs, long toMs, long clipEstimateMs) {
        long startMillis = (Long) row.get("recordTime");
        Long endTime = (Long) row.get("endTime");
        long endMillis = endTime != null ? endTime : startMillis + clipEstimateMs;
        return endMillis >= fromMs && startMillis <= toMs;
    }

    private record ScannedFile(String deviceId, String fileName, Path file, long startMillis) {
    }

    private record CachedDuration(long mtime, long durationMs) {
    }

    private Path deviceDir(String deviceId) {
        return Path.of(outputDir, sanitize(deviceId)).toAbsolutePath().normalize();
    }

    private static String requireDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        return deviceId.trim();
    }

    private static String requireFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName 不能为空");
        }
        String name = fileName.trim();
        if (name.contains("..") || name.contains("/") || name.contains("\\") || !FILE_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("非法文件名");
        }
        return name;
    }

    private static String sanitize(String name) {
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /**
     * 支持毫秒时间戳（推荐）；兼容 yyyyMMdd_HHmmss、yyyy-MM-dd HH:mm:ss 等旧格式。
     */
    static LocalDateTime parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("\\d{10,13}")) {
            long millis = s.length() == 10 ? Long.parseLong(s) * 1000L : Long.parseLong(s);
            return TsUtil.fromMillis(millis);
        }
        try {
            if (s.length() == 15 && s.charAt(8) == '_') {
                return LocalDateTime.parse(s, FILE_TS);
            }
            if (s.length() == 10 && s.charAt(4) == '-') {
                return LocalDateTime.parse(s + "T00:00:00");
            }
            if (s.contains(" ")) {
                return LocalDateTime.parse(s.replace(' ', 'T'));
            }
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式错误，请传毫秒时间戳: " + raw);
        }
    }
}
