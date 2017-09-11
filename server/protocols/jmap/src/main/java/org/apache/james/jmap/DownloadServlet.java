/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.jmap;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import java.io.IOException;
import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.james.jmap.api.SimpleTokenFactory;
import org.apache.james.jmap.utils.DownloadPath;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.BlobNotFoundException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Blob;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.mime4j.codec.EncoderUtil;
import org.apache.james.mime4j.codec.EncoderUtil.Usage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;

public class DownloadServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadServlet.class);
    private static final String TEXT_PLAIN_CONTENT_TYPE = "text/plain";

    private final BlobManager blobManager;
    private final SimpleTokenFactory simpleTokenFactory;
    private final MetricFactory metricFactory;

    @Inject
    @VisibleForTesting DownloadServlet(BlobManager blobManager, SimpleTokenFactory simpleTokenFactory, MetricFactory metricFactory) {
        this.blobManager = blobManager;
        this.simpleTokenFactory = simpleTokenFactory;
        this.metricFactory = metricFactory;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        TimeMetric timeMetric = metricFactory.timer("JMAP-download-post");
        String pathInfo = req.getPathInfo();
        try {
            respondAttachmentAccessToken(getMailboxSession(req), DownloadPath.from(pathInfo), resp);
        } catch (IllegalArgumentException e) {
            LOGGER.error(String.format("Error while generating attachment access token '%s'", pathInfo), e);
            resp.setStatus(SC_BAD_REQUEST);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private void respondAttachmentAccessToken(MailboxSession mailboxSession, DownloadPath downloadPath, HttpServletResponse resp) {
        TimeMetric timeMetric = metricFactory.timer("JMAP-download-get");
        String blobId = downloadPath.getBlobId();
        try {
            if (! attachmentExists(mailboxSession, blobId)) {
                resp.setStatus(SC_NOT_FOUND);
                return;
            }
            resp.setContentType(TEXT_PLAIN_CONTENT_TYPE);
            resp.getOutputStream().print(simpleTokenFactory.generateAttachmentAccessToken(mailboxSession.getUser().getUserName(), blobId).serialize());
            resp.setStatus(SC_OK);
        } catch (MailboxException | IOException e) {
            LOGGER.error("Error while asking attachment access token", e);
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
        } finally {
            timeMetric.stopAndPublish();
        }
    }

    private boolean attachmentExists(MailboxSession mailboxSession, String blobId) throws MailboxException {
        try {
            blobManager.retrieve(BlobId.fromString(blobId), mailboxSession);
            return true;
        } catch (BlobNotFoundException e) {
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        String pathInfo = req.getPathInfo();
        try {
            download(getMailboxSession(req), DownloadPath.from(pathInfo), resp);
        } catch (IllegalArgumentException e) {
            LOGGER.error(String.format("Error while downloading '%s'", pathInfo), e);
            resp.setStatus(SC_BAD_REQUEST);
        }
    }

    @VisibleForTesting void download(MailboxSession mailboxSession, DownloadPath downloadPath, HttpServletResponse resp) {
        String blobId = downloadPath.getBlobId();
        try {
            addContentDispositionHeader(downloadPath.getName(), resp);

            Blob blob = blobManager.retrieve(BlobId.fromString(blobId), mailboxSession);
            IOUtils.copy(blob.getStream(), resp.getOutputStream());

            resp.setHeader("Content-Length", String.valueOf(blob.getSize()));
            resp.setStatus(SC_OK);
        } catch (BlobNotFoundException e) {
            LOGGER.info(String.format("Attachment '%s' not found", blobId), e);
            resp.setStatus(SC_NOT_FOUND);
        } catch (MailboxException | IOException e) {
            LOGGER.error("Error while downloading", e);
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void addContentDispositionHeader(Optional<String> optionalName, HttpServletResponse resp) {
        optionalName.ifPresent(name -> addContentDispositionHeaderRegardingEncoding(name, resp));
    }

    private void addContentDispositionHeaderRegardingEncoding(String name, HttpServletResponse resp) {
        if (CharMatcher.ASCII.matchesAllOf(name)) {
            resp.addHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
        } else {
            resp.addHeader("Content-Disposition", "attachment; filename*=\"" + EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN) + "\"");
        }
    }

    private MailboxSession getMailboxSession(HttpServletRequest req) {
        return (MailboxSession) req.getAttribute(AuthenticationFilter.MAILBOX_SESSION);
    }
}
