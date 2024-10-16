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
package ch.qos.logback.contrib.mongodb;

import java.util.Date;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

/**
 * @author Christian Trutz
 * @author Tomasz Nurkiewicz
 * @since 0.1
 */
public class MongoDBLoggingEventAppender extends MongoDBAppenderBase<ILoggingEvent> {

    private boolean includeCallerData;

    @Override
    protected BasicDBObject toMongoDocument(ILoggingEvent event) {
        BasicDBObject logEntry = new BasicDBObject();
        logEntry.append("message", event.getFormattedMessage());
        logEntry.append("logger", event.getLoggerName());
        logEntry.append("thread", event.getThreadName());
        logEntry.append("timestamp", new Date(event.getTimeStamp()));
        logEntry.append("level", event.getLevel().toString());
        if (event.getMDCPropertyMap() != null && !event.getMDCPropertyMap().isEmpty()) {
            logEntry.append("mdc", event.getMDCPropertyMap());
        }
        if (includeCallerData) {
            logEntry.append("callerData", toDocument(event.getCallerData()));
        }
        if (event.getArgumentArray() != null && event.getArgumentArray().length > 0) {
            logEntry.append("arguments", event.getArgumentArray());
        }
        appendThrowableIfAvailable(logEntry, event);
        return logEntry;
    }

    private BasicDBList toDocument(StackTraceElement[] callerData) {
        final BasicDBList dbList = new BasicDBList();
        for (final StackTraceElement ste : callerData) {
            dbList.add(
                    new BasicDBObject()
                            .append("file", ste.getFileName())
                            .append("class", ste.getClassName())
                            .append("method", ste.getMethodName())
                            .append("line", ste.getLineNumber())
                            .append("native", ste.isNativeMethod()));
        }
        return dbList;
    }

    private void appendThrowableIfAvailable(BasicDBObject doc, ILoggingEvent event) {
        if (event.getThrowableProxy() != null) {
            final BasicDBObject val = toMongoDocument(event.getThrowableProxy());
            doc.append("throwable", val);
        }
    }

    private BasicDBObject toMongoDocument(IThrowableProxy throwable) {
        final BasicDBObject throwableDoc = new BasicDBObject();
        throwableDoc.append("class", throwable.getClassName());
        throwableDoc.append("message", throwable.getMessage());
        throwableDoc.append("stackTrace", toSteArray(throwable));
        if (throwable.getCause() != null) {
            throwableDoc.append("cause", toMongoDocument(throwable.getCause()));
        }
        return throwableDoc;
    }

    private String[] toSteArray(IThrowableProxy throwableProxy) {
        final StackTraceElementProxy[] elementProxies = throwableProxy.getStackTraceElementProxyArray();
        final int totalFrames = elementProxies.length - throwableProxy.getCommonFrames();
        final String[] stackTraceElements = new String[totalFrames];
        for (int i = 0; i < totalFrames; ++i)
            stackTraceElements[i] = elementProxies[i].getStackTraceElement().toString();
        return stackTraceElements;
    }

    public void setIncludeCallerData(boolean includeCallerData) {
        this.includeCallerData = includeCallerData;
    }

}
