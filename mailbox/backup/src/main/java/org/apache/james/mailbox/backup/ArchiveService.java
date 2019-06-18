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
package org.apache.james.mailbox.backup;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.mailbox.model.MessageResult;

public interface ArchiveService {

    /**
     * @param mailboxes   list of mailboxes and their annotations to be stored in the archive
     * @param messages    a stream of MessageResult that will be consumed
     * @param destination an OutputStream in which the zip will be written
     */
    void archive(List<MailboxWithAnnotations> mailboxes, Stream<MessageResult> messages, OutputStream destination) throws IOException;
}
