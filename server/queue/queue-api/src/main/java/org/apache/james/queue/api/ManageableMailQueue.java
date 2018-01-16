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
package org.apache.james.queue.api;

import java.util.Iterator;

import org.apache.mailet.Mail;

/**
 * {@link MailQueue} which is manageable
 */
public interface ManageableMailQueue extends MailQueue {

    enum Type {
        Sender, Recipient, Name
    }

    /**
     * Return the size of the queue
     * 
     * @return size
     * @throws MailQueueException
     */
    long getSize() throws MailQueueException;

    /**
     * Flush the queue, which means it will make all message ready for dequeue
     * 
     * @return count the count of all flushed mails
     * @throws MailQueueException
     */
    long flush() throws MailQueueException;

    /**
     * Remove all mails from the queue
     * 
     * @return count the count of all removed mails
     * @throws MailQueueException
     */
    long clear() throws MailQueueException;

    /**
     * Remove all mails from the queue that match
     * 
     * @param type
     * @param value
     * @return count the count of all removed mails
     * @throws MailQueueException
     */
    long remove(Type type, String value) throws MailQueueException;

    /**
     * Allow to browse the queues content. The returned content may get modified
     * while browsing it during other threads.
     * 
     * @return content
     */
    MailQueueIterator browse() throws MailQueueException;

    /**
     * {@link Iterator} subclass which allows to browse the content of a queue.
     * The content is not meant to be modifiable, everything is just READ-ONLY!
     */
    interface MailQueueIterator extends Iterator<MailQueueItemView> {

        /**
         * Close the iterator. After this was called the iterator MUST NOT be
         * used again
         */
        void close();
    }

    /**
     * Represent a View over a queue {@link MailQueue.MailQueueItem}
     */
    class MailQueueItemView {

        private final Mail mail;
        private final long nextDelivery;

        public MailQueueItemView(Mail mail, long nextDelivery) {
            this.mail = mail;
            this.nextDelivery = nextDelivery;
        }

        /**
         * Return the Mail
         * 
         * @return mail
         */
        public Mail getMail() {
            return mail;
        }

        /**
         * Return the timestamp when the mail will be ready for dequeuing or -1
         * if there is no restriction set..
         * 
         * @return nextDelivery
         */
        public long getNextDelivery() {
            return nextDelivery;
        }
    }

}
