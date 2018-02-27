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
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * Sends the message through daemonized SpamAssassin (spamd), visit <a
 * href="SpamAssassin.org">SpamAssassin.org</a> for info on configuration.
 */
public class SpamAssassinInvoker {

    /** The mail attribute under which the status get stored */
    public static final String STATUS_MAIL_ATTRIBUTE_NAME = "org.apache.james.spamassassin.status";

    /** The mail attribute under which the flag get stored */
    public static final String FLAG_MAIL_ATTRIBUTE_NAME = "org.apache.james.spamassassin.flag";

    private static final int SPAM_INDEX = 1;
    private static final int HITS_INDEX = 3;
    private static final int REQUIRED_HITS_INDEX = 5;
    private static final String CRLF = "\r\n";

    private final String spamdHost;

    private final int spamdPort;

    /**
     * Init the spamassassin invoker
     * 
     * @param spamdHost
     *            The host on which spamd runs
     * @param spamdPort
     *            The port on which spamd listen
     */
    public SpamAssassinInvoker(String spamdHost, int spamdPort) {
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
    public SpamAssassinResult scanMail(MimeMessage message) throws MessagingException {
        try (Socket socket = new Socket(spamdHost, spamdPort);
                OutputStream out = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(out);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            writer.write("CHECK SPAMC/1.2");
            writer.write(CRLF);
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
        } catch (UnknownHostException e1) {
            throw new MessagingException("Error communicating with spamd. Unknown host: " + spamdHost);
        } catch (IOException | MessagingException e1) {
            throw new MessagingException("Error communicating with spamd on " + spamdHost + ":" + spamdPort + " Exception: " + e1);
        }
    }

    private SpamAssassinResult processSpam(String line) {
        List<String> elements = Lists.newArrayList(Splitter.on(' ').split(line));
        boolean spam = spam(elements.get(SPAM_INDEX));
        String hits = elements.get(HITS_INDEX);
        String required = elements.get(REQUIRED_HITS_INDEX);
        SpamAssassinResult.Builder builder = SpamAssassinResult.builder()
            .hits(hits)
            .requiredHits(required);

        if (spam) {
            builder.putHeader(FLAG_MAIL_ATTRIBUTE_NAME, "YES");
            builder.putHeader(STATUS_MAIL_ATTRIBUTE_NAME, "Yes, hits=" + hits + " required=" + required);
        } else {
            builder.putHeader(FLAG_MAIL_ATTRIBUTE_NAME, "NO");
            builder.putHeader(STATUS_MAIL_ATTRIBUTE_NAME, "No, hits=" + hits + " required=" + required);
        }
        return builder.build();
    }

    private boolean spam(String string) {
        try {
            return Boolean.valueOf(string);
        } catch (Exception e) {
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
    public boolean learnAsSpam(InputStream message) throws MessagingException {
        try (Socket socket = new Socket(spamdHost, spamdPort);
                OutputStream out = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(out);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            writer.write("TELL SPAMC/1.2");
            writer.write(CRLF);
            writer.write("Message-class: spam");
            writer.write(CRLF);
            writer.write("Set: local, remote");
            writer.write(CRLF);
            writer.write(CRLF);
            writer.flush();

            IOUtils.copy(message, out);
            out.flush();
            socket.shutdownOutput();

            return in.lines()
                .filter(this::hasBeenSet)
                .findAny()
                .isPresent();
        } catch (UnknownHostException e) {
            throw new MessagingException("Error communicating with spamd. Unknown host: " + spamdHost);
        } catch (IOException e) {
            throw new MessagingException("Error communicating with spamd on " + spamdHost + ":" + spamdPort + " Exception: " + e);
        }
    }

    private boolean hasBeenSet(String line) {
        return line.startsWith("DidSet");
    }
}
