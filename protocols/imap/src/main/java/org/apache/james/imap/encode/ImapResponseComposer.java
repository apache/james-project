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

package org.apache.james.imap.encode;

import java.io.IOException;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.display.CharsetUtil;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.message.response.Literal;

public interface ImapResponseComposer {

    /**
     * Writes an untagged NO response. Indicates that a warning. The command may
     * still complete sucessfully.
     * 
     * @param displayMessage
     *            message for display, not null
     * @param responseCode
     *            response code or null when there is no response code
     */
    public ImapResponseComposer untaggedNoResponse(String displayMessage, String responseCode) throws IOException;

    /**
     * Compose flags to output using standard format.
     * 
     * @param flags
     *            <code>Flags</code>, not null
     */
    public ImapResponseComposer flags(Flags flags) throws IOException;

    /**
     * Composes a <code>NIL</code>.
     * 
     * @throws IOException
     */
    public ImapResponseComposer nil() throws IOException;

    /**
     * Compose a response which contains the {@link ImapCommand} to which the
     * response belongs
     * 
     * @param command
     * @param message
     * @return self
     * @throws IOException
     */
    public ImapResponseComposer commandResponse(ImapCommand command, String message) throws IOException;

    /**
     * Writes the message provided to the client, prepended with the request
     * tag.
     * 
     * @param message
     *            The message to write to the client.
     */
    public ImapResponseComposer taggedResponse(String message, String tag) throws IOException;

    /**
     * Writes the message provided to the client, prepended with the untagged
     * marker "*".
     * 
     * @param message
     *            The message to write to the client.
     */
    public ImapResponseComposer untaggedResponse(String message) throws IOException;

    /**
     * Write a '*'
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer untagged() throws IOException;

    /**
     * 
     * @param name
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer commandName(final String name) throws IOException;

    /**
     * Write the message of type <code>String</code>
     * 
     * @param message
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer message(final String message) throws IOException;

    /**
     * Write the message of type <code>Long</code>
     * 
     * @param number
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer message(final long number) throws IOException;

    /**
     * First encodes the given {@code mailboxName} using
     * {@link CharsetUtil#encodeModifiedUTF7(String)} and then quotes the result
     * with {@link #quote(String)}.
     * 
     * @param mailboxName
     * @return
     * @throws IOException
     */
    public ImapResponseComposer mailbox(final String mailboxName) throws IOException;

    /**
     * Write the given sequence-set
     * 
     * @param ranges
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer sequenceSet(final IdRange[] ranges) throws IOException;

    /**
     * Write a CRLF and flush the composer which will write the content of it to
     * the socket
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer end() throws IOException;

    /**
     * Write a tag
     * 
     * @param tag
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer tag(String tag) throws IOException;

    /**
     * Write a quoted message
     * 
     * @param message
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer quote(String message) throws IOException;

    /**
     * Compose a {@link Literal} and write it to the socket. Everything which
     * was buffered before will get written too
     * 
     * @param literal
     * @return self
     * @throws IOException
     */
    public ImapResponseComposer literal(Literal literal) throws IOException;

    /**
     * Write a '('
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer openParen() throws IOException;

    /**
     * Write a ')'
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer closeParen() throws IOException;

    /**
     * Appends the given message after conversion to upper case. The message may
     * be assumed to be ASCII encoded. Conversion of characters MUST NOT be
     * performed according to the current locale but as per ASCII.
     * 
     * @param message
     *            ASCII encoded, not null
     * @return self, not null
     * @throws IOException
     */
    public ImapResponseComposer upperCaseAscii(final String message) throws IOException;

    /**
     * Appends the given message after conversion to upper case. The message may
     * be assumed to be ASCII encoded. Conversion of characters MUST NOT be
     * performed according to the current locale but as per ASCII.
     * 
     * @param message
     *            ASCII encoded, not null
     * @return self, not null
     * @throws IOException
     */
    public ImapResponseComposer quoteUpperCaseAscii(final String message) throws IOException;

    /**
     * Tell the {@link ImapResponseComposer} to skip the next written space
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer skipNextSpace() throws IOException;

    /**
     * Writes a continuation response.
     * 
     * @param message
     *            message for display, not null
     */
    public ImapResponseComposer continuationResponse(String message) throws IOException;

    /**
     * Write a '}'
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer closeSquareBracket() throws IOException;

    /**
     * Write a '{'
     * 
     * @return composer
     * @throws IOException
     */
    public ImapResponseComposer openSquareBracket() throws IOException;

}
