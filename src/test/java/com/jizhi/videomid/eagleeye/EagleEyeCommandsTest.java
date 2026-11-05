package com.jizhi.videomid.eagleeye;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EagleEyeCommandsTest {

    @Test
    void directionUsesProtocolNames() {
        assertEquals("up", EagleEyeCommands.direction("up"));
        assertEquals("upleft", EagleEyeCommands.direction("left_up"));
        assertEquals("upright", EagleEyeCommands.direction("RIGHT_UP"));
        assertEquals("downleft", EagleEyeCommands.direction("left_down"));
        assertEquals("downright", EagleEyeCommands.direction("right_down"));
        assertNull(EagleEyeCommands.direction("stop"));
    }

    @Test
    void zoomAndFocusKeepProtocolValues() {
        assertEquals("zoom_in", EagleEyeCommands.zoom("in"));
        assertEquals("zoom_out", EagleEyeCommands.zoom("wide"));
        assertEquals("focus_near", EagleEyeCommands.focus("near"));
        assertEquals("focus_far", EagleEyeCommands.focus("focus_far"));
    }

    @Test
    void speedStaysInsideSubsystemRange() {
        assertEquals(1, EagleEyeCommands.speed(0));
        assertEquals(4, EagleEyeCommands.speed(4));
        assertEquals(7, EagleEyeCommands.speed(8));
    }

    @Test
    void extendedCommandsKeepProtocolParams() {
        assertEquals(Map.of("autoFocus", false),
                EagleEyeCommands.params("camera_autofocus", Map.of("autoFocus", false)));
        assertEquals(Map.of("switch", "on"),
                EagleEyeCommands.params("autoScan_switch", Map.of("switch", "ON")));
        assertEquals(Map.of("control", "pause"),
                EagleEyeCommands.params("autoScan_control", Map.of("control", "pause")));
        assertEquals(Map.of("switch", "off"),
                EagleEyeCommands.params("detectMode_switch", Map.of("switch", "off")));

        Map<String, Object> times = new LinkedHashMap<>();
        times.put("beginTimeFirst", "08_00_00");
        times.put("endTimeFirst", "12_00_00");
        times.put("beginTimeSecond", "14_00_00");
        times.put("endTimeSecond", "18_30_00");
        assertEquals(times, EagleEyeCommands.params("regularTime_timeSet", times));

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("monday", true);
        days.put("tuesday", false);
        days.put("wednesday", true);
        days.put("thursday", false);
        days.put("friday", true);
        days.put("saturday", false);
        days.put("sunday", true);
        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("building", "32");
        scan.put("floor", 5);
        scan.put("repeatTimes", 2);
        assertEquals(days, EagleEyeCommands.params("regularTime_daySet", days));
        assertEquals(scan, EagleEyeCommands.params("specialScan_control", scan));
    }

    @Test
    void extendedCommandsRejectUnknownOrBadParams() {
        assertThrows(IllegalArgumentException.class,
                () -> EagleEyeCommands.params("ptz_direction", Map.of("direction", "up")));
        assertThrows(IllegalArgumentException.class,
                () -> EagleEyeCommands.params("regularTime_timeSet", Map.of("beginTimeFirst", "8:00:00")));
        assertThrows(IllegalArgumentException.class,
                () -> EagleEyeCommands.params("specialScan_control", Map.of("building", "32", "floor", 1.5, "repeatTimes", 2)));
    }

    @Test
    void terminalIdDefaultsToDeviceId() {
        EagleEyeProperties properties = new EagleEyeProperties();
        assertEquals("YY01", properties.terminalIdOf("YY01"));
        EagleEyeProperties.Terminal terminal = new EagleEyeProperties.Terminal();
        terminal.setDeviceId("cam-1");
        terminal.setTerminalId("YY02");
        properties.setTerminals(java.util.List.of(terminal));
        assertEquals("YY02", properties.terminalIdOf("cam-1"));
    }
}
