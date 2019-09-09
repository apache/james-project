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

/**
 * This defines a reusable datasource that can supply an input stream with
 * MimeMessage data. This allows a MimeMessageWrapper or other classes to grab
 * the underlying data.
 * 
 * @see MimeMessageWrapper
 */
public abstract class MimeMessageSource {

    /**
     * Returns a unique String ID that represents the location from where this
     * file is loaded. This will be used to identify where the data is,
     * primarily to avoid situations where this data would get overwritten.
     * 
     * @return the String ID
     */
    public abstract String getSourceId();

    /**
     * Get an input stream to retrieve the data stored in the datasource
     * 
     * @return a <code>InputStream</code> containing the data
     * 
     * @throws IOException
     *             if an error occurs while generating the InputStream
     */
    public abstract InputStream getInputStream() throws IOException;

    /**
     * Return the size of all the data. Default implementation... others can
     * override to do this much faster
     * 
     * @return the size of the data represented by this source
     * @throws IOException
     *             if an error is encountered while computing the message size
     */
    public long getMessageSize() throws IOException {
        int size = 0;
        try (InputStream in = getInputStream()) {
            int read;
            byte[] data = new byte[1024];
            while ((read = in.read(data)) > 0) {
                size += read;
            }
        }
        // Exception ignored because logging is
        // unavailable
        return size;
    }

}
