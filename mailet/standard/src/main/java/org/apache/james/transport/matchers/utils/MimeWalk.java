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

package org.apache.james.transport.matchers.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.StringTokenizer;

import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.codec.DecoderUtil;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MimeWalk {
    private static final Logger LOGGER = LoggerFactory.getLogger(MimeWalk.class);
    /** Unzip request parameter. */
    protected static final String UNZIP_REQUEST_PARAMETER = "-z";

    /** Debug request parameter. */
    protected static final String DEBUG_REQUEST_PARAMETER = "-d";

    @FunctionalInterface
    public interface PartMatch {
        boolean partMatch(Part part) throws MessagingException, IOException;
    }

    public static class Mask {
        /** true if the mask starts with a wildcard asterisk */
        private boolean suffixMatch;

        /** file name mask not including the wildcard asterisk */
        private String matchString;

        public boolean match(String fileName) {
            //XXX: file names in mail may contain directory - theoretically
            if (this.suffixMatch) {
                return fileName.endsWith(this.matchString);
            } else {
                return fileName.equals(this.matchString);
            }
        }

        public String getMatchString() {
            return matchString;
        }
    }

    public record Configuration(Mask[] masks, boolean isDebug, boolean unzipIsRequested) {
        public static Configuration DEFAULT = new Configuration(null, false, false);

        public static Configuration parse(String condition) {
            StringTokenizer st = new StringTokenizer(condition, ", ", false);
            ArrayList<Mask> theMasks = new ArrayList<>(20);
            boolean unzipIsRequested = false;
            boolean isDebug = false;
            while (st.hasMoreTokens()) {
                String fileName = st.nextToken();

                // check possible parameters at the beginning of the condition
                if (theMasks.isEmpty() && fileName.equalsIgnoreCase(UNZIP_REQUEST_PARAMETER)) {
                    unzipIsRequested = true;
                    LOGGER.info("zip file analysis requested");
                    continue;
                }
                if (theMasks.isEmpty() && fileName.equalsIgnoreCase(DEBUG_REQUEST_PARAMETER)) {
                    isDebug = true;
                    LOGGER.info("debug requested");
                    continue;
                }
                Mask mask = new Mask();
                if (fileName.startsWith("*")) {
                    mask.suffixMatch = true;
                    mask.matchString = fileName.substring(1);
                } else {
                    mask.suffixMatch = false;
                    mask.matchString = fileName;
                }
                mask.matchString = cleanString(mask.matchString);
                theMasks.add(mask);
            }
            return new Configuration(theMasks.toArray(Mask[]::new), isDebug, unzipIsRequested);
        }
    }

    public static String cleanString(String fileName) {
        return DecoderUtil.decodeEncodedWords(fileName.toLowerCase(Locale.US).trim(), DecodeMonitor.SILENT);
    }

    private final Configuration configuration;
    private final PartMatch partMatch;

    public MimeWalk(Configuration configuration, PartMatch partMatch) {
        this.configuration = configuration;
        this.partMatch = partMatch;
    }

    public Collection<MailAddress> matchMail(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();

            if (matchFound(message)) {
                return mail.getRecipients();
            } else {
                return null;
            }

        } catch (Exception e) {
            if (configuration.isDebug()) {
                LOGGER.debug("Malformed message", e);
            }
            throw new MessagingException("Malformed message", e);
        }
    }

    /**
     * Checks if <I>part</I> matches with at least one of the <CODE>masks</CODE>.
     *
     * @param part
     */
    protected boolean matchFound(Part part) throws Exception {

        /*
         * if there is an attachment and no inline text,
         * the content type can be anything
         */

        if (part.getContentType() == null ||
            part.getContentType().startsWith("multipart/alternative")) {
            return false;
        }

        Object content;

        try {
            content = part.getContent();
        } catch (UnsupportedEncodingException uee) {
            // in this case it is not an attachment, so ignore it
            return false;
        }

        Exception anException = null;

        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                try {
                    Part bodyPart = multipart.getBodyPart(i);
                    if (matchFound(bodyPart)) {
                        return true; // matching file found
                    }
                } catch (MessagingException e) {
                    anException = e;
                } // remember any messaging exception and process next bodypart
            }
        } else {
            if (partMatch.partMatch(part)) {
                return true;
            }
        }

        // if no matching attachment was found and at least one exception was catched rethrow it up
        if (anException != null) {
            throw anException;
        }

        return false;
    }
}
