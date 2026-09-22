package com.jizhi.videomid.record;

import com.jizhi.videomid.util.FfmpegUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 按设备编号 + 时间点，截取前后若干秒的录像片段。
 */
@Service
public class RecordClipService {

    private final RecordFileService recordFileService;

    @Value("${record.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${record.clip-cache-dir:data/clip-cache}")
    private String clipCacheDir;

    @Value("${record.clip-cache-max-age-hours:24}")
    private int clipCacheMaxAgeHours;

    @Value("${record.clip-timeout-seconds:60}")
    private int clipTimeoutSeconds;

    @Value("${record.default-clip-offset-seconds:30}")
    private int defaultClipOffsetSeconds;

    @Value("${record.max-clip-offset-seconds:150}")
    private int maxClipOffsetSeconds;

    @Value("${record.default-clip-seconds:300}")
    private int defaultClipSeconds;

    @Value("${record.clip-batch-max-size:50}")
    private int clipBatchMaxSize;

    public RecordClipService(RecordFileService recordFileService) {
        this.recordFileService = recordFileService;
    }

    /** 截取并返回 MP4 资源（带磁盘缓存）。 */
    public Resource openClip(String deviceId, String atRaw, Integer seconds) {
        ClipRequest req = parseRequest(deviceId, atRaw, seconds);
        Path clip = getOrCreateClip(req);
        return new FileSystemResource(clip);
    }

    /** 返回片段元数据：videoUrl、startTime、endTime（毫秒）等。 */
    public Map<String, Object> clipInfo(String deviceId, String atRaw, Integer seconds, String videoUrl) {
        ClipRequest req = parseRequest(deviceId, atRaw, seconds);
        Path clip = getOrCreateClip(req);
        long size;
        try {
            size = Files.size(clip);
        } catch (IOException e) {
            throw new IllegalStateException("读取片段文件失败: " + e.getMessage(), e);
        }
        return buildClipMetadata(req, clip, size, videoUrl);
    }

    /**
     * 批量截取：数组每项含 deviceId、at、seconds。
     * 单条失败不影响其他条目（返回 ok=false + error）。
     */
    public List<Map<String, Object>> clipInfoBatch(List<RecordClipItemRequest> items,
                                                   ClipVideoUrlFactory videoUrlFactory,
                                                   Consumer<String> deviceValidator) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("请求数组不能为空");
        }
        if (items.size() > clipBatchMaxSize) {
            throw new IllegalArgumentException("单次最多 " + clipBatchMaxSize + " 条");
        }
        List<Map<String, Object>> results = new ArrayList<>(items.size());
        for (RecordClipItemRequest item : items) {
            results.add(clipInfoOne(item, videoUrlFactory, deviceValidator));
        }
        return results;
    }

    private Map<String, Object> clipInfoOne(RecordClipItemRequest item,
                                            ClipVideoUrlFactory videoUrlFactory,
                                            Consumer<String> deviceValidator) {
        Map<String, Object> row = new LinkedHashMap<>();
        String deviceId = item.getDeviceId() == null ? null : item.getDeviceId().trim();
        String atStr = item.atAsString();
        Integer seconds = item.getSeconds();
        row.put("deviceId", deviceId);
        row.put("seconds", seconds != null ? seconds : defaultClipOffsetSeconds);
        if (atStr != null && atStr.matches("\\d{10,13}")) {
            long atMillis = atStr.length() == 10 ? Long.parseLong(atStr) * 1000L : Long.parseLong(atStr);
            row.put("at", atMillis);
        } else if (item.getAt() != null) {
            row.put("at", item.getAt());
        }
        try {
            if (deviceId == null || deviceId.isBlank()) {
                throw new IllegalArgumentException("deviceId 不能为空");
            }
            if (atStr == null || atStr.isBlank()) {
                throw new IllegalArgumentException("at 不能为空（毫秒时间戳）");
            }
            if (deviceValidator != null) {
                deviceValidator.accept(deviceId);
            }
            String videoUrl = videoUrlFactory.build(deviceId, atStr, seconds);
            Map<String, Object> info = clipInfo(deviceId, atStr, seconds, videoUrl);
            row.putAll(info);
            row.put("ok", true);
        } catch (Exception e) {
            row.put("ok", false);
            row.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        return row;
    }

    @FunctionalInterface
    public interface ClipVideoUrlFactory {
        String build(String deviceId, String at, Integer seconds);
    }

    private Map<String, Object> buildClipMetadata(ClipRequest req, Path clip, long size, String videoUrl) {
        long startTime = req.actualStartMillis();
        long endTime = req.actualEndMillis();

        Map<String, Object> info = new HashMap<>();
        info.put("deviceId", req.deviceId());
        info.put("at", req.atMillis());
        info.put("seconds", req.seconds());
        info.put("startTime", startTime);
        info.put("endTime", endTime);
        info.put("durationSeconds", Math.max(0L, Math.round((endTime - startTime) / 1000.0)));
        info.put("videoUrl", videoUrl);
        info.put("clipUrl", videoUrl);
        info.put("windowStart", req.windowStart());
        info.put("windowEnd", req.windowEnd());
        info.put("sourceFiles", req.sourceFileNames());
        info.put("clipFileName", clip.getFileName().toString());
        info.put("size", size);
        return info;
    }

    private Path getOrCreateClip(ClipRequest req) {
        Path cacheFile = cachePath(req);
        if (isCacheValid(cacheFile, req)) {
            return cacheFile;
        }
        generateClip(req, cacheFile);
        return cacheFile;
    }

    private ClipRequest parseRequest(String deviceId, String atRaw, Integer secondsRaw) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId 不能为空");
        }
        if (atRaw == null || atRaw.isBlank()) {
            throw new IllegalArgumentException("at 不能为空（毫秒时间戳）");
        }
        String atStr = atRaw.trim();
        if (!atStr.matches("\\d{10,13}")) {
            throw new IllegalArgumentException("at 必须为 10~13 位毫秒/秒时间戳");
        }
        long atMillis = atStr.length() == 10 ? Long.parseLong(atStr) * 1000L : Long.parseLong(atStr);

        int seconds = secondsRaw != null ? secondsRaw : defaultClipOffsetSeconds;
        if (seconds <= 0) {
            throw new IllegalArgumentException("seconds 必须大于 0");
        }
        if (seconds > maxClipOffsetSeconds) {
            throw new IllegalArgumentException("seconds 过大，最大 " + maxClipOffsetSeconds);
        }

        long windowStart = atMillis - seconds * 1000L;
        long windowEnd = atMillis + seconds * 1000L;

        List<Map<String, Object>> records = recordFileService.list(
                        deviceId, String.valueOf(windowStart), String.valueOf(windowEnd))
                .stream()
                .sorted(Comparator.comparing(r -> (Long) r.get("recordTime")))
                .toList();
        if (records.isEmpty()) {
            throw new IllegalArgumentException("该时间点无可用录像");
        }
        List<SlicePlan> plans = buildPlans(records, windowStart, windowEnd);
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("该时间点无可用录像");
        }

        List<String> sourceNames = plans.stream().map(SlicePlan::fileName).toList();
        long actualStart = plans.stream().mapToLong(SlicePlan::segStartMillis).min().orElse(windowStart);
        long actualEnd = plans.stream().mapToLong(SlicePlan::segEndMillis).max().orElse(windowEnd);
        return new ClipRequest(deviceId.trim(), atMillis, seconds, windowStart, windowEnd,
                actualStart, actualEnd, plans, sourceNames);
    }

    private List<SlicePlan> buildPlans(List<Map<String, Object>> records, long windowStart, long windowEnd) {
        List<SlicePlan> plans = new ArrayList<>();
        long clipEstimateMs = Math.max(1, defaultClipSeconds) * 1000L;

        for (Map<String, Object> row : records) {
            long recordTime = (Long) row.get("recordTime");
            Long endTime = (Long) row.get("endTime");
            long endMillis = endTime != null ? endTime : recordTime + clipEstimateMs;

            long segStart = Math.max(windowStart, recordTime);
            long segEnd = Math.min(windowEnd, endMillis);
            if (segStart >= segEnd) {
                continue;
            }

            String fileName = String.valueOf(row.get("fileName"));
            Path file = recordFileService.resolveFilePath(String.valueOf(row.get("deviceId")), fileName);
            double offsetSec = (segStart - recordTime) / 1000.0;
            double durationSec = (segEnd - segStart) / 1000.0;
            plans.add(new SlicePlan(fileName, file, offsetSec, durationSec, recordTime, segStart, segEnd));
        }
        return plans;
    }

    private void generateClip(ClipRequest req, Path output) {
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            if (req.plans().size() == 1) {
                SlicePlan plan = req.plans().get(0);
                FfmpegUtil.cutCopy(ffmpegPath, plan.file(), plan.offsetSec(), plan.durationSec(),
                        output, clipTimeoutSeconds);
                return;
            }
            Path tempDir = Files.createTempDirectory("record-clip-");
            try {
                List<Path> parts = new ArrayList<>();
                for (int i = 0; i < req.plans().size(); i++) {
                    SlicePlan plan = req.plans().get(i);
                    Path part = tempDir.resolve("part-" + i + ".mp4");
                    FfmpegUtil.cutCopy(ffmpegPath, plan.file(), plan.offsetSec(), plan.durationSec(),
                            part, clipTimeoutSeconds);
                    parts.add(part);
                }
                FfmpegUtil.concatCopy(ffmpegPath, parts, output, clipTimeoutSeconds);
            } finally {
                deleteRecursively(tempDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("生成录像片段失败: " + e.getMessage(), e);
        }
    }

    private Path cachePath(ClipRequest req) {
        String safeDevice = req.deviceId().replaceAll("[\\\\/:*?\"<>|]", "_");
        String name = safeDevice + "_" + req.atMillis() + "_" + req.seconds() + ".mp4";
        return Path.of(clipCacheDir, safeDevice, name).toAbsolutePath().normalize();
    }

    private boolean isCacheValid(Path cacheFile, ClipRequest req) {
        if (!Files.isRegularFile(cacheFile)) {
            return false;
        }
        try {
            long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(cacheFile).toMillis();
            if (ageMs > Math.max(1, clipCacheMaxAgeHours) * 3600_000L) {
                return false;
            }
            for (SlicePlan plan : req.plans()) {
                if (!Files.isRegularFile(plan.file())) {
                    return false;
                }
                long srcMtime = Files.getLastModifiedTime(plan.file()).toMillis();
                long cacheMtime = Files.getLastModifiedTime(cacheFile).toMillis();
                if (srcMtime > cacheMtime) {
                    return false;
                }
            }
            return Files.size(cacheFile) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // ignore
                }
            });
        } catch (IOException ignored) {
            // ignore
        }
    }

    private record ClipRequest(String deviceId, long atMillis, int seconds,
                               long windowStart, long windowEnd, long actualStartMillis, long actualEndMillis,
                               List<SlicePlan> plans, List<String> sourceFileNames) {
    }

    private record SlicePlan(String fileName, Path file, double offsetSec, double durationSec, long recordTime,
                             long segStartMillis, long segEndMillis) {
    }
}
