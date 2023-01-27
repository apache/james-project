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
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.UidRange;
import org.apache.james.imap.message.Literal;

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
    ImapResponseComposer untaggedNoResponse(String displayMessage, String responseCode) throws IOException;

    /**
     * Compose flags to output using standard format.
     * 
     * @param flags
     *            <code>Flags</code>, not null
     */
    ImapResponseComposer flags(Flags flags) throws IOException;

    /**
     * Composes a <code>NIL</code>.
     */
    ImapResponseComposer nil() throws IOException;

    /**
     * Writes the message provided to the client, prepended with the untagged
     * marker "*".
     * 
     * @param message
     *            The message to write to the client.
     */
    ImapResponseComposer untaggedResponse(String message) throws IOException;

    /**
     * Write a '*'
     * 
     * @return composer
     */
    ImapResponseComposer untagged() throws IOException;

    /**
     * @return composer
     */
    ImapResponseComposer commandName(ImapCommand command) throws IOException;

    /**
     * Write the message of type <code>String</code>
     *
     * @return composer
     */
    ImapResponseComposer message(String message) throws IOException;

    ImapResponseComposer message(byte[] message) throws IOException;

    /**
     * Write the message of type <code>Long</code>
     * 
     * @param number
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer message(long number) throws IOException;

    /**
     * First encodes the given {@code mailboxName} using
     * {@link ModifiedUtf7#encodeModifiedUTF7(String)} and then quotes the result
     * with {@link #quote(String)}.
     * 
     * @param mailboxName
     * @return
     * @throws IOException
     */
    ImapResponseComposer mailbox(String mailboxName) throws IOException;

    /**
     * Write the given sequence-set
     */
    ImapResponseComposer sequenceSet(UidRange[] ranges) throws IOException;

    /**
     * Write the given sequence-set
     */
    ImapResponseComposer sequenceSet(IdRange[] ranges) throws IOException;
    
    /**
     * Write a CRLF and flush the composer which will write the content of it to
     * the socket
     * 
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer end() throws IOException;

    /**
     * Write a tag
     * 
     * @param tag
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer tag(Tag tag) throws IOException;

    /**
     * Write a quoted message
     * 
     * @param message
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer quote(String message) throws IOException;

    ImapResponseComposer quote(char message) throws IOException;


    /**
     * Compose a {@link Literal} and write it to the socket. Everything which
     * was buffered before will get written too
     * 
     * @param literal
     * @return self
     * @throws IOException
     */
    ImapResponseComposer literal(Literal literal) throws IOException;

    /**
     * Write a '('
     * 
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer openParen() throws IOException;

    /**
     * Write a ')'
     * 
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer closeParen() throws IOException;

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
    ImapResponseComposer quoteUpperCaseAscii(String message) throws IOException;

    /**
     * Tell the {@link ImapResponseComposer} to skip the next written space
     * 
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer skipNextSpace() throws IOException;

    /**
     * Writes a continuation response.
     * 
     * @param message
     *            message for display, not null
     */
    ImapResponseComposer continuationResponse(String message) throws IOException;

    ImapResponseComposer continuationResponse() throws IOException;

    /**
     * Write a '}'
     * 
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer closeSquareBracket() throws IOException;

    /**
     * Write a '{'
     * 
     * @return composer
     * @throws IOException
     */
    ImapResponseComposer openSquareBracket() throws IOException;

}
