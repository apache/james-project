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

package org.apache.mailet.base.mail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeUtility;
import javax.mail.internet.ParseException;

/**
 * <p>Data Content Handler for...</p>
 * <dl>
 * <dt>MIME type name</dt><dd>message</dd>
 * <dt>MIME subtype name</dt><dd>disposition-notification</dd>
 * </dl>
 */
public class MessageDispositionNotification extends AbstractDataContentHandler {

    /**
     * Default Constructor.
     */
    public MessageDispositionNotification() {
        super();
    }

    @Override
    protected ActivationDataFlavor computeDataFlavor() {
        return new ActivationDataFlavor(String.class,
                "message/disposition-notification", "Message String");
    }

    @Override
    protected Object computeContent(DataSource aDataSource)
            throws MessagingException {
        String encoding = getCharacterSet(aDataSource.getContentType());
        Reader reader = null;
        Writer writer = new StringWriter(2048);
        String content = null;
        try {
            reader = new BufferedReader(new InputStreamReader(aDataSource
                    .getInputStream(), encoding), 2048);
            while (reader.ready()) {
                writer.write(reader.read());
            }
            writer.flush();
            content = writer.toString();
        } catch (IllegalArgumentException e) {
            throw new MessagingException("Encoding = \"" + encoding + "\"", e);
        } catch (IOException e) {
            throw new MessagingException(
                    "Exception obtaining content from DataSource", e);
        } finally {
            try {
                writer.close();
            } catch (IOException e1) {
                // No-op
            }
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e1) {
                // No-op
            }
        }
        return content;
    }

    @Override
    public void writeTo(Object aPart, String aMimeType, OutputStream aStream)
            throws IOException {
        if (!(aPart instanceof String)) {
            throw new IOException("Type \"" + aPart.getClass().getName()
                + "\" is not supported.");
        }

        String encoding = getCharacterSet(getDataFlavor().getMimeType());
        Writer writer;
        try {
            writer = new BufferedWriter(new OutputStreamWriter(aStream,
                    encoding), 2048);
        } catch (IllegalArgumentException e) {
            throw new UnsupportedEncodingException(encoding);
        }
        writer.write((String) aPart);
        writer.flush();
    }

    protected String getCharacterSet(String aType) {
        String characterSet = null;
        try {
            characterSet = new ContentType(aType).getParameter("charset");
        } catch (ParseException e) {
            // no-op
        } finally {
            if (null == characterSet) {
                characterSet = "us-ascii";
            }
        }
        return MimeUtility.javaCharset(characterSet);
    }

}
