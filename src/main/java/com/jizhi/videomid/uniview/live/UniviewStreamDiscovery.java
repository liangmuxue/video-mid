package com.jizhi.videomid.uniview.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.jizhi.videomid.device.Device;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 向摄像机查询当前通道实际启用的视频流。 */
@Service
public class UniviewStreamDiscovery {

    private final UniviewLapiClient lapiClient;

    public UniviewStreamDiscovery(UniviewLapiClient lapiClient) {
        this.lapiClient = lapiClient;
    }

    public List<UniviewVideoStream> listEnabled(Device device) {
        String channel = device.getAccessChannel() == null || device.getAccessChannel().isBlank()
                ? "0" : device.getAccessChannel().trim();
        String path = "/LAPI/V1.0/Channels/" + channel + "/Media/Video/Streams/DetailInfos";
        JsonNode root;
        try {
            root = lapiClient.get(LapiEndpoint.from(device), path);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("401") || message.toLowerCase().contains("unauthorized")) {
                throw new IllegalArgumentException("摄像机认证失败，请检查用户名和密码");
            }
            throw new IllegalArgumentException("无法连接摄像机，请检查 IP、端口和白名单");
        }
        int code = root.path("Response").path("ResponseCode").asInt(-1);
        if (code != 0) {
            throw new IllegalArgumentException("查询码流失败");
        }
        JsonNode infos = root.path("Response").path("Data").path("VideoStreamInfos");
        if (!infos.isArray() || infos.isEmpty()) {
            throw new IllegalArgumentException("摄像机没有返回码流");
        }
        Set<String> seen = new LinkedHashSet<>();
        List<UniviewVideoStream> streams = new ArrayList<>();
        for (JsonNode node : infos) {
            if (node.has("Enabled") && node.path("Enabled").asInt(1) != 1) {
                continue;
            }
            int id = node.path("ID").asInt(-1);
            if (id < 0) {
                continue;
            }
            UniviewVideoStream stream = UniviewVideoStream.of(id);
            if (seen.add(stream.streamType())) {
                streams.add(stream);
            }
        }
        streams.sort(Comparator.comparingInt(UniviewVideoStream::id));
        if (streams.isEmpty()) {
            throw new IllegalArgumentException("摄像机没有启用的码流");
        }
        return streams;
    }
}
