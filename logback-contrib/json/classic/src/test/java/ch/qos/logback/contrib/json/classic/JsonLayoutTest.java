/**
 * Copyright (C) 2016, The logback-contrib developers. All rights reserved.
 *
 * This program and the accompanying materials are dual-licensed under
 * either the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation
 *
 *   or (per the licensee's choosing)
 *
 * under the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation.
 */
package ch.qos.logback.contrib.json.classic;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.joran.spi.JoranException;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.containsString;

/**
 * Tests the {@link JsonLayout} class
 *
 * @author Espen A. Fossen
 */
public class JsonLayoutTest {

    private LoggerContext context = new LoggerContext();

    private void configure(String file) throws JoranException {
        context.reset();
        JoranConfigurator jc = new JoranConfigurator();
        jc.setContext(context);
        jc.doConfigure(file);
    }

    @Test
    public void addToJsonMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        JsonLayout jsonLayout = new JsonLayout();
        jsonLayout.add("key1", true, "value1", map);
        jsonLayout.add("key2", false, "value2", map);
        jsonLayout.add("key3", true, null, map);

        assertThat(map.size(), is(1));
        assertThat(map, hasKey("key1"));
        assertEquals(map.get("key1"), "value1");
        assertThat(map, not(hasKey("key2")));
        assertThat(map, not(hasKey("key3")));
    }

    @Test
    public void addTimestampToJsonMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        JsonLayout jsonLayout = new JsonLayout();
        jsonLayout.setTimestampFormat("yyyy-MM-dd HH:mm:ss.SSS");
        jsonLayout.addTimestamp("key1", true, 1, map);
        jsonLayout.addTimestamp("key2", false, 1, map);
        jsonLayout.addTimestamp("key3", true, -1, map);

        assertThat(map.size(), is(2));
        assertThat(map, hasKey("key1"));
        assertThat(map, not(hasKey("key2")));
        assertThat(map, hasKey("key3"));
        assertEquals("-1", map.get("key3"));
    }

    @Test
    public void addMapToJsonMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();

        Map<String, String> emptyMap = new HashMap<String, String>();
        Map<String, String> mapWithData = new HashMap<String, String>();
        mapWithData.put("mapKey1", "mapValue1");
        Map<String, String[]> mapWithArrayValue = new HashMap<String, String[]>();
        mapWithArrayValue.put("mapKey1", new String[]{"mapValue1","mapValue2","mapValue3"});

        JsonLayout jsonLayout = new JsonLayout();
        jsonLayout.addMap("key1", true, emptyMap, map);
        jsonLayout.addMap("key2", true, mapWithData, map);
        jsonLayout.addMap("key3", true, mapWithArrayValue, map);
        jsonLayout.addMap("key4", false, mapWithArrayValue, map);

        assertThat(map.size(), is(2));
        assertThat(map, not(hasKey("key1")));
        assertEquals(mapWithData, map.get("key2"));
        assertEquals(mapWithArrayValue, map.get("key3"));
        assertThat(map, not(hasKey("key4")));
    }

    @Test
    public void jsonLayout() throws Exception {
        configure("src/test/input/json/jsonLayout.xml");
        String loggerName = "ROOT";
        String message = "Info message";
        String debugMessage = "Debug message";
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.info("Test");
        ILoggingEvent event = new LoggingEvent("my.class.name", logger, Level.INFO, message, null, null);

        JsonLayout jsonLayout = new JsonLayout();
        jsonLayout.setContext(context);
        String log = jsonLayout.doLayout(event);

        assertTimestamp(log);
        assertThat(log, containsString(String.format("%s=%s", JsonLayout.LEVEL_ATTR_NAME, Level.INFO)));
        assertThat(log, containsString(String.format("%s=%s", JsonLayout.THREAD_ATTR_NAME, "main")));
        assertThat(log, containsString(String.format("%s=%s", JsonLayout.LOGGER_ATTR_NAME, loggerName)));
        assertThat(log, containsString(String.format("%s=%s", JsonLayout.FORMATTED_MESSAGE_ATTR_NAME, message)));

        jsonLayout.setIncludeContextName(true);
        jsonLayout.setIncludeMDC(true);
        jsonLayout.setIncludeLoggerName(true);
        jsonLayout.setIncludeException(true);
        jsonLayout.setIncludeMessage(true);

        RuntimeException exception = new RuntimeException("Exception");
        ILoggingEvent eventWithException = new LoggingEvent("my.class.name", logger, Level.DEBUG, debugMessage, exception, null);
        String logWithException = jsonLayout.doLayout(eventWithException);

        assertTimestamp(logWithException);
        assertThat(logWithException, containsString(String.format("%s=%s", JsonLayout.LEVEL_ATTR_NAME, Level.DEBUG)));
        assertThat(logWithException, containsString(String.format("%s=%s", JsonLayout.LOGGER_ATTR_NAME, loggerName)));
        assertThat(logWithException, containsString(String.format("%s=%s", JsonLayout.FORMATTED_MESSAGE_ATTR_NAME, debugMessage)));
        assertThat(logWithException, containsString(String.format("%s=%s", JsonLayout.MESSAGE_ATTR_NAME, debugMessage)));
        assertThat(logWithException, containsString(String.format("%s=%s", JsonLayout.EXCEPTION_ATTR_NAME, exception.toString())));
    }

    private void assertTimestamp(String log) {
        int timestamp = log.indexOf(JsonLayout.TIMESTAMP_ATTR_NAME);
        if(timestamp == -1){
            fail(String.format("No instance of %s found in log, there should be one.", JsonLayout.TIMESTAMP_ATTR_NAME));
        }

        int timestampStart = timestamp + JsonLayout.TIMESTAMP_ATTR_NAME.length() + 1;
        int timestampEnd = log.indexOf(",", timestampStart);
        String timestampValue = log.substring(timestampStart, timestampEnd);
        try {
            new Date(Long.parseLong(timestampValue));
        } catch (NumberFormatException e) {
            fail(String.format("Value of attribute %s could not be converted to a valid Date", JsonLayout.TIMESTAMP_ATTR_NAME));
        }
    }

}
