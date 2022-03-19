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

package org.apache.james.server.core;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Enumeration;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.mailet.base.RFC2822Headers;

/**
 * This interface defines a container for mail headers. Each header must use
 * MIME format:
 * 
 * <pre>
 * name: value
 * </pre>
 */
public class MailHeaders extends InternetHeaders implements Serializable, Cloneable {

    private static final long serialVersionUID = 238748126601L;
    private boolean modified = false;
    private long size = -1;

    /**
     * No argument constructor
     * 
     * @throws MessagingException
     *             if the super class cannot be properly instantiated
     */
    public MailHeaders() {
        super();
    }

    /**
     * Constructor that takes an InputStream containing the contents of the set
     * of mail headers.
     * 
     * @param in
     *            the InputStream containing the header data
     * 
     * @throws MessagingException
     *             if the super class cannot be properly instantiated based on
     *             the stream
     */
    public MailHeaders(InputStream in) throws MessagingException {
        super();
        load(in);
    }

    /**
     * Write the headers to an output stream
     * 
     * @param out
     *            the OutputStream to which to write the headers
     */
    public void writeTo(OutputStream out) throws MessagingException {
        MimeMessageUtil.writeHeadersTo(getAllHeaderLines(), out);
    }

    /**
     * Generate a representation of the headers as a series of bytes.
     * 
     * @return the byte array containing the headers
     */
    public byte[] toByteArray() throws MessagingException {
        UnsynchronizedByteArrayOutputStream headersBytes = new UnsynchronizedByteArrayOutputStream();
        writeTo(headersBytes);
        return headersBytes.toByteArray();
    }

    /**
     * Check if a particular header is present.
     * 
     * @return true if the header is present, false otherwise
     */
    public boolean isSet(String name) {
        String[] value = super.getHeader(name);
        return (value != null && value.length != 0);
    }

    /**
     * If the new header is a Return-Path we get sure that we add it to the top
     * Javamail, at least until 1.4.0 does the wrong thing if it loaded a stream
     * with a return-path in the middle.
     */
    @Override
    public synchronized void addHeader(String arg0, String arg1) {
        if (RFC2822Headers.RETURN_PATH.equalsIgnoreCase(arg0)) {
            headers.add(0, new InternetHeader(arg0, arg1));
        } else {
            super.addHeader(arg0, arg1);
        }
        modified();
    }

    /**
     * If the new header is a Return-Path we get sure that we add it to the top
     * Javamail, at least until 1.4.0 does the wrong thing if it loaded a stream
     * with a return-path in the middle.
     */
    @Override
    public synchronized void setHeader(String arg0, String arg1) {
        if (RFC2822Headers.RETURN_PATH.equalsIgnoreCase(arg0)) {
            super.removeHeader(arg0);
        }
        super.setHeader(arg0, arg1);

        modified();
    }

    @Override
    public synchronized void removeHeader(String name) {
        super.removeHeader(name);
        modified();
    }

    @Override
    public synchronized void addHeaderLine(String line) {
        super.addHeaderLine(line);
        modified();
    }

    private void modified() {
        modified = true;
        size = -1;
    }

    /**
     * Check if all REQUIRED headers fields as specified in RFC 822 are present.
     * 
     * @return true if the headers are present, false otherwise
     */
    public boolean isValid() {
        return (isSet(RFC2822Headers.DATE) && isSet(RFC2822Headers.TO) && isSet(RFC2822Headers.FROM));
    }

    /**
     * Return the size of the headers
     * 
     * @return size
     */
    public synchronized long getSize() {
        if (size == -1 || modified) {
            long c = 0;
            Enumeration<String> headerLines = getAllHeaderLines();
            while (headerLines.hasMoreElements()) {
                c += headerLines.nextElement().length();
                // CRLF
                c += 2;
            }
            size = c;
            modified = false;
        }
        return size;

    }
}
