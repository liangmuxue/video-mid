package com.jizhi.videomid.gb28181.live;

import com.jizhi.videomid.config.Gb28181Properties;
import org.springframework.stereotype.Component;

import java.util.BitSet;

/** RTP 接收端口分配（live INVITE 用） */
@Component
public class RtpPortAllocator {

    private final Gb28181Properties props;
    private final BitSet used = new BitSet();
    private final Object lock = new Object();

    public RtpPortAllocator(Gb28181Properties props) {
        this.props = props;
    }

    public int allocate() {
        synchronized (lock) {
            int min = props.getMedia().getRtpPortMin();
            int max = props.getMedia().getRtpPortMax();
            if (min <= 0 || max < min) {
                throw new IllegalStateException("RTP 端口范围无效: " + min + "-" + max);
            }
            for (int p = min; p <= max; p += 2) {
                int idx = p - min;
                if (!used.get(idx)) {
                    used.set(idx);
                    return p;
                }
            }
            throw new IllegalStateException("RTP 端口已耗尽: " + min + "-" + max);
        }
    }

    public void release(int port) {
        synchronized (lock) {
            int min = props.getMedia().getRtpPortMin();
            int idx = port - min;
            if (idx >= 0 && idx < used.length()) {
                used.clear(idx);
            }
        }
    }
}
