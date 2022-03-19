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

package org.apache.james.javax;

import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeMultipart;

/**
 * Class <code>MimeMultipartReport</code> implements JavaMail support
 * for a MIME type of MimeMultipart with a subtype of report.
 */
public class MimeMultipartReport extends MimeMultipart {

    /**
     * Default constructor
     */
    public MimeMultipartReport() {
        this("report");
    }

    /**
     * Constructs a MimeMultipartReport of the given subtype.
     * @param subtype
     */
    public MimeMultipartReport(String subtype) {
        super(subtype);
    }

    /**
     * Constructs a MimeMultipartReport from the passed DataSource.
     * @param aDataSource
     * @throws MessagingException
     */
    public MimeMultipartReport(DataSource aDataSource) throws MessagingException {
        super(aDataSource);
    }
    
    /**
     * Sets the type of report.
     * @param reportType
     * @throws MessagingException
     */
    public void setReportType(String reportType) throws MessagingException {
        ContentType contentType = new ContentType(getContentType());
        contentType.setParameter("report-type", reportType);
        setContentType(contentType);
    }
    
    /**
     * Sets the content type
     * @param aContentType
     */
    protected void setContentType(ContentType aContentType) {
        contentType = aContentType.toString();
    }

}
