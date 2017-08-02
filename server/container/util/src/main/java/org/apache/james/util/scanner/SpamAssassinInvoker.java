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
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import com.google.common.io.Closeables;

/**
 * Sends the message through daemonized SpamAssassin (spamd), visit <a
 * href="SpamAssassin.org">SpamAssassin.org</a> for info on configuration.
 */
public class SpamAssassinInvoker {

    /** The mail attribute under which the status get stored */
    public final static String STATUS_MAIL_ATTRIBUTE_NAME = "org.apache.james.spamassassin.status";

    /** The mail attribute under which the flag get stored */
    public final static String FLAG_MAIL_ATTRIBUTE_NAME = "org.apache.james.spamassassin.flag";

    private final String spamdHost;

    private final int spamdPort;

    private String hits = "?";

    private String required = "?";

    private final Map<String, String> headers = new HashMap<>();

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
    public boolean scanMail(MimeMessage message) throws MessagingException {
        Socket socket = null;
        OutputStream out = null;
        BufferedReader in = null;

        try {
            socket = new Socket(spamdHost, spamdPort);

            out = socket.getOutputStream();
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.write("CHECK SPAMC/1.2\r\n\r\n".getBytes());

            // pass the message to spamd
            message.writeTo(out);
            out.flush();
            socket.shutdownOutput();
            String s;
            while ((s = in.readLine()) != null) {
                if (s.startsWith("Spam:")) {
                    StringTokenizer t = new StringTokenizer(s, " ");
                    boolean spam;
                    try {
                        t.nextToken();
                        spam = Boolean.valueOf(t.nextToken());
                    } catch (Exception e) {
                        // On exception return flase
                        return false;
                    }
                    t.nextToken();
                    hits = t.nextToken();
                    t.nextToken();
                    required = t.nextToken();

                    if (spam) {
                        // message was spam
                        headers.put(FLAG_MAIL_ATTRIBUTE_NAME, "YES");
                        headers.put(STATUS_MAIL_ATTRIBUTE_NAME, "Yes, hits=" + hits + " required=" + required);

                        // spam detected
                        return true;
                    } else {
                        // add headers
                        headers.put(FLAG_MAIL_ATTRIBUTE_NAME, "NO");
                        headers.put(STATUS_MAIL_ATTRIBUTE_NAME, "No, hits=" + hits + " required=" + required);

                        return false;
                    }
                }
            }
            return false;
        } catch (UnknownHostException e1) {
            throw new MessagingException("Error communicating with spamd. Unknown host: " + spamdHost);
        } catch (IOException e1) {
            throw new MessagingException("Error communicating with spamd on " + spamdHost + ":" + spamdPort + " Exception: " + e1);
        } catch (MessagingException e1) {
            throw new MessagingException("Error communicating with spamd on " + spamdHost + ":" + spamdPort + " Exception: " + e1);
        } finally {
            try {
                Closeables.close(in, true);
                Closeables.close(out, true);
                socket.close();
            } catch (Exception e) {
                // Should never happen
            }

        }
    }

    /**
     * Return the hits which was returned by spamd
     * 
     * @return hits The hits which was detected
     */
    public String getHits() {
        return hits;
    }

    /**
     * Return the required hits
     * 
     * @return required The required hits before a message is handled as spam
     */
    public String getRequiredHits() {
        return required;
    }

    /**
     * Return the headers as attributes which spamd generates
     * 
     * @return headers Map of headers to add as attributes
     */
    public Map<String, String> getHeadersAsAttribute() {
        return headers;
    }
}
