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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.UUID;

import jakarta.activation.DataHandler;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.SharedInputStream;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.UnsynchronizedBufferedInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.lifecycle.api.LifecycleUtil;

import com.google.common.io.CountingInputStream;

/**
 * This object wraps a MimeMessage, only loading the underlying MimeMessage
 * object when needed. Also tracks if changes were made to reduce unnecessary
 * saves.
 *
 * This class is not thread safe.
 */
public class MimeMessageWrapper extends MimeMessage implements Disposable {

    /**
     * System property which tells JAMES if it should copy a message in memory
     * or via a temporary file. Default is the file
     */
    public static final String USE_MEMORY_COPY = "james.message.usememorycopy";
    private static final int UNKNOWN = -1;
    private static final int HEADER_BODY_SEPARATOR_SIZE = 2;

    /**
     * Can provide an input stream to the data
     */
    protected MimeMessageSource source = null;

    /**
     * This is false until we parse the message
     */
    protected boolean messageParsed = false;

    /**
     * This is false until we parse the message
     */
    protected boolean headersModified = false;

    /**
     * This is false until we parse the message
     */
    protected boolean bodyModified = false;

    /**
     * Keep a reference to the sourceIn so we can close it only when we dispose
     * the message.
     */
    private InputStream sourceIn;

    private long initialHeaderSize;

    private MimeMessageWrapper(Session session) {
        super(session);
        this.headers = null;
        this.modified = false;
        this.headersModified = false;
        this.bodyModified = false;
    }

    /**
     * A constructor that instantiates a MimeMessageWrapper based on a
     * MimeMessageSource
     * 
     * @param source
     *            the MimeMessageSource
     * @throws MessagingException
     */
    public MimeMessageWrapper(Session session, MimeMessageSource source) {
        this(session);
        this.source = source;
    }

    /**
     * A constructor that instantiates a MimeMessageWrapper based on a
     * MimeMessageSource
     * 
     * @param source
     *            the MimeMessageSource
     * @throws MessagingException
     * @throws MessagingException
     */
    public MimeMessageWrapper(MimeMessageSource source) {
        this(Session.getDefaultInstance(System.getProperties()), source);
    }

    public MimeMessageWrapper(MimeMessage original) throws MessagingException {
        this(Session.getDefaultInstance(System.getProperties()));
        flags = original.getFlags();

        if (source == null) {
            InputStream in;

            boolean useMemoryCopy = false;
            String memoryCopy = System.getProperty(USE_MEMORY_COPY);
            if (memoryCopy != null) {
                useMemoryCopy = Boolean.parseBoolean(memoryCopy);
            }
            try {

                if (useMemoryCopy) {
                    UnsynchronizedByteArrayOutputStream bos;
                    int size = original.getSize();
                    if (size > 0) {
                        bos = new UnsynchronizedByteArrayOutputStream(size);
                    } else {
                        bos = new UnsynchronizedByteArrayOutputStream();
                    }
                    original.writeTo(bos);
                    bos.close();
                    in = new SharedByteArrayInputStream(bos.toByteArray());
                    parse(in);
                    in.close();
                    saved = true;
                } else {
                    MimeMessageInputStreamSource src = MimeMessageInputStreamSource.create("MailCopy-" + UUID.randomUUID().toString());
                    OutputStream out = src.getWritableOutputStream();
                    original.writeTo(out);
                    out.close();
                    source = src;
                }

            } catch (IOException ex) {
                // should never happen, but just in case...
                throw new MessagingException("IOException while copying message", ex);
            }
        }
    }

    /**
     * Overrides default javamail behaviour by not altering the Message-ID by
     * default, see <a href="https://issues.apache.org/jira/browse/JAMES-875">JAMES-875</a> and
     * <a href="https://issues.apache.org/jira/browse/JAMES-1010">JAMES-1010</a>
     */
    @Override
    protected void updateMessageID() throws MessagingException {
        if (getMessageID() == null) {
            super.updateMessageID();
        }
    }

    /**
     * Returns the source ID of the MimeMessageSource that is supplying this
     * with data.
     * 
     * @see MimeMessageSource
     */
    public String getSourceId() {
        return source != null ? source.getSourceId() : null;
    }

    /**
     * Load the message headers from the internal source.
     * 
     * @throws MessagingException
     *             if an error is encountered while loading the headers
     */
    protected void loadHeaders() throws MessagingException {
        if (headers != null) {
            // Another thread has already loaded these headers
        } else if (source != null) {
            try (InputStream in = source.getInputStream()) {
                headers = createInternetHeaders(in);
            } catch (IOException ioe) {
                throw new MessagingException("Unable to parse headers from stream: " + ioe.getMessage(), ioe);
            }
        } else {
            throw new MessagingException("loadHeaders called for a message with no source, contentStream or stream");
        }
    }

    /**
     * Load the complete MimeMessage from the internal source.
     * 
     * @throws MessagingException
     *             if an error is encountered while loading the message
     */
    public void loadMessage() throws MessagingException {
        if (messageParsed) {
            // Another thread has already loaded this message
        } else if (source != null) {
            sourceIn = null;
            try {
                sourceIn = source.getInputStream();

                parse(sourceIn);
                // TODO is it ok?
                saved = true;

            } catch (IOException ioe) {
                try {
                    sourceIn.close();
                } catch (IOException e) {
                    //ignore exception during close
                }
                sourceIn = null;
                throw new MessagingException("Unable to parse stream: " + ioe.getMessage(), ioe);
            }
        } else {
            throw new MessagingException("loadHeaders called for an unparsed message with no source");
        }
    }

    /**
     * Get whether the message has been modified.
     * 
     * @return whether the message has been modified
     */
    public boolean isModified() {
        return headersModified || bodyModified || modified;
    }

    /**
     * Get whether the body of the message has been modified
     * 
     * @return bodyModified
     */
    public boolean isBodyModified() {
        return bodyModified;
    }

    /**
     * Get whether the header of the message has been modified
     * 
     * @return headersModified
     */
    public boolean isHeaderModified() {
        return headersModified;
    }

    /**
     * Rewritten for optimization purposes
     */
    @Override
    public void writeTo(OutputStream os) throws IOException, MessagingException {
        writeTo(os, os);

    }

    /**
     * Rewritten for optimization purposes
     */
    @Override
    public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {
        writeTo(os, os, ignoreList);
    }

    /**
     * Write
     */
    public void writeTo(OutputStream headerOs, OutputStream bodyOs) throws IOException, MessagingException {
        writeTo(headerOs, bodyOs, new String[0]);
    }

    public void writeTo(OutputStream headerOs, OutputStream bodyOs, String[] ignoreList) throws IOException, MessagingException {
        writeTo(headerOs, bodyOs, ignoreList, false);
    }

    public void writeTo(OutputStream headerOs, OutputStream bodyOs, String[] ignoreList, boolean preLoad) throws IOException, MessagingException {
        
        if (!preLoad && source != null && !isBodyModified()) {
            // We do not want to instantiate the message... just read from
            // source
            // and write to this outputstream

            // First handle the headers
            try (InputStream in = source.getInputStream()) {
                InternetHeaders myHeaders;
                MailHeaders parsedHeaders = new MailHeaders(in);

                // check if we should use the parsed headers or not
                if (!isHeaderModified()) {
                    myHeaders = parsedHeaders;
                } else {
                    // The headers was modified so we need to call saveChanges() just to be sure
                    // See JAMES-1320
                    if (!saved) {
                        saveChanges();
                    }
                    myHeaders = headers;
                }
                Enumeration<String> filteredHeaders = myHeaders.getNonMatchingHeaderLines(ignoreList);
                new InternetHeadersInputStream(filteredHeaders).transferTo(headerOs);
                in.transferTo(bodyOs);
            }
        } else {
            // save the changes as the message was modified
            // See JAMES-1320
            if (!saved) {
                saveChanges();
            }

            // MimeMessageUtil.writeToInternal(this, headerOs, bodyOs,
            // ignoreList);
            if (headers == null) {
                loadHeaders();
            }
            Enumeration<String> filteredHeaders = headers.getNonMatchingHeaderLines(ignoreList);
            new InternetHeadersInputStream(filteredHeaders).transferTo(headerOs);

            if (preLoad && !messageParsed) {
                loadMessage();
            }
            MimeMessageUtil.writeMessageBodyTo(this, bodyOs);
        }
    }

    /**
     * This is the MimeMessage implementation - this should return ONLY the
     * body, not the entire message (should not count headers). This size will
     * never change on {@link #saveChanges()}
     */
    @Override
    public int getSize() throws MessagingException {
        if (source != null && !bodyModified) {
            try {
                long fullSize = source.getMessageSize();
                if (headers == null) {
                    loadHeaders();
                }
                // 2 == CRLF
                return Math.max(0, (int) (fullSize - initialHeaderSize - HEADER_BODY_SEPARATOR_SIZE));

            } catch (IOException e) {
                throw new MessagingException("Unable to calculate message size");
            }
        } else {
            return UNKNOWN;
        }
    }

    /**
     * Corrects JavaMail 1.1 version which always returns -1. Only corrected for
     * content less than 5000 bytes, to avoid memory hogging.
     */
    @Override
    public int getLineCount() throws MessagingException {
        InputStream in;
        try {
            in = getContentStream();
        } catch (Exception e) {
            return UNKNOWN;
        }
        if (in == null) {
            return UNKNOWN;
        }
        // Wrap input stream in LineNumberReader
        // Not sure what encoding to use really...
        try (InputStream input = in;
            InputStreamReader isr = builderReader(input)) {
            // Read through all the data
            char[] block = new char[4096];
            try (LineNumberReader counter = new LineNumberReader(isr)) {
                while (counter.read(block) > UNKNOWN) {
                    // Just keep reading
                }
                return counter.getLineNumber();
            }
        } catch (IOException ioe) {
            return UNKNOWN;
        }
    }

    private InputStreamReader builderReader(InputStream in) throws MessagingException, UnsupportedEncodingException {
        if (getEncoding() != null) {
            return new InputStreamReader(in, getEncoding());
        }
        return new InputStreamReader(in);
    }

    /**
     * Returns size of message, ie headers and content
     */
    public long getMessageSize() throws MessagingException {
        if (source != null && !isModified()) {
            try {
                return source.getMessageSize();
            } catch (IOException ioe) {
                throw new MessagingException("Error retrieving message size", ioe);
            }
        } else if (source != null && !bodyModified) {
            try (InputStream in = source.getInputStream()) {
                CountingInputStream countingInputStream = new CountingInputStream(in);
                new MailHeaders(countingInputStream);
                long previousHeaderLength = countingInputStream.getCount();
                return source.getMessageSize() - previousHeaderLength + IOUtils.consume(new InternetHeadersInputStream(getAllHeaderLines()));
            } catch (IOException e) {
                throw new MessagingException("Error retrieving message size", e);
            }
        } else {
            return MimeMessageUtil.calculateMessageSize(this);
        }
    }

    /**
     * We override all the "headers" access methods to be sure that we loaded
     * the headers
     */

    @Override
    public String[] getHeader(String name) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getHeader(name);
    }

    @Override
    public String getHeader(String name, String delimiter) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getHeader(name, delimiter);
    }

    @Override
    public Enumeration<Header> getAllHeaders() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getAllHeaders();
    }

    @Override
    public Enumeration<Header> getMatchingHeaders(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getMatchingHeaders(names);
    }

    @Override
    public Enumeration<Header> getNonMatchingHeaders(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getNonMatchingHeaders(names);
    }

    @Override
    public Enumeration<String> getAllHeaderLines() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getAllHeaderLines();
    }

    @Override
    public Enumeration<String> getMatchingHeaderLines(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getMatchingHeaderLines(names);
    }

    @Override
    public Enumeration<String> getNonMatchingHeaderLines(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getNonMatchingHeaderLines(names);
    }

    private void checkModifyHeaders() throws MessagingException {
        // Disable only-header loading optimizations for JAMES-559
        /*
         * if (!messageParsed) { loadMessage(); }
         */

        // End JAMES-559
        if (headers == null) {
            loadHeaders();
        }
        modified = true;
        saved = false;
        headersModified = true;
    }

    @Override
    public void setHeader(String name, String value) throws MessagingException {
        checkModifyHeaders();
        super.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) throws MessagingException {
        checkModifyHeaders();
        super.addHeader(name, value);
    }

    @Override
    public void removeHeader(String name) throws MessagingException {
        checkModifyHeaders();
        super.removeHeader(name);
    }

    @Override
    public void addHeaderLine(String line) throws MessagingException {
        checkModifyHeaders();
        super.addHeaderLine(line);
    }

    /**
     * The message is changed when working with headers and when altering the
     * content. Every method that alter the content will fallback to this one.
     */
    @Override
    public void setDataHandler(DataHandler arg0) throws MessagingException {
        modified = true;
        saved = false;
        bodyModified = true;
        super.setDataHandler(arg0);
    }

    @Override
    public void dispose() {
        if (sourceIn != null) {
            try {
                sourceIn.close();
            } catch (IOException e) {
                //ignore exception during close
            }
        }
        if (source != null) {
            LifecycleUtil.dispose(source);
        }
    }

    @Override
    protected void parse(InputStream is) throws MessagingException {
        // the super implementation calls
        // headers = createInternetHeaders(is);
        parseUnsynchronized(is);
        messageParsed = true;
    }

    protected void parseUnsynchronized(InputStream is) throws MessagingException {
        if (!(is instanceof ByteArrayInputStream) && !(is instanceof BufferedInputStream) && !(is instanceof SharedInputStream)) {
            try {
                is = UnsynchronizedBufferedInputStream.builder()
                    .setBufferSize(8192)
                    .setInputStream(is)
                    .get();
            } catch (IOException e) {
                throw new MessagingException("Failure buffering stream", e);
            }
        }

        this.headers = this.createInternetHeaders(is);
        if (is instanceof SharedInputStream) {
            SharedInputStream sharedInputStream = (SharedInputStream)is;
            this.contentStream = sharedInputStream.newStream(sharedInputStream.getPosition(), -1L);
        } else {
            try {
                this.content = MimeUtility.getBytes(is);
            } catch (IOException var3) {
                throw new MessagingException("IOException", var3);
            }
        }
        this.modified = false;
    }

    /**
     * If we already parsed the headers then we simply return the updated ones.
     * Otherwise we parse
     */
    @Override
    protected InternetHeaders createInternetHeaders(InputStream is) throws MessagingException {
        /*
         * This code is no more needed: see JAMES-570 and new tests
         * 
         * InternetHeaders can be a bit awkward to work with due to its own
         * internal handling of header order. This hack may not always be
         * necessary, but for now we are trying to ensure that there is a
         * Return-Path header, even if just a placeholder, so that later, e.g.,
         * in LocalDelivery, when we call setHeader, it will remove any other
         * Return-Path headers, and ensure that ours is on the top. addHeader
         * handles header order, but not setHeader. This may change in future
         * JavaMail. But if there are other Return-Path header values, let's
         * drop our placeholder.
         * 
         * MailHeaders newHeaders = new MailHeaders(new
         * ByteArrayInputStream((f.RETURN_PATH + ": placeholder").getBytes()));
         * newHeaders.setHeader(RFC2822Headers.RETURN_PATH, null);
         * newHeaders.load(is); String[] returnPathHeaders =
         * newHeaders.getHeader(RFC2822Headers.RETURN_PATH); if
         * (returnPathHeaders.length > 1)
         * newHeaders.setHeader(RFC2822Headers.RETURN_PATH,
         * returnPathHeaders[1]);
         */

        // Keep this: skip the headers from the stream
        // we could put that code in the else and simple write an "header"
        // skipping
        // reader for the others.
        MailHeaders newHeaders = new MailHeaders(is);

        if (headers != null) {
            return headers;
        } else {
            initialHeaderSize = newHeaders.getSize();

            return newHeaders;
        }
    }

    @Override
    protected InputStream getContentStream() throws MessagingException {
        if (!messageParsed) {
            loadMessage();
        }
        return super.getContentStream();
    }

    @Override
    public InputStream getRawInputStream() throws MessagingException {
        if (!messageParsed && !isModified() && source != null) {
            InputStream is;
            try {
                is = source.getInputStream();
                // skip the headers.
                new MailHeaders(is);
                return is;
            } catch (IOException e) {
                throw new MessagingException("Unable to read the stream", e);
            }
        } else {
            return super.getRawInputStream();
        }
    }

    /**
     * Return an {@link InputStream} which holds the full content of the
     * message. This method tries to optimize this call as far as possible. This
     * stream contains the updated {@link MimeMessage} content if something was
     * changed
     * 
     * @return messageInputStream
     * @throws MessagingException
     */

    public InputStream getMessageInputStream() throws MessagingException {
        if (!messageParsed && !isModified() && source != null) {
            try {
                return source.getInputStream();
            } catch (IOException e) {
                throw new MessagingException("Unable to get inputstream", e);
            }
        } else {
            try {

                // Try to optimize if possible to prevent OOM on big mails.
                // See JAMES-1252 for an example
                if (!bodyModified && source != null) {
                    // ok only the headers were modified so we don't need to
                    // copy the whole message content into memory
                    InputStream in = source.getInputStream();
                    
                    // skip over headers from original stream we want to use the
                    // in memory ones
                    new MailHeaders(in);

                    // now construct the new stream using the in memory headers
                    // and the body from the original source
                    return new SequenceInputStream(new InternetHeadersInputStream(getAllHeaderLines()), in);
                } else {
                    // the body was changed so we have no other solution to copy
                    // it into memory first :(
                    UnsynchronizedByteArrayOutputStream out = new UnsynchronizedByteArrayOutputStream();
                    writeTo(out);
                    return out.toInputStream();
                }
            } catch (IOException e) {
                throw new MessagingException("Unable to get inputstream", e);
            }
        }
    }

}
