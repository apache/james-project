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

package org.apache.james.mailbox.store;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.msgpack.core.Preconditions;

public class MessageBatcher {

    public static final int NO_BATCH_SIZE = 0;

    public interface BatchedOperation {
        List<MessageRange> execute(MessageRange messageRange) throws MailboxException;
    }

    private final int moveBatchSize;

    public MessageBatcher(int moveBatchSize) {
        Preconditions.checkArgument(moveBatchSize >= NO_BATCH_SIZE);
        this.moveBatchSize = moveBatchSize;
    }

    public List<MessageRange> batchMessages(MessageRange set, BatchedOperation batchedOperation) throws MailboxException {
        if (moveBatchSize > 0) {
            List<MessageRange> movedRanges = new ArrayList<>();
            for (MessageRange messageRange : set.split(moveBatchSize)) {
                movedRanges.addAll(batchedOperation.execute(messageRange));
            }
            return movedRanges;
        } else {
            return batchedOperation.execute(set);
        }
    }

}
