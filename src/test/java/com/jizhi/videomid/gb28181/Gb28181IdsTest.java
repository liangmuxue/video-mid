package com.jizhi.videomid.gb28181;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gb28181IdsTest {

    @Test
    void typeCodePicksVideoAndSkipsAudio() {
        assertTrue(Gb28181Ids.isVideoChannel("34020000001320000001"));
        assertTrue(Gb28181Ids.isVideoChannel("34020000001310000001"));
        assertFalse(Gb28181Ids.isVideoChannel("34020000001370000001"));
        assertFalse(Gb28181Ids.isVideoChannel("34020000002000000001"));
        assertFalse(Gb28181Ids.isVideoChannel("34020000001180000001"));
        assertFalse(Gb28181Ids.isVideoChannel("132"));
        assertEquals("132", Gb28181Ids.typeCode("34020000001320000001"));
    }

    @Test
    void catalogKeepsVideoChannelsOnly() {
        List<Map<String, String>> items = List.of(
                Map.of("DeviceID", "34020000001320000001", "Name", "大门"),
                Map.of("DeviceID", "34020000001370000001", "Name", "音频"),
                Map.of("DeviceID", "34020000001180000001", "Name", "录像机"));
        List<Gb28181Ids.VideoChannel> channels = Gb28181Ids.videoChannels(items, "34020000001180000001");
        assertEquals(1, channels.size());
        assertEquals("34020000001320000001", channels.get(0).channelId());
        assertEquals("大门", channels.get(0).name());
    }

    @Test
    void registerIdUsedWhenCatalogHasNoVideoChannel() {
        List<Gb28181Ids.VideoChannel> channels = Gb28181Ids.videoChannels(List.of(), "34020000001320000001");
        assertEquals(1, channels.size());
        assertEquals("34020000001320000001", channels.get(0).channelId());
        assertTrue(Gb28181Ids.videoChannels(List.of(), "34020000002000000001").isEmpty());
    }

    @Test
    void audioChannelStaysOutOfVideoList() {
        assertTrue(Gb28181Ids.isAudioChannel("34020000001370000001"));
        assertFalse(Gb28181Ids.isAudioChannel("34020000001320000001"));
        List<Map<String, String>> items = List.of(
                Map.of("DeviceID", "34020000001320000001", "Name", "大门"),
                Map.of("DeviceID", "34020000001370000001", "Name", "音频"));
        assertEquals("34020000001370000001", Gb28181Ids.firstAudioChannel(items));
        assertEquals(1, Gb28181Ids.videoChannels(items, "34020000001320000001").size());
    }
}
