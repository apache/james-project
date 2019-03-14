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

package org.apache.james.vault;

import java.io.IOException;
import java.io.InputStream;

/**
 * This class carries a {@link org.apache.james.vault.DeletedMessage}
 * and its data inside an InputStream.
 *
 * The InputStream is created and maintained by the callers.
 */
public class DeletedMessageWithContent implements AutoCloseable {

    private final DeletedMessage deletedMessage;
    private final InputStream content;

    public DeletedMessageWithContent(DeletedMessage deletedMessage, InputStream content) {
        this.deletedMessage = deletedMessage;
        this.content = content;
    }

    public DeletedMessage getDeletedMessage() {
        return deletedMessage;
    }

    /**
     * Returns the original InputStream passed to the constructor.
     * Thus, if the InputStream is already closed by the callers, it cannot be reused
     *
     * @return content
     */
    public InputStream getContent() {
        return content;
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
