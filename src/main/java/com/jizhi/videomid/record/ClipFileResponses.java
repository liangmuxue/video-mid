package com.jizhi.videomid.record;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.List;

/**
 * 浏览器播放会带 Range。这里直接写 MP4 字节，避免被当成 JSON。
 */
public final class ClipFileResponses {

    private ClipFileResponses() {
    }

    public static void write(Resource resource, String fileName,
                             HttpServletRequest request, HttpServletResponse response) throws IOException {
        long length = resource.contentLength();
        long start = 0;
        long end = length - 1;
        boolean partial = false;
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        if (rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
                if (ranges.size() != 1) {
                    unsatisfiable(response, length);
                    return;
                }
                HttpRange range = ranges.get(0);
                start = range.getRangeStart(length);
                end = range.getRangeEnd(length);
                partial = true;
            } catch (IllegalArgumentException ex) {
                unsatisfiable(response, length);
                return;
            }
        }
        long count = end - start + 1;
        response.setStatus(partial ? HttpServletResponse.SC_PARTIAL_CONTENT : HttpServletResponse.SC_OK);
        response.setContentType("video/mp4");
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
        if (partial) {
            response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + length);
        }
        response.setContentLengthLong(count);
        try (RandomAccessFile file = new RandomAccessFile(resource.getFile(), "r")) {
            file.seek(start);
            OutputStream out = response.getOutputStream();
            byte[] buf = new byte[64 * 1024];
            long remaining = count;
            while (remaining > 0) {
                int n = file.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (n < 0) {
                    break;
                }
                out.write(buf, 0, n);
                remaining -= n;
            }
            out.flush();
        }
    }

    private static void unsatisfiable(HttpServletResponse response, long length) {
        response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes */" + length);
    }
}
