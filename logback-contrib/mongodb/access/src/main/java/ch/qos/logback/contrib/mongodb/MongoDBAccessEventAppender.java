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

import ch.qos.logback.access.spi.IAccessEvent;

import com.mongodb.BasicDBObject;

/**
 * A {@link MongoDBAppenderBase} handling {@link IAccessEvent}s.
 * 
 * @author Tomasz Nurkiewicz
 * @author Christian Trutz
 * @since 0.1
 */
public class MongoDBAccessEventAppender extends MongoDBAppenderBase<IAccessEvent> {

    // configuration parameters
    private boolean serverName = true;
    private boolean requestUri = true;
    private boolean requestProtocol = true;
    private boolean requestMethod = true;
    private boolean requestPostContent = true;
    private boolean requestSessionId = true;
    private boolean requestUserAgent = true;
    private boolean requestReferer = true;
    private boolean remoteHost = true;
    private boolean remoteUser = true;
    private boolean remoteAddr = true;
    private boolean responseContentLength = true;
    private boolean responseStatusCode = true;

    /**
     * {@inheritDoc}
     */
    @Override
    protected BasicDBObject toMongoDocument(IAccessEvent event) {
        final BasicDBObject doc = new BasicDBObject();
        doc.append("timeStamp", new Date(event.getTimeStamp()));
        if (serverName)
            doc.append("serverName", event.getServerName());
        addRemote(doc, event);
        addRequest(doc, event);
        addResponse(doc, event);
        return doc;
    }

    /*
     * Add remote MongoDB sub-document.
     */
    private void addRemote(BasicDBObject parent, IAccessEvent event) {
        final BasicDBObject remote = new BasicDBObject();
        final String host = event.getRemoteHost();
        if (remoteHost && host != null)
            remote.append("host", host);
        final String remoteUserName = event.getRemoteUser();
        if (remoteUser && remoteUserName != null && !remoteUserName.equals("-")) {
            remote.append("user", remoteUserName);
        }
        final String addr = event.getRemoteAddr();
        if (remoteAddr && addr != null)
            remote.append("addr", addr);
        if (!remote.isEmpty())
            parent.put("remote", remote);
    }

    /*
     * Add request MongoDB sub-document.
     */
    private void addRequest(BasicDBObject parent, IAccessEvent event) {
        final BasicDBObject request = new BasicDBObject();
        final String uri = event.getRequestURI();
        if (requestUri && uri != null && !uri.equals("-")) {
            request.append("uri", uri);
        }
        final String protocol = event.getProtocol();
        if (requestProtocol && protocol != null)
            request.append("protocol", protocol);
        final String method = event.getMethod();
        if (requestMethod && method != null)
            request.append("method", method);
        final String requestContent = event.getRequestContent();
        if (requestPostContent && requestContent != null && !requestContent.equals("")) {
            request.append("postContent", requestContent);
        }
        final String jSessionId = event.getCookie("JSESSIONID");
        if (requestSessionId && jSessionId != null && !jSessionId.equals("-"))
            request.append("sessionId", jSessionId);
        final String userAgent = event.getRequestHeader("User-Agent");
        if (requestUserAgent && userAgent != null && !userAgent.equals("-"))
            request.append("userAgent", userAgent);
        final String referer = event.getRequestHeader("Referer");
        if (requestReferer && referer != null && !referer.equals("-"))
            request.append("referer", referer);
        if (!request.isEmpty())
            parent.put("request", request);
    }

    /*
     * Add response MongoDB sub-document.
     */
    private void addResponse(BasicDBObject doc, IAccessEvent event) {
        final BasicDBObject response = new BasicDBObject();
        if (responseContentLength)
            response.append("contentLength", event.getContentLength());
        if (responseStatusCode)
            response.append("statusCode", event.getStatusCode());
        if (!response.isEmpty())
            doc.append("response", response);
    }

    //
    // Setter for configuration parameters ...
    //

    public void setServerName(boolean serverName) {
        this.serverName = serverName;
    }

    public void setRequestUri(boolean requestUri) {
        this.requestUri = requestUri;
    }

    public void setRequestProtocol(boolean requestProtocol) {
        this.requestProtocol = requestProtocol;
    }

    public void setRequestMethod(boolean requestMethod) {
        this.requestMethod = requestMethod;
    }

    public void setRequestPostContent(boolean requestPostContent) {
        this.requestPostContent = requestPostContent;
    }

    public void setRequestSessionId(boolean requestSessionId) {
        this.requestSessionId = requestSessionId;
    }

    public void setRequestUserAgent(boolean requestUserAgent) {
        this.requestUserAgent = requestUserAgent;
    }

    public void setRequestReferer(boolean requestReferer) {
        this.requestReferer = requestReferer;
    }

    public void setRemoteHost(boolean remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRemoteUser(boolean remoteUser) {
        this.remoteUser = remoteUser;
    }

    public void setRemoteAddr(boolean remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public void setResponseContentLength(boolean responseContentLength) {
        this.responseContentLength = responseContentLength;
    }

    public void setResponseStatusCode(boolean responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }

}
