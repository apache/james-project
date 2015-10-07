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

package org.apache.james.imap.processor.fetch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageResult;

public class MessageResultUtils {

    /**
     * Gets all header lines.
     * 
     * @param iterator
     *            {@link org.apache.james.mailbox.MessageResult.Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header<code>'s,
     * in their natural order
     * 
     * @throws MessagingException
     */
    public static List<MessageResult.Header> getAll(final Iterator<MessageResult.Header> iterator) {
        final List<MessageResult.Header> results = new ArrayList<MessageResult.Header>();
        if (iterator != null) {
            while (iterator.hasNext()) {
                results.add(iterator.next());
            }
        }
        return results;
    }

    /**
     * Gets header lines whose header names matches (ignoring case) any of those
     * given.
     * 
     * @param names
     *            header names to be matched, not null
     * @param iterator
     *            {@link org.apache.james.mailbox.MessageResult.Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<MessageResult.Header> getMatching(final String[] names, final Iterator<MessageResult.Header> iterator) throws MailboxException {
        final List<MessageResult.Header> results = new ArrayList<MessageResult.Header>(20);
        if (iterator != null) {
            while (iterator.hasNext()) {
                MessageResult.Header header = iterator.next();
                final String headerName = header.getName();
                if (headerName != null) {
                    final int length = names.length;
                    for (int i = 0; i < length; i++) {
                        final String name = names[i];
                        if (headerName.equalsIgnoreCase(name)) {
                            results.add(header);
                            break;
                        }
                    }
                }
            }
        }
        return results;
    }

    /**
     * Gets header lines whose header names matches (ignoring case) any of those
     * given.
     * 
     * @param names
     *            header names to be matched, not null
     * @param iterator
     *            {@link org.apache.james.mailbox.MessageResult.Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<MessageResult.Header> getMatching(final Collection<String> names, final Iterator<MessageResult.Header> iterator) throws MailboxException {
        final List<MessageResult.Header> result = matching(names, iterator, false);
        return result;
    }

    private static List<MessageResult.Header> matching(final Collection<String> names, final Iterator<MessageResult.Header> iterator, boolean not) throws MailboxException {
        final List<MessageResult.Header> results = new ArrayList<MessageResult.Header>(names.size());
        if (iterator != null) {
            while (iterator.hasNext()) {
                final MessageResult.Header header = iterator.next();
                final boolean match = contains(names, header);
                final boolean add = (not && !match) || (!not && match);
                if (add) {
                    results.add(header);
                }
            }
        }
        return results;
    }

    private static boolean contains(final Collection<String> names, MessageResult.Header header) throws MailboxException {
        boolean match = false;
        final String headerName = header.getName();
        if (headerName != null) {
            for (final Iterator<String> it = names.iterator(); it.hasNext();) {
                final String name = it.next();
                if (name.equalsIgnoreCase(headerName)) {
                    match = true;
                    break;
                }
            }
        }
        return match;
    }

    /**
     * Gets header lines whose header names matches (ignoring case) any of those
     * given.
     * 
     * @param names
     *            header names to be matched, not null
     * @param iterator
     *            {@link org.apache.james.mailbox.MessageResult.Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<MessageResult.Header> getNotMatching(final Collection<String> names, final Iterator<MessageResult.Header> iterator) throws MailboxException {
        final List<MessageResult.Header> result = matching(names, iterator, true);
        return result;
    }

    /**
     * Gets a header matching the given name. The matching is case-insensitive.
     * 
     * @param name
     *            name to be matched, not null
     * @param iterator
     *            <code>Iterator</code> of <code>MessageResult.Header</code>'s,
     *            not null
     * @return <code>MessageResult.Header</code>, or null if the header does not
     *         exist
     * @throws MessagingException
     */
    public static MessageResult.Header getMatching(final String name, final Iterator<MessageResult.Header> iterator) throws MailboxException {
        MessageResult.Header result = null;
        if (name != null) {
            while (iterator.hasNext()) {
                MessageResult.Header header = iterator.next();
                final String headerName = header.getName();
                if (name.equalsIgnoreCase(headerName)) {
                    result = header;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Gets header lines whose header name fails to match (ignoring case) all of
     * the given names.
     * 
     * @param names
     *            header names, not null
     * @param iterator
     *            {@link org.apache.james.mailbox.MessageResult.Header} <code>Iterator</code>
     * @return <code>List</code> of <code>@MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<MessageResult.Header> getNotMatching(final String[] names, final Iterator<MessageResult.Header> iterator) throws MailboxException {
        final List<MessageResult.Header> results = new ArrayList<MessageResult.Header>(20);
        if (iterator != null) {
            while (iterator.hasNext()) {
                MessageResult.Header header = iterator.next();
                final String headerName = header.getName();
                if (headerName != null) {
                    final int length = names.length;
                    boolean match = false;
                    for (int i = 0; i < length; i++) {
                        final String name = names[i];
                        if (headerName.equalsIgnoreCase(name)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        results.add(header);
                    }
                }
            }
        }
        return results;
    }
}
