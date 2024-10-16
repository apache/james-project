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

import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.contrib.json.JsonLayoutBase;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JsonLayout builds its {@link #toJsonMap(ch.qos.logback.classic.spi.ILoggingEvent) jsonMap} from a
 * source {@link ch.qos.logback.classic.spi.ILoggingEvent ILoggingEvent} with the following keys/value pairs:
 * <p/>
 * <table>
 *     <tr>
 *         <th nowrap="nowrap">Key</th>
 *         <th nowrap="nowrap">Value</th>
 *         <th nowrap="nowrap">Notes</th>
 *         <th nowrap="nowrap">Enabled by default?</th>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code timestamp}</td>
 *         <td nowrap="nowrap">String value of <code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getTimeStamp() getTimeStamp()}</code></td>
 *         <td>By default, the value is not formatted; it is simply {@code String.valueOf(timestamp)}.  To format
 *         the string using a SimpleDateFormat, set the {@link #setTimestampFormat(String) timestampFormat}
 *         property with the corresponding SimpleDateFormat string, for example, {@code yyyy-MM-dd HH:mm:ss.SSS}</td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code level}</td>
 *         <td nowrap="nowrap">String value of <code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getLevel() getLevel()}</code></td>
 *         <td><code>String.valueOf(event.getLevel());</code></td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code thread}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getThreadName() getThreadName()}</code></td>
 *         <td></td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code mdc}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getMDCPropertyMap() getMDCPropertyMap()}</code></td>
 *         <td>Unlike the other values which are all Strings, this value is a {@code Map&lt;String,String&gt;}.  If there is no
 *         MDC, this property will not be added to the JSON map.</td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code thread}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getLoggerName() getLoggerName()}</code></td>
 *         <td></td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code message}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getFormattedMessage() getFormattedMessage()}</code></td>
 *         <td>This is the <em>formatted</em> message.  The raw (unformatted) message is available as {@code raw-message}.
 *         Most people will want the formatted message as the raw message does not reflect any log message arguments.</td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code raw-message}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getMessage() getMessage()}</code></td>
 *         <td></td>
 *         <td>false</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code exception}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getThrowableProxy() getThrowableProxy()}</code></td>
 *         <td>If there is no exception, this property will not be added to the JSON map.  If there is an exception, it
 *             will be formatted to a String first via a {@link ch.qos.logback.classic.pattern.ThrowableProxyConverter ThrowableProxyConverter}.</td>
 *         <td>true</td>
 *     </tr>
 *     <tr>
 *         <td nowrap="nowrap">{@code context}</td>
 *         <td nowrap="nowrap"><code>ILoggingEvent.{@link ch.qos.logback.classic.spi.ILoggingEvent#getLoggerContextVO() getLoggerContextVO()}</code></td>
 *         <td>The name of the logger context. Defaults to <em>default</em>.</td>
 *         <td>true</td>
 *     </tr>
 * </table>
 * <p/>
 * The constructed Map will be serialized to JSON via the parent class's {@link #getJsonFormatter() jsonFormatter}.
 *
 * @author Les Hazlewood
 * @author Pierre Queinnec
 * @author Espen A. Fossen
 * @since 0.1
 */
public class JsonLayout extends JsonLayoutBase<ILoggingEvent> {

    public static final String TIMESTAMP_ATTR_NAME = "timestamp";
    public static final String LEVEL_ATTR_NAME = "level";
    public static final String THREAD_ATTR_NAME = "thread";
    public static final String MDC_ATTR_NAME = "mdc";
    public static final String LOGGER_ATTR_NAME = "logger";
    public static final String FORMATTED_MESSAGE_ATTR_NAME = "message";
    public static final String MESSAGE_ATTR_NAME = "raw-message";
    public static final String EXCEPTION_ATTR_NAME = "exception";
    public static final String CONTEXT_ATTR_NAME = "context";

    protected boolean includeLevel;
    protected boolean includeThreadName;
    protected boolean includeMDC;
    protected boolean includeLoggerName;
    protected boolean includeFormattedMessage;
    protected boolean includeMessage;
    protected boolean includeException;
    protected boolean includeContextName;

    private ThrowableHandlingConverter throwableProxyConverter;

    public JsonLayout() {
        super();
        this.includeLevel = true;
        this.includeThreadName = true;
        this.includeMDC = true;
        this.includeLoggerName = true;
        this.includeFormattedMessage = true;
        this.includeException = true;
        this.includeContextName = true;
        this.throwableProxyConverter = new ThrowableProxyConverter();
    }

    @Override
    public void start() {
        this.throwableProxyConverter.start();
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
        this.throwableProxyConverter.stop();
    }

    @Override
    protected Map toJsonMap(ILoggingEvent event) {

        Map<String, Object> map = new LinkedHashMap<String, Object>();

        addTimestamp(TIMESTAMP_ATTR_NAME, this.includeTimestamp, event.getTimeStamp(), map);
        add(LEVEL_ATTR_NAME, this.includeLevel, String.valueOf(event.getLevel()), map);
        add(THREAD_ATTR_NAME, this.includeThreadName, event.getThreadName(), map);
        addMap(MDC_ATTR_NAME, this.includeMDC, event.getMDCPropertyMap(), map);
        add(LOGGER_ATTR_NAME, this.includeLoggerName, event.getLoggerName(), map);
        add(FORMATTED_MESSAGE_ATTR_NAME, this.includeFormattedMessage, event.getFormattedMessage(), map);
        add(MESSAGE_ATTR_NAME, this.includeMessage, event.getMessage(), map);
        add(CONTEXT_ATTR_NAME, this.includeContextName, event.getLoggerContextVO().getName(), map);
        addThrowableInfo(EXCEPTION_ATTR_NAME, this.includeException, event, map);
        addCustomDataToJsonMap(map, event);
        return map;
    }

    protected void addThrowableInfo(String fieldName, boolean field, ILoggingEvent value, Map<String, Object> map) {
        if (field && value != null) {
            IThrowableProxy throwableProxy = value.getThrowableProxy();
            if (throwableProxy != null) {
                String ex = throwableProxyConverter.convert(value);
                if (ex != null && !ex.equals("")) {
                    map.put(fieldName, ex);
                }
            }
        }
    }

    /**
     * Override to add custom data to the produced JSON from the logging event.
     * Useful if you e.g. want to include the parameter array as a separate json attribute.
     *
     * @param map the map for JSON serialization, populated with data corresponding to the
     *            configured attributes. Add new entries from the event to this map to have
     *            them included in the produced JSON.
     * @param event the logging event to extract data from.
     */
    protected void addCustomDataToJsonMap(Map<String, Object> map, ILoggingEvent event) {
        // Nothing to do in default implementation
    }

    public boolean isIncludeLevel() {
        return includeLevel;
    }

    public void setIncludeLevel(boolean includeLevel) {
        this.includeLevel = includeLevel;
    }

    public boolean isIncludeLoggerName() {
        return includeLoggerName;
    }

    public void setIncludeLoggerName(boolean includeLoggerName) {
        this.includeLoggerName = includeLoggerName;
    }

    public boolean isIncludeFormattedMessage() {
        return includeFormattedMessage;
    }

    public void setIncludeFormattedMessage(boolean includeFormattedMessage) {
        this.includeFormattedMessage = includeFormattedMessage;
    }

    public boolean isIncludeMessage() {
        return includeMessage;
    }

    public void setIncludeMessage(boolean includeMessage) {
        this.includeMessage = includeMessage;
    }

    public boolean isIncludeMDC() {
        return includeMDC;
    }

    public void setIncludeMDC(boolean includeMDC) {
        this.includeMDC = includeMDC;
    }

    public boolean isIncludeThreadName() {
        return includeThreadName;
    }

    public void setIncludeThreadName(boolean includeThreadName) {
        this.includeThreadName = includeThreadName;
    }

    public boolean isIncludeException() {
        return includeException;
    }

    public void setIncludeException(boolean includeException) {
        this.includeException = includeException;
    }

    public boolean isIncludeContextName() {
        return includeContextName;
    }

    public void setIncludeContextName(boolean includeContextName) {
        this.includeContextName = includeContextName;
    }

    public ThrowableHandlingConverter getThrowableProxyConverter() {
        return throwableProxyConverter;
    }

    public void setThrowableProxyConverter(ThrowableHandlingConverter throwableProxyConverter) {
        this.throwableProxyConverter = throwableProxyConverter;
    }
}
