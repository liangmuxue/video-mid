package com.jizhi.videomid.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 使用 ffmpeg 截取/合并本地 MP4。 */
public final class FfmpegUtil {

    private static final Logger log = LoggerFactory.getLogger(FfmpegUtil.class);
    private static final OutputStream DEV_NULL = new OutputStream() {
        @Override
        public void write(int b) {
        }

        @Override
        public void write(byte[] b, int off, int len) {
        }
    };

    private FfmpegUtil() {
    }

    /**
     * 从单个文件截取片段（流复制，不重新编码）。
     *
     * @param offsetSec  文件内起始偏移（秒）
     * @param durationSec 截取时长（秒）
     */
    public static void cutCopy(String ffmpegBin, Path input, double offsetSec, double durationSec,
                               Path output, int timeoutSeconds) {
        if (durationSec <= 0) {
            throw new IllegalArgumentException("截取时长必须大于 0");
        }
        String bin = normalizeBin(ffmpegBin);
        try {
            Files.createDirectories(output.getParent());
            ProcessBuilder pb = new ProcessBuilder(
                    bin,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-ss", formatSec(offsetSec),
                    "-i", input.toAbsolutePath().normalize().toString(),
                    "-t", formatSec(durationSec),
                    "-c", "copy",
                    "-movflags", "+faststart",
                    output.toAbsolutePath().normalize().toString()
            );
            runProcess(pb, timeoutSeconds, "ffmpeg cut " + input.getFileName());
            if (!Files.isRegularFile(output) || Files.size(output) <= 0) {
                throw new IllegalStateException("截取失败，输出文件为空");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ffmpeg 截取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 把一段 HTTP-FLV（录像机回放）收成 MP4。视频流复制，音频转成 AAC。
     * 没有音轨时改为只保留视频。
     */
    public static void recordUrl(String ffmpegBin, String inputUrl, double durationSec,
                                 Path output, int timeoutSeconds) {
        if (inputUrl == null || inputUrl.isBlank()) {
            throw new IllegalArgumentException("回放地址为空");
        }
        if (durationSec <= 0) {
            throw new IllegalArgumentException("截取时长必须大于 0");
        }
        try {
            Files.createDirectories(output.getParent());
            try {
                runRecord(ffmpegBin, inputUrl, durationSec, output, timeoutSeconds, true);
            } catch (IllegalStateException first) {
                if (first.getMessage() != null && first.getMessage().contains("超时")) {
                    throw first;
                }
                runRecord(ffmpegBin, inputUrl, durationSec, output, timeoutSeconds, false);
            }
            if (!Files.isRegularFile(output) || Files.size(output) <= 0) {
                throw new IllegalStateException("截取失败，输出文件为空");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ffmpeg 截取回放失败: " + e.getMessage(), e);
        }
    }

    private static void runRecord(String ffmpegBin, String inputUrl, double durationSec,
                                  Path output, int timeoutSeconds, boolean withAudio) throws Exception {
        String bin = normalizeBin(ffmpegBin);
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(bin);
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(inputUrl);
        cmd.add("-t");
        cmd.add(formatSec(durationSec));
        cmd.add("-c:v");
        cmd.add("copy");
        if (withAudio) {
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("64k");
        } else {
            cmd.add("-an");
        }
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add(output.toAbsolutePath().normalize().toString());
        runProcess(new ProcessBuilder(cmd), timeoutSeconds, "ffmpeg record");
    }

    /**
     * 把 SDK 下载好的 MP4 整理成浏览器能播的文件。视频流复制，音频转成 48000Hz AAC。
     * 没有音轨时改为只保留视频。
     */
    public static void remuxCopy(String ffmpegBin, Path input, Path output, int timeoutSeconds) {
        if (!Files.isRegularFile(input)) {
            throw new IllegalArgumentException("回放文件不存在");
        }
        try {
            if (Files.size(input) <= 0) {
                throw new IllegalArgumentException("回放文件为空");
            }
            Files.createDirectories(output.getParent());
            try {
                runRemux(ffmpegBin, input, output, timeoutSeconds, true);
            } catch (IllegalStateException first) {
                if (first.getMessage() != null && first.getMessage().contains("超时")) {
                    throw first;
                }
                runRemux(ffmpegBin, input, output, timeoutSeconds, false);
            }
            if (!Files.isRegularFile(output) || Files.size(output) <= 0) {
                throw new IllegalStateException("截取失败，输出文件为空");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("整理回放文件失败: " + e.getMessage(), e);
        }
    }

    private static void runRemux(String ffmpegBin, Path input, Path output, int timeoutSeconds,
                                 boolean withAudio) throws Exception {
        String bin = normalizeBin(ffmpegBin);
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(bin);
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-y");
        cmd.add("-i");
        cmd.add(input.toAbsolutePath().normalize().toString());
        cmd.add("-c:v");
        cmd.add("copy");
        // 录像机每帧都带截断的 SEI。浏览器解到这里会停，VLC 会忽略。类型 6 就是 SEI。
        cmd.add("-bsf:v");
        cmd.add("filter_units=remove_types=6");
        if (withAudio) {
            cmd.add("-c:a");
            cmd.add("aac");
            // 录像机原音是 8000Hz，浏览器解到几秒就会停。改成 48000Hz。
            cmd.add("-ar");
            cmd.add("48000");
            cmd.add("-ac");
            cmd.add("1");
            cmd.add("-b:a");
            cmd.add("64k");
        } else {
            cmd.add("-an");
        }
        cmd.add("-movflags");
        cmd.add("+faststart");
        cmd.add(output.toAbsolutePath().normalize().toString());
        runProcess(new ProcessBuilder(cmd), timeoutSeconds, "ffmpeg remux");
    }

    /** 按顺序合并多个 MP4（流复制）。 */
    public static void concatCopy(String ffmpegBin, List<Path> inputs, Path output, int timeoutSeconds) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("合并输入不能为空");
        }
        if (inputs.size() == 1) {
            copyOnePart(inputs.get(0), output);
            return;
        }
        String bin = normalizeBin(ffmpegBin);
        Path listFile = null;
        try {
            Files.createDirectories(output.getParent());
            listFile = Files.createTempFile("ffmpeg-concat-", ".txt");
            StringBuilder sb = new StringBuilder();
            for (Path in : inputs) {
                String p = in.toAbsolutePath().normalize().toString().replace("'", "'\\''");
                sb.append("file '").append(p).append("'\n");
            }
            Files.writeString(listFile, sb.toString(), StandardCharsets.UTF_8);

            ProcessBuilder pb = new ProcessBuilder(
                    bin,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-y",
                    "-f", "concat",
                    "-safe", "0",
                    "-i", listFile.toAbsolutePath().normalize().toString(),
                    "-c", "copy",
                    "-movflags", "+faststart",
                    output.toAbsolutePath().normalize().toString()
            );
            runProcess(pb, timeoutSeconds, "ffmpeg concat");
            if (!Files.isRegularFile(output) || Files.size(output) <= 0) {
                throw new IllegalStateException("合并失败，输出文件为空");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("ffmpeg 合并失败: " + e.getMessage(), e);
        } finally {
            if (listFile != null) {
                try {
                    Files.deleteIfExists(listFile);
                } catch (Exception ignored) {
                    log.debug("删除 concat 列表失败: {}", listFile);
                }
            }
        }
    }

    private static void copyOnePart(Path source, Path output) {
        try {
            if (!Files.isRegularFile(source) || Files.size(source) <= 0) {
                throw new IllegalStateException("源片段不存在或为空: " + source);
            }
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING);
            if (!Files.isRegularFile(output) || Files.size(output) <= 0) {
                throw new IllegalStateException("复制后输出为空: " + output);
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("复制片段失败: " + source + " -> " + output + ": "
                    + e.getMessage(), e);
        }
    }

    private static void runProcess(ProcessBuilder pb, int timeoutSeconds, String label) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (InputStream in = p.getInputStream()) {
            in.transferTo(DEV_NULL);
        }
        int timeout = Math.max(10, timeoutSeconds);
        boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IllegalStateException(label + " 超时（" + timeout + "s）");
        }
        if (p.exitValue() != 0) {
            throw new IllegalStateException(label + " 失败，exit=" + p.exitValue());
        }
    }

    private static String normalizeBin(String ffmpegBin) {
        if (ffmpegBin == null || ffmpegBin.isBlank()) {
            return "ffmpeg";
        }
        return ffmpegBin.trim();
    }

    static String formatSec(double sec) {
        if (sec < 0) {
            sec = 0;
        }
        return String.format(java.util.Locale.US, "%.3f", sec);
    }
}
