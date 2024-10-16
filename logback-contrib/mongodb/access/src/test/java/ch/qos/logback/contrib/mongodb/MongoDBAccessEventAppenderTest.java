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

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import ch.qos.logback.access.spi.IAccessEvent;

import com.mongodb.BasicDBObject;

/**
 * Tests for {@link MongoDBAccessEventAppender}.
 * 
 * @author Tomasz Nurkiewicz
 * @author Christian Trutz
 * @since 0.1
 */
@RunWith(MockitoJUnitRunner.class)
public class MongoDBAccessEventAppenderTest {

    // to be tested
    private MongoDBAccessEventAppender appender = null;

    @Test
    public void testTimeStamp() {
        // given
        Mockito.when(event.getTimeStamp()).thenReturn(1000L);
        // when
        final BasicDBObject dbObject = appender.toMongoDocument(event);
        // then
        assertEquals(new Date(1000L), dbObject.get("timeStamp"));
    }

    @Test
    public void testServerName() {
        // given
        Mockito.when(event.getServerName()).thenReturn("servername");
        // when
        final BasicDBObject dbObject = appender.toMongoDocument(event);
        // then
        assertEquals("servername", dbObject.get("serverName"));
    }

    @Test
    public void testRemote() {
        // given
        Mockito.when(event.getRemoteHost()).thenReturn("host");
        Mockito.when(event.getRemoteUser()).thenReturn("user");
        Mockito.when(event.getRemoteAddr()).thenReturn("addr");
        // when
        final BasicDBObject dbObject = appender.toMongoDocument(event);
        // then
        BasicDBObject remoteDBObject = (BasicDBObject) dbObject.get("remote");
        assertEquals("host", remoteDBObject.getString("host"));
        assertEquals("user", remoteDBObject.getString("user"));
        assertEquals("addr", remoteDBObject.getString("addr"));
    }

    @Test
    public void testRequest() {
        // given
        Mockito.when(event.getRequestURI()).thenReturn("uri");
        Mockito.when(event.getProtocol()).thenReturn("protocol");
        Mockito.when(event.getMethod()).thenReturn("method");
        Mockito.when(event.getRequestContent()).thenReturn("postContent");
        Mockito.when(event.getCookie("JSESSIONID")).thenReturn("sessionId");
        Mockito.when(event.getRequestHeader("User-Agent")).thenReturn("userAgent");
        Mockito.when(event.getRequestHeader("Referer")).thenReturn("referer");
        // when
        final BasicDBObject dbObject = appender.toMongoDocument(event);
        // then
        BasicDBObject requestDBObject = (BasicDBObject) dbObject.get("request");
        assertEquals("uri", requestDBObject.getString("uri"));
        assertEquals("protocol", requestDBObject.getString("protocol"));
        assertEquals("method", requestDBObject.getString("method"));
        assertEquals("postContent", requestDBObject.getString("postContent"));
        assertEquals("sessionId", requestDBObject.getString("sessionId"));
        assertEquals("userAgent", requestDBObject.getString("userAgent"));
        assertEquals("referer", requestDBObject.getString("referer"));
    }

    //
    //
    // MOCKING
    //

    @Mock
    private IAccessEvent event;

    @Before
    public void before() {
        appender = new MongoDBAccessEventAppender();
    }

}
