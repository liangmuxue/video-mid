package com.jizhi.videomid.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

/** 使用 ffprobe 读取本地媒体文件时长。 */
public final class FfprobeUtil {

    private static final Logger log = LoggerFactory.getLogger(FfprobeUtil.class);

    private FfprobeUtil() {
    }

    /**
     * 由 ffmpeg 可执行路径推导 ffprobe 路径。
     */
    public static String toFfprobePath(String ffmpegOrFfprobe) {
        if (ffmpegOrFfprobe == null || ffmpegOrFfprobe.isBlank()) {
            return "ffprobe";
        }
        String p = ffmpegOrFfprobe.trim();
        if ("ffmpeg".equalsIgnoreCase(p)) {
            return "ffprobe";
        }
        if (p.endsWith("ffmpeg.exe")) {
            return p.substring(0, p.length() - "ffmpeg.exe".length()) + "ffprobe.exe";
        }
        if (p.endsWith("ffmpeg")) {
            return p.substring(0, p.length() - "ffmpeg".length()) + "ffprobe";
        }
        if (p.endsWith("ffprobe.exe") || p.endsWith("ffprobe")) {
            return p;
        }
        return "ffprobe";
    }

    /**
     * 读取本地文件时长（毫秒）。失败返回 empty（不抛异常）。
     */
    public static OptionalLong probeDurationMillis(String ffprobeBin, Path file, int timeoutSeconds) {
        if (file == null || !Files.isRegularFile(file)) {
            return OptionalLong.empty();
        }
        String bin = toFfprobePath(ffprobeBin);
        int timeout = Math.max(3, timeoutSeconds);
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    bin,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    file.toAbsolutePath().normalize().toString()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                log.warn("ffprobe 超时 file={}", file);
                return OptionalLong.empty();
            }
            String out;
            try (InputStream in = p.getInputStream()) {
                out = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            if (p.exitValue() != 0 || out.isEmpty()) {
                log.warn("ffprobe 失败 file={} exit={} out={}", file, p.exitValue(), out);
                return OptionalLong.empty();
            }
            double seconds = Double.parseDouble(out.split("\\s+")[0]);
            if (seconds <= 0 || Double.isNaN(seconds) || Double.isInfinite(seconds)) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Math.round(seconds * 1000.0));
        } catch (Exception e) {
            log.warn("ffprobe 读取时长失败 file={}: {}", file, e.getMessage());
            return OptionalLong.empty();
        }
    }
}
