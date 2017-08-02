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
package org.apache.james.queue.file;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.slf4j.Logger;

/**
 * {@link MailQueueFactory} implementation which returns {@link FileMailQueue} instances
 */
public class FileMailQueueFactory implements MailQueueFactory, LogEnabled {

    private final Map<String, MailQueue> queues = new HashMap<>();
    private MailQueueItemDecoratorFactory mailQueueActionItemDecoratorFactory;
    private FileSystem fs;
    private Logger log;
    private boolean sync = true;

    @Inject
    public FileMailQueueFactory(FileSystem fs, MailQueueItemDecoratorFactory mailQueueItemDecoratorFactory) {
        this.fs = fs;
        this.mailQueueActionItemDecoratorFactory = mailQueueItemDecoratorFactory;
    }

    /**
     * If <code>true</code> the later created {@link FileMailQueue} will call <code>fsync</code> after each message {@link FileMailQueue#enQueue(org.apache.mailet.Mail)} call. This
     * is needed to be fully RFC conform but gives a performance penalty. If you are brave enough you man set it to <code>false</code>
     * <p/>
     * The default is <code>true</code>
     *
     * @param sync
     */
    public void setSync(boolean sync) {
        this.sync = sync;
    }

    @Override
    public MailQueue getQueue(String name) {
        MailQueue queue = queues.get(name);
        if (queue == null) {
            synchronized (queues) {
                try {
                    queue = new FileMailQueue(mailQueueActionItemDecoratorFactory, fs.getFile("file://var/store/queue"), name, sync, log);
                    queues.put(name, queue);
                } catch (IOException e) {
                    throw new RuntimeException("Unable to access queue " + name, e);
                }
            }
        }
        return queue;
    }

    @Override
    public void setLog(Logger log) {
        this.log = log;
    }

}

