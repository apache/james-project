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

package org.apache.james.util.scanner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * Sends the message through daemonized SpamAssassin (spamd), visit <a
 * href="SpamAssassin.org">SpamAssassin.org</a> for info on configuration.
 */
public class SpamAssassinInvoker {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpamAssassinInvoker.class);

    enum MessageClass {
        HAM("ham"),
        SPAM("spam");

        private final String value;

        MessageClass(String value) {
            this.value = value;
        }
    }

    private static final int SPAM_INDEX = 1;
    private static final int HITS_INDEX = 3;
    private static final int REQUIRED_HITS_INDEX = 5;
    private static final String CRLF = "\r\n";

    private final MetricFactory metricFactory;
    private final String spamdHost;
    private final int spamdPort;

    /**
     * Init the spamassassin invoker
     *
     * @param spamdHost
     *            The host on which spamd runs
     * @param spamdPort
     */
    public SpamAssassinInvoker(MetricFactory metricFactory, String spamdHost, int spamdPort) {
        this.metricFactory = metricFactory;
        this.spamdHost = spamdHost;
        this.spamdPort = spamdPort;
    }

    /**
     * Scan a MimeMessage for spam by passing it to spamd.
     * 
     * @param message
     *            The MimeMessage to scan
     * @return true if spam otherwise false
     * @throws MessagingException
     *             if an error on scanning is detected
     */
    public SpamAssassinResult scanMail(MimeMessage message, String user) throws MessagingException {
        return metricFactory.withMetric(
            "spamAssassin-check",
            Throwing.supplier(
                () -> scanMailWithAdditionalHeaders(message,
                    "User: " + user))
                .sneakyThrow());
    }

    public SpamAssassinResult scanMail(MimeMessage message) throws MessagingException {
        return metricFactory.withMetric(
            "spamAssassin-check",
            Throwing.supplier(
                () -> scanMailWithoutAdditionalHeaders(message))
            .sneakyThrow());
    }

    public SpamAssassinResult scanMailWithAdditionalHeaders(MimeMessage message, String... additionalHeaders) throws MessagingException {
        try (Socket socket = new Socket(spamdHost, spamdPort);
             OutputStream out = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(out);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            writer.write("CHECK SPAMC/1.2");
            writer.write(CRLF);

            Arrays.stream(additionalHeaders)
                .forEach(header -> {
                    writer.write(header);
                    writer.write(CRLF);
                });

            writer.write(CRLF);
            writer.flush();

            // pass the message to spamd
            message.writeTo(out);
            out.flush();
            socket.shutdownOutput();

            return in.lines()
                .filter(this::isSpam)
                .map(this::processSpam)
                .findFirst()
                .orElse(SpamAssassinResult.empty());
        } catch (UnknownHostException e) {
            throw new MessagingException("Error communicating with spamd. Unknown host: " + spamdHost);
        } catch (IOException | MessagingException e) {
            throw new MessagingException("Error communicating with spamd on " + spamdHost + ":" + spamdPort, e);
        }
    }

    public SpamAssassinResult scanMailWithoutAdditionalHeaders(MimeMessage message) throws MessagingException {
        return scanMailWithAdditionalHeaders(message);
    }

    private SpamAssassinResult processSpam(String line) {
        List<String> elements = Lists.newArrayList(Splitter.on(' ').split(line));

        return builderFrom(elements)
            .hits(elements.get(HITS_INDEX))
            .requiredHits(elements.get(REQUIRED_HITS_INDEX))
            .build();
    }

    private SpamAssassinResult.Builder builderFrom(List<String> elements) {
        if (spam(elements.get(SPAM_INDEX))) {
            return SpamAssassinResult.asSpam();
        } else {
            return SpamAssassinResult.asHam();
        }
    }

    private boolean spam(String string) {
        try {
            return Boolean.valueOf(string);
        } catch (Exception e) {
            LOGGER.warn("Fail parsing spamassassin answer: " + string);
            return false;
        }
    }

    private boolean isSpam(String line) {
        return line.startsWith("Spam:");
    }

    /**
     * Tell spamd that the given MimeMessage is a spam.
     * 
     * @param message
     *            The MimeMessage to tell
     * @throws MessagingException
     *             if an error occured during learning.
     */
    public boolean learnAsSpam(InputStream message, String user) throws MessagingException {
        return metricFactory.withMetric(
            "spamAssassin-spam-report",
            Throwing.supplier(
                () -> reportMessageAs(message, user, MessageClass.SPAM))
                .sneakyThrow());
    }

    /**
     * Tell spamd that the given MimeMessage is a ham.
     *
     * @param message
     *            The MimeMessage to tell
     * @throws MessagingException
     *             if an error occured during learning.
     */
    public boolean learnAsHam(InputStream message, String user) throws MessagingException {
        return metricFactory.withMetric(
            "spamAssassin-ham-report",
            Throwing.supplier(
                () -> reportMessageAs(message, user, MessageClass.HAM))
                .sneakyThrow());
    }

    private boolean reportMessageAs(InputStream message, String user, MessageClass messageClass) throws MessagingException {
        try (Socket socket = new Socket(spamdHost, spamdPort);
             OutputStream out = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(out);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            byte[] byteArray = IOUtils.toByteArray(message);
            writer.write("TELL SPAMC/1.2");
            writer.write(CRLF);
            writer.write("Content-length: " + byteArray.length);
            writer.write(CRLF);
            writer.write("Message-class: " + messageClass.value);
            writer.write(CRLF);
            writer.write("Set: local, remote");
            writer.write(CRLF);
            writer.write("User: " + user);
            writer.write(CRLF);
            writer.write(CRLF);
            writer.flush();

            out.write(byteArray);
            out.flush();
            socket.shutdownOutput();

            return in.lines()
                .anyMatch(this::hasBeenSet);
        } catch (UnknownHostException e) {
            throw new MessagingException("Error communicating with spamd. Unknown host: " + spamdHost);
        } catch (IOException e) {
            throw new MessagingException("Error communicating with spamd on " + spamdHost + ":" + spamdPort, e);
        }
    }

    private boolean hasBeenSet(String line) {
        return line.startsWith("DidSet: ");
    }
}
