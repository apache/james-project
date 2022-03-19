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

import org.apache.james.core.MailAddress;

/**
 * This interface defines the behaviour of the message "routing" inside
 * the mailet container. At its heart is the {@link #match(Mail)} method,
 * which inspects a Mail and returns a subset of its recipients which meet
 * this Matcher's criteria.
 * <p>
 * An important feature of the mailet container is the ability to fork
 * processing of messages. When a message first arrives at the server,
 * it might have multiple recipients specified. When a message is passed
 * to a matcher, the matcher might only match some of the listed recipients.
 * The mailet container should then duplicate the message, splitting the
 * recipient list across the two messages as per the match result, and
 * proceed to process them separately.
 * <p>
 * The Matcher life cycle is controlled by the mailet container,
 * which invokes the Matcher methods in the following order:
 * <ol>
 * <li>The matcher is constructed.
 * <li>The {@link #init} method is invoked once to initialize the matcher.
 * <li>The {@link #match} method is invoked to match mail messages.
 *     This can occur an unlimited number of times, even concurrently.
 * <li>At some point, such as when the mailet container is shut down,
 *     the matcher is taken out of service and then destroyed by invoking
 *     the {@link #destroy} method once.
 * </ol>
 * <p>
 * In addition to the life-cycle methods, this interface provides the
 * {@link #getMatcherConfig} method, which provides the Matcher with
 * its configuration information and a {@link MailetContext} through which
 * it can interact with the mailet container, and the {@link #getMatcherInfo}
 * method, which provides basic information about the Matcher.
 */
public interface Matcher {

    /**
     * Initializes this Matcher.
     * <p>
     * This method is called only once, and must complete successfully
     * before the {@link #match} method can be invoked.
     *
     * @param config a MatcherConfig containing the matcher's configuration
     *          and initialization parameters
     * @throws MessagingException if an error occurs
     */
    void init(MatcherConfig config) throws MessagingException;

    /**
     * Takes a Mail message, looks at any pertinent information, and returns
     * a subset of recipients that meet the match conditions.
     * <p>
     * Matchers typically run inside multithreaded mailet containers that can handle
     * multiple requests concurrently. Developers must be aware to synchronize access
     * to any shared resources such as files, network connections, and as well as the
     * matcher's fields. More information on multithreaded programming in Java is
     * available at <a href="http://java.sun.com/Series/Tutorial/java/threads/multithreaded.html">the
     * Java tutorial on multi-threaded programming</a>.
     *
     * @param mail the Mail to match
     * @return the recipients that meet the match criteria as a Collection of String objects
     *         (may be null if there are no matched recipients)
     * @throws MessagingException if any error occurs which prevents the Mail
     *         matching from completing successfully
     */
    Collection<MailAddress> match(Mail mail) throws MessagingException;
    
    /**
     * Destroys this Matcher.
     * <p>
     * This method is called only once, after all {@link #match} invocations
     * have completed (or a timeout period has elapsed). After this method
     * returns, this Matcher will no longer be used.
     * <p>
     * Implementations should use this method to release any resources that
     * are being held (such as memory, file handles or threads) and make sure
     * that any persistent information is properly stored.
     * <p>
     * Note that containers <code>SHOULD NOT</code> invoke this method before 
     * {@link #init(MatcherConfig)} has been successfully completed. 
     */
    void destroy();
    
    /**
     * Returns a MatcherConfig object, which provides initialization parameters
     * and a {@link MailetContext} through which it can interact with the
     * mailet container.
     * <p>
     * Implementations of this interface are responsible for storing the
     * MatcherConfig which they receive in the {@link #init} method so
     * that this method can return it.
     *
     * @return the MatcherConfig that this matcher was initialized with
     */
    MatcherConfig getMatcherConfig();

    /**
     * Returns information about the matcher, such as author, version and
     * copyright.
     *
     * @return the Mailet information (as a plain text string)
     */
    String getMatcherInfo();
}
