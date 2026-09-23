package com.jizhi.videomid.uniview.live;

/** LiteAPI 视频流索引：0 主码流，1 辅码流，2 第三流。 */
public record UniviewVideoStream(int id, String streamType, String streamName) {

    public static UniviewVideoStream of(int id) {
        return switch (id) {
            case 0 -> new UniviewVideoStream(0, "main", "主码流");
            case 1 -> new UniviewVideoStream(1, "sub", "辅码流");
            case 2 -> new UniviewVideoStream(2, "third", "第三流");
            default -> new UniviewVideoStream(id, "stream" + id, "码流" + id);
        };
    }
}
