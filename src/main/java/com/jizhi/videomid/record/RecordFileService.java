package com.jizhi.videomid.record;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public List<Map<String, Object>> list(String deviceId, String from, String to) {
        String id = requireDeviceId(deviceId);
        LocalDateTime fromTs = parseOptional(from);
        LocalDateTime toTs = parseOptional(to);
        if (fromTs != null && toTs != null && fromTs.isAfter(toTs)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }

        Path dir = deviceDir(id);
        List<Map<String, Object>> result = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.mp4")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                Matcher m = FILE_PATTERN.matcher(name);
                if (!m.matches()) {
                    continue;
                }
                LocalDateTime ts = LocalDateTime.parse(m.group(1), FILE_TS);
                if (fromTs != null && ts.isBefore(fromTs)) {
                    continue;
                }
                if (toTs != null && ts.isAfter(toTs)) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                row.put("deviceId", id);
                row.put("fileName", name);
                row.put("timestamp", FILE_TS.format(ts));
                row.put("recordTime", ts);
                row.put("size", Files.size(file));
                row.put("path", file.toAbsolutePath().normalize().toString());
                result.add(row);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读取录像目录失败: " + e.getMessage(), e);
        }

        result.sort(Comparator.comparing((Map<String, Object> r) -> (LocalDateTime) r.get("recordTime")).reversed());
        return result;
    }

    /**
     * 对外查询：在 list 结果上附加可直接访问的视频 URL。
     * @param publicBaseUrl 如 http://host:8090（不含末尾斜杠）
     */
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
        String id = requireDeviceId(deviceId);
        String name = requireFileName(fileName);
        Path file = deviceDir(id).resolve(name).normalize();
        Path root = deviceDir(id).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("录像文件不存在");
        }
        return new FileSystemResource(file);
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
     * 支持：yyyyMMdd_HHmmss、yyyy-MM-dd HH:mm:ss、yyyy-MM-dd'T'HH:mm:ss、yyyy-MM-dd
     */
    static LocalDateTime parseOptional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
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
            throw new IllegalArgumentException("时间格式错误，支持 yyyyMMdd_HHmmss 或 yyyy-MM-dd HH:mm:ss: " + raw);
        }
    }
}
