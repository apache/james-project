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

package org.apache.james.protocols.api;

import java.io.InputStream;

/**
 * Special {@link Response} sub-type which allows to write an {@link InputStream} to the remote peer
 * 
 *
 */
public interface StreamResponse extends Response{

    /**
     * Return the stream which needs to get written to the remote peer. This method should only be called one time (when the data is written to the client) as it returns
     * the same {@link InputStream} on every call. So once it is consumed there is no way to re-process it.
     * 
     * @return stream
     */
    public InputStream getStream();

}
