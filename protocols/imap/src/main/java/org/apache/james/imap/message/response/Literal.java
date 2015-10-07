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

package org.apache.james.imap.message.response;

import java.io.IOException;
import java.io.InputStream;

public interface Literal {
    /**
     * Size of the literal content data.
     * 
     * @return number of octets which will be
     *         put onto the channel
     * @throws IOException 
     */
    public long size() throws IOException;


    /**
     * Return the Literal as {@link InputStream}
     * 
     * @return elementIn
     * @throws IOException
     */
    public InputStream getInputStream() throws IOException;
}
