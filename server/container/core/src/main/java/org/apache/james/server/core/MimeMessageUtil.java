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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;

import jakarta.activation.UnsupportedDataTypeException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

/**
 * Utility class to provide optimized write methods for the various MimeMessage
 * implementations.
 */
public class MimeMessageUtil {

    /**
     * Convenience method to take any MimeMessage and write the headers and body
     * to two different output streams
     * 
     * @param message
     *            the MimeMessage reading from
     * @param headerOs
     *            the OutputStream writting the headers to
     * @param bodyOs
     *            the OutputStream writting the body to
     * @throws IOException
     *             get thrown if an IO Error detected while writing to the
     *             streams
     * @throws MessagingException
     *             get thrown if an error detected while reading the message
     */
    public static void writeTo(MimeMessage message, OutputStream headerOs, OutputStream bodyOs) throws IOException, MessagingException {
        writeTo(message, headerOs, bodyOs, null);
    }

    /**
     * Convenience method to take any MimeMessage and write the headers and body
     * to two different output streams, with an ignore list
     * 
     * @param message
     *            the MimeMessage reading from
     * @param headerOs
     *            the OutputStream writting the headers to
     * @param bodyOs
     *            the OutputStream writting the body to
     * @param ignoreList
     *            the String[] which contains headers which should be ignored
     * @throws IOException
     *             get thrown if an IO Error detected while writing to the
     *             streams
     * @throws MessagingException
     *             get thrown if an error detected while reading the message
     */
    public static void writeTo(MimeMessage message, OutputStream headerOs, OutputStream bodyOs, String[] ignoreList) throws IOException, MessagingException {
        if (message instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper) message;
            if (!wrapper.isModified()) {
                wrapper.writeTo(headerOs, bodyOs, ignoreList);
                return;
            }
        }
        writeToInternal(message, headerOs, bodyOs, ignoreList);
    }

    /**
     * 
     * @param message
     * @param headerOs
     * @param bodyOs
     * @param ignoreList
     * @throws MessagingException
     * @throws IOException
     * @throws UnsupportedDataTypeException
     */
    public static void writeToInternal(MimeMessage message, OutputStream headerOs, OutputStream bodyOs, String[] ignoreList) throws MessagingException, IOException {
        if (message.getMessageID() == null) {
            message.saveChanges();
        }

        writeHeadersTo(message, headerOs, ignoreList);

        // Write the body to the output stream
        writeMessageBodyTo(message, bodyOs);
    }

    /**
     * Write message body of given mimeessage to the given outputStream
     * 
     * @param message
     *            the MimeMessage used as input
     * @param bodyOs
     *            the OutputStream to write the message body to
     * @throws IOException
     * @throws UnsupportedDataTypeException
     * @throws MessagingException
     */
    public static void writeMessageBodyTo(MimeMessage message, OutputStream bodyOs) throws IOException, MessagingException {
        OutputStream bos;
        InputStream bis;

        try {
            // Get the message as a stream. This will encode
            // objects as necessary, and we have some input from
            // decoding an re-encoding the stream. I'd prefer the
            // raw stream, but see
            bos = MimeUtility.encode(bodyOs, message.getEncoding());
            bis = message.getInputStream();
        } catch (UnsupportedDataTypeException | MessagingException udte) {
            /*
             * If we get an UnsupportedDataTypeException try using the raw input
             * stream as a "best attempt" at rendering a message.
             * 
             * WARNING: JavaMail v1.3 getRawInputStream() returns INVALID
             * (unchanged) content for a changed message. getInputStream() works
             * properly, but in this case has failed due to a missing
             * DataHandler.
             * 
             * MimeMessage.getRawInputStream() may throw a "no content"
             * MessagingException. In JavaMail v1.3, when you initially create a
             * message using MimeMessage APIs, there is no raw content
             * available. getInputStream() works, but getRawInputStream() throws
             * an exception. If we catch that exception, throw the UDTE. It
             * should mean that someone has locally constructed a message part
             * for which JavaMail doesn't have a DataHandler.
             */

            try {
                bis = message.getRawInputStream();
                bos = bodyOs;
            } catch (MessagingException ignored) {
                throw udte;
            }
        }

        try (InputStream input = bis) {
            input.transferTo(bos);
        }
    }

    /**
     * Write the message headers to the given outputstream
     * 
     * @param message
     *            the MimeMessage to read from
     * @param headerOs
     *            the OutputStream to which the headers get written
     * @param ignoreList
     *            the String[] which holds headers which should be ignored
     * @throws MessagingException
     */
    private static void writeHeadersTo(MimeMessage message, OutputStream headerOs, String[] ignoreList) throws MessagingException {
        // Write the headers (minus ignored ones)
        Enumeration<String> headers = message.getNonMatchingHeaderLines(ignoreList);
        writeHeadersTo(headers, headerOs);
    }

    /**
     * Write the message headers to the given outputstream
     * 
     * @param headers
     *            the Enumeration which holds the headers
     * @param headerOs
     *            the OutputStream to which the headers get written
     * @throws MessagingException
     */
    public static void writeHeadersTo(Enumeration<String> headers, OutputStream headerOs) throws MessagingException {
        try {
            new InternetHeadersInputStream(headers).transferTo(headerOs);
        } catch (IOException e) {
            throw new MessagingException("Unable to write headers to stream", e);
        }
    }

    /**
     * Get an InputStream which holds all headers of the given MimeMessage
     * 
     * @param message
     *            the MimeMessage used as source
     * @param ignoreList
     *            the String[] which holds headers which should be ignored
     * @return stream the InputStream which holds the headers
     * @throws MessagingException
     */
    public static InputStream getHeadersInputStream(MimeMessage message, String[] ignoreList) throws MessagingException {
        return new InternetHeadersInputStream(message.getNonMatchingHeaderLines(ignoreList));
    }

    /**
     * Slow method to calculate the exact size of a message!
     */
    private static final class SizeCalculatorOutputStream extends OutputStream {
        long size = 0;

        @Override
        public void write(int arg0) throws IOException {
            size++;
        }

        public long getSize() {
            return size;
        }

        @Override
        public void write(byte[] arg0, int arg1, int arg2) throws IOException {
            size += arg2;
        }

        @Override
        public void write(byte[] arg0) throws IOException {
            size += arg0.length;
        }
    }

    /**
     * Return the full site of an mimeMessage
     * 
     * @return size of full message including headers
     * @throws MessagingException
     *             if a problem occours while computing the message size
     */
    public static long getMessageSize(MimeMessage message) throws MessagingException {
        // If we have a MimeMessageWrapper, then we can ask it for just the
        // message size and skip calculating it
        long size = -1;

        if (message instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper) message;
            size = wrapper.getMessageSize();
        }

        if (size == -1) {
            size = calculateMessageSize(message);
        }

        return size;
    }

    /**
     * Calculate the size of the give mimeMessage
     * 
     * @param message
     *            the MimeMessage
     * @return size the calculated size
     * @throws MessagingException
     *             if a problem occours while calculate the message size
     */
    public static long calculateMessageSize(MimeMessage message) throws MessagingException {
        long size;
        // SK: Should probably eventually store this as a locally
        // maintained value (so we don't have to load and reparse
        // messages each time).
        size = message.getSize();
        if (size != -1) {
            Enumeration<String> e = message.getAllHeaderLines();
            if (e.hasMoreElements()) {
                size += 2;
            }
            while (e.hasMoreElements()) {
                // add 2 bytes for the CRLF
                size += e.nextElement().length() + 2;
            }
        }

        if (size == -1) {
            SizeCalculatorOutputStream out = new SizeCalculatorOutputStream();
            try {
                message.writeTo(out);
            } catch (IOException e) {
                // should never happen as SizeCalculator does not actually throw
                // IOExceptions.
                throw new MessagingException("IOException wrapped by getMessageSize", e);
            }
            size = out.getSize();
        }
        return size;
    }

}
