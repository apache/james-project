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


package org.apache.mailet;

import java.util.Collection;

import jakarta.mail.MessagingException;

import com.google.common.collect.ImmutableList;

/**
 * A Mailet processes mail messages.
 * <p>
 * The Mailet life cycle is controlled by the mailet container,
 * which invokes the Mailet methods in the following order:
 * <ol>
 * <li>The mailet is constructed.
 * <li>The {@link #init} method is invoked once to initialize the mailet.
 * <li>The {@link #service} method is invoked to process mail messages.
 *     This can occur an unlimited number of times, even concurrently.
 * <li>At some point, such as when the mailet container is shut down,
 *     the mailet is taken out of service and then destroyed by invoking
 *     the {@link #destroy} method once.
 * </ol>
 * <p>
 * In addition to the life-cycle methods, this interface provides the
 * {@link #getMailetConfig} method, which provides the Mailet with
 * its initialization parameters and a {@link MailetContext} through which
 * it can interact with the mailet container, and the {@link #getMailetInfo}
 * method, which provides basic information about the Mailet.
 * <p>
 * Mailets are grouped by the mailet container's configuration into processors.
 * Each processor is comprised of an ordered sequence of Mailets, each with a
 * corresponding {@link Matcher}. A Mail message is processed by each
 * Matcher-Mailet pair in order: If the mail is matched by the Matcher, it is
 * passed to the Mailet's {@code service} method for processing, and if it is
 * not matched, the Mailet is skipped and the mail moves on to the next
 * Matcher-Mailet pair.
 * <p>
 * The {@code service} method performs all needed processing on the Mail,
 * and may modify the message content, recipients, attributes, state, etc.
 * When the method returns, the mail is passed on to the next Matcher-Mailer
 * pair in the processor. If there are no subsequent mailets in the processor,
 * it is moved to the error processor.
 * Setting the Mail {@link Mail#setState state} to {@link Mail#GHOST}, or clearing its
 * recipient list, both mean that no further processing is needed, which will
 * cause the Mail to be dropped without ever reaching subsequent Mailets.
 * <p>
 * Instead of creating new messages, the mailet can put a message with new recipients
 * at the top of the mail queue, or insert them immediately after it's execution
 * through the API are provided by the MailetContext interface.
 */
public interface Mailet {
    
    /**
     * Initializes this Mailet.
     * <p>
     * This method is called only once, and must complete successfully
     * before the {@link #service} method can be invoked.
     *
     * @param config a MailetConfig containing the mailet's configuration
     *          and initialization parameters
     * @throws MessagingException if an error occurs
     */
    void init(MailetConfig config) throws MessagingException;

    /**
     * Services a mail message.
     * <p>
     * Mailets typically run inside multithreaded mailet containers that can handle
     * multiple requests concurrently. Developers must be aware to synchronize access
     * to any shared resources such as files and network connections, as well as the
     * mailet's fields. More information on multithreaded programming in Java is
     * available at <a href="http://java.sun.com/Series/Tutorial/java/threads/multithreaded.html">the
     * Java tutorial on multi-threaded programming</a>.
     *
     * @param mail the Mail to process
     * @throws MessagingException if any error occurs which prevents the Mail
     *         processing from completing successfully
     */
    void service(Mail mail) throws MessagingException;

    /**
     * Destroys this Mailet.
     * <p>
     * This method is called only once, after all {@link #service} invocations
     * have completed (or a timeout period has elapsed). After this method
     * returns, this Mailet will no longer be used.
     * <p>
     * Implementations should use this method to release any resources that
     * are being held (such as memory, file handles or threads) and make sure
     * that any persistent information is properly stored.
     */
    void destroy();

    /**
     * Returns a MailetConfig object, which provides initialization parameters
     * and a {@link MailetContext} through which it can interact with the
     * mailet container.
     * <p>
     * Implementations of this interface are responsible for storing the
     * MailetConfig which they receive in the {@link #init} method so
     * that this method can return it.
     *
     * @return the MailetConfig that this mailet was initialized with
     */
    MailetConfig getMailetConfig();
    
    /**
     * Returns information about the mailet, such as author, version and
     * copyright.
     *
     * @return the Mailet information (as a plain text string)
     */
    String getMailetInfo();

    /**
     * @return the list of processors that needs to be present according to this mailet configuration.
     *
     * Needs to be called after {@link Mailet::init()}
     */
    default Collection<ProcessingState> requiredProcessingState() {
        return ImmutableList.of();
}

}
