package com.batz.syncwatch;
import org.junit.Test;
import static org.junit.Assert.*;
public class SubtitleTimingTest {
    @Test public void shiftsSrtWithoutChangingText() {
        assertEquals("1\n00:00:02,000 --> 00:00:04,000\nText 00:00:01,500\n",
                SubtitleTiming.shift("1\n00:00:01,500 --> 00:00:03,500\nText 00:00:01,500\n", 500));
    }
    @Test public void supportsVttSettingsShortTimesAndNegativeOffset() {
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.000 align:start\nOlá\n",
                SubtitleTiming.shift("WEBVTT\n\n00:00.200 --> 00:02.000 align:start\nOlá\n", -1000));
    }
    @Test public void handlesHourRollover() {
        assertEquals("01:00:00,000 --> 01:00:01,000", SubtitleTiming.shift("00:59:59,500 --> 01:00:00,500", 500));
    }
}
