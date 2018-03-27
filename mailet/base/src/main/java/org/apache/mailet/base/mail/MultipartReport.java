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

import java.io.IOException;
import java.io.OutputStream;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataSource;
import javax.mail.MessagingException;

import org.apache.james.javax.MimeMultipartReport;


/**
 * <p>Data Content Handler for...</p>
 * <dl>
 * <dt>MIME type name</dt><dd>multipart</dd>
 * <dt>MIME subtype name</dt><dd>report</dd>
 * </dl>
 */
public class MultipartReport extends AbstractDataContentHandler {
    /**
     * Default constructor.
     */
    public MultipartReport() {
        super();
    }

    @Override
    protected ActivationDataFlavor computeDataFlavor() {
        return new ActivationDataFlavor(MimeMultipartReport.class,
                "multipart/report", "Multipart Report");
    }

    @Override
    public void writeTo(Object aPart, String aMimeType, OutputStream aStream)
            throws IOException {
        if (!(aPart instanceof MimeMultipartReport)) {
            throw new IOException("Type \"" + aPart.getClass().getName()
                + "\" is not supported.");
        }
        try {
            ((MimeMultipartReport) aPart).writeTo(aStream);
        } catch (MessagingException e) {
            throw new IOException(e.getMessage());
        }
    }

    @Override
    protected Object computeContent(DataSource aDataSource)
            throws MessagingException {
        return new MimeMultipartReport(aDataSource);
    }
}
