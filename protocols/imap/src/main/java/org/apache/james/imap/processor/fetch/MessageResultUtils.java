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
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;

public class MessageResultUtils {

    /**
     * Gets all header lines.
     * 
     * @param iterator
     *            {@link Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header<code>'s,
     * in their natural order
     * 
     * @throws MessagingException
     */
    public static List<Header> getAll(Iterator<Header> iterator) {
        final List<Header> results = new ArrayList<>();
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
     *            {@link Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<Header> getMatching(String[] names, Iterator<Header> iterator) throws MailboxException {
        final List<Header> results = new ArrayList<>(20);
        if (iterator != null) {
            while (iterator.hasNext()) {
                Header header = iterator.next();
                final String headerName = header.getName();
                if (headerName != null) {
                    if (Arrays.stream(names)
                        .anyMatch(headerName::equalsIgnoreCase)) {
                        results.add(header);
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
     *            {@link Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<Header> getMatching(Collection<String> names, Iterator<Header> iterator) throws MailboxException {
        return matching(names, iterator, false);
    }

    private static List<Header> matching(Collection<String> names, Iterator<Header> iterator, boolean not) throws MailboxException {
        final List<Header> results = new ArrayList<>(names.size());
        if (iterator != null) {
            while (iterator.hasNext()) {
                final Header header = iterator.next();
                final boolean match = contains(names, header);
                final boolean add = (not && !match) || (!not && match);
                if (add) {
                    results.add(header);
                }
            }
        }
        return results;
    }

    private static boolean contains(Collection<String> names, Header header) throws MailboxException {
        final String headerName = header.getName();
        if (headerName != null) {
            return names.stream().anyMatch(name -> name.equalsIgnoreCase(headerName));
        }
        return false;
    }

    /**
     * Gets header lines whose header names matches (ignoring case) any of those
     * given.
     * 
     * @param names
     *            header names to be matched, not null
     * @param iterator
     *            {@link Header} <code>Iterator</code>
     * @return <code>List</code> of <code>MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<Header> getNotMatching(Collection<String> names, Iterator<Header> iterator) throws MailboxException {
        return matching(names, iterator, true);
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
    public static Header getMatching(String name, Iterator<Header> iterator) throws MailboxException {
        Header result = null;
        if (name != null) {
            while (iterator.hasNext()) {
                Header header = iterator.next();
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
     *            {@link Header} <code>Iterator</code>
     * @return <code>List</code> of <code>@MessageResult.Header</code>'s, in
     *         their natural order
     * @throws MessagingException
     */
    public static List<Header> getNotMatching(String[] names, Iterator<Header> iterator) throws MailboxException {
        final List<Header> results = new ArrayList<>(20);
        if (iterator != null) {
            while (iterator.hasNext()) {
                Header header = iterator.next();
                final String headerName = header.getName();
                if (headerName != null) {
                    boolean match = Arrays.stream(names)
                        .anyMatch(headerName::equalsIgnoreCase);
                    if (!match) {
                        results.add(header);
                    }
                }
            }
        }
        return results;
    }
}
