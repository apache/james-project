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

import static org.apache.james.imap.api.ImapConstants.SAVEDATE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FetchResponse.Structure;
import org.apache.james.mailbox.MessageSequenceNumber;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class FetchResponseEncoder implements ImapResponseEncoder<FetchResponse> {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchResponseEncoder.class);

    private static final byte[] ENVELOPE = "ENVELOPE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RFC_822_SIZE = "RFC822.SIZE".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INTERNALDATE = "INTERNALDATE".getBytes(StandardCharsets.US_ASCII);

    /** Disables all optional BODYSTRUCTURE extensions */
    private final boolean neverAddBodyStructureExtensions;

    @Inject
    public FetchResponseEncoder() {
        this(false);
    }

    /**
     * Constructs an encoder for FETCH messages.
     *
     * @param neverAddBodyStructureExtensions
     *            true to activate a workaround for broken clients who cannot
     *            parse BODYSTRUCTURE extensions, false to fully support RFC3501
     */
    public FetchResponseEncoder(boolean neverAddBodyStructureExtensions) {
        this.neverAddBodyStructureExtensions = neverAddBodyStructureExtensions;
    }

    @Override
    public Class<FetchResponse> acceptableMessages() {
        return FetchResponse.class;
    }

    @Override
    public void encode(FetchResponse fetchResponse, ImapResponseComposer composer) throws IOException {
        MessageSequenceNumber messageNumber = fetchResponse.getMessageNumber();

        composer.untagged().message(messageNumber.asInt()).message(ImapConstants.FETCH_COMMAND.getNameAsBytes()).openParen();

        encodeModSeq(composer, fetchResponse);
        encodeFlags(composer, fetchResponse);
        encodeInternalDate(composer, fetchResponse);
        encodeSize(composer, fetchResponse);
        encodeEnvelope(composer, fetchResponse);
        encodeBody(composer, fetchResponse.getBody());
        encodeBodyStructure(composer, fetchResponse.getBodyStructure());
        encodeUid(composer, fetchResponse);
        encodeBodyElements(composer, fetchResponse.getElements());
        encodeEmailId(composer, fetchResponse);
        encodeThreadId(composer, fetchResponse);
        encodeSaveDate(composer, fetchResponse);
        composer.closeParen().end();
    }

    // Handle the MODSEQ 
    private void encodeModSeq(ImapResponseComposer composer, FetchResponse response) throws IOException {
        ModSeq modSeq = response.getModSeq();
        if (modSeq != null) {
            composer.message(ImapConstants.FETCH_MODSEQ);
            composer.openParen();
            composer.skipNextSpace();
            composer.message(modSeq.asLong());
            composer.closeParen();
        }
    }

    
    private void encodeBody(ImapResponseComposer composer, Structure body) throws IOException {
        if (body != null) {
            composer.message(ImapConstants.FETCH_BODY);
            encodeStructure(composer, body, false, false);
        }
    }

    private void encodeBodyStructure(ImapResponseComposer composer, Structure bodyStructure) throws IOException {
        if (bodyStructure != null) {
            composer.message(ImapConstants.FETCH_BODY_STRUCTURE);
            encodeStructure(composer, bodyStructure, true, false);
        }
    }

    private void encodeStructure(ImapResponseComposer composer, Structure structure, boolean includeExtensions, boolean isInnerPart) throws IOException {

        final String mediaType;
        final String subType;
        final String rawMediaType = structure.getMediaType();
        if (rawMediaType == null) {
            mediaType = ImapConstants.MIME_TYPE_TEXT;
            subType = ImapConstants.MIME_SUBTYPE_PLAIN;
        } else {
            mediaType = rawMediaType;
            subType = structure.getSubType();
        }
        encodeStructure(composer, structure, includeExtensions, mediaType, subType, isInnerPart);
    }

    private void encodeStructure(ImapResponseComposer composer, Structure structure, boolean includeExtensions, String mediaType, String subType, boolean isInnerPart) throws IOException {
        //
        // Workaround for broken clients
        // See IMAP-91
        //
        final boolean includeOptionalExtensions = includeExtensions && !neverAddBodyStructureExtensions;
        if (isInnerPart) {
            composer.skipNextSpace();
        }
        if (ImapConstants.MIME_TYPE_MULTIPART.equalsIgnoreCase(mediaType)) {

            encodeMultipart(composer, structure, subType, includeOptionalExtensions);

        } else {
            if (ImapConstants.MIME_TYPE_MESSAGE.equalsIgnoreCase(mediaType) && ImapConstants.MIME_SUBTYPE_RFC822.equalsIgnoreCase(subType)) {

                encodeRfc822Message(composer, structure, mediaType, subType, includeOptionalExtensions);
            } else {
                encodeBasic(composer, structure, includeOptionalExtensions, mediaType, subType);
            }
        }
    }

    private void encodeBasic(ImapResponseComposer composer, Structure structure, boolean includeExtensions, String mediaType, String subType) throws IOException {
        if (ImapConstants.MIME_TYPE_TEXT.equalsIgnoreCase(mediaType)) {

            final long lines = structure.getLines();

            encodeBodyFields(composer, structure, mediaType, subType);
            composer.message(lines);
        } else {
            encodeBodyFields(composer, structure, mediaType, subType);
        }
        if (includeExtensions) {
            encodeOnePartBodyExtensions(composer, structure);
        }
        composer.closeParen();
    }

    private void encodeOnePartBodyExtensions(ImapResponseComposer composer, Structure structure) throws IOException {
        final String md5 = structure.getMD5();
        final List<String> languages = structure.getLanguages();
        final String location = structure.getLocation();
        nillableQuote(composer, md5);
        bodyFldDsp(structure, composer);
        nillableQuotes(composer, languages);
        nillableQuote(composer, location);
    }

    private ImapResponseComposer bodyFldDsp(Structure structure, ImapResponseComposer composer) throws IOException {
        final String disposition = structure.getDisposition();
        if (disposition == null) {
            composer.nil();
        } else {
            composer.openParen();
            composer.quote(disposition);
            final Map<String, String> params = structure.getDispositionParams();
            bodyFldParam(params, composer);
            composer.closeParen();
        }
        return composer;
    }

    private void bodyFldParam(Map<String, String> params, ImapResponseComposer composer) throws IOException {
        if (params == null || params.isEmpty()) {
            composer.nil();
        } else {
            composer.openParen();
            final Set<String> keySet = params.keySet();
            final Collection<String> names = new TreeSet<>(keySet);
            for (String name : names) {
                final String value = params.get(name);
                if (value == null) {
                    LOGGER.debug("Disposition parameter {} has no matching value", name);
                } else {
                    composer.quote(name);
                    composer.quote(value);
                }
            }
            composer.closeParen();
        }
    }

    private void encodeBodyFields(ImapResponseComposer composer, Structure structure, String mediaType, String subType) throws IOException {
        final List<String> bodyParams = structure.getParameters();
        final String id = structure.getId();
        final String description = structure.getDescription();
        final String encoding = structure.getEncoding();
        final long octets = structure.getOctets();
        composer.openParen().quoteUpperCaseAscii(mediaType).quoteUpperCaseAscii(subType);
        nillableQuotes(composer, bodyParams);
        nillableQuote(composer, id);
        nillableQuote(composer, description);
        composer.quoteUpperCaseAscii(encoding).message(octets);
    }

    private void encodeMultipart(ImapResponseComposer composer, Structure structure, String subType, boolean includeExtensions) throws IOException {
        composer.openParen();

        for (Iterator<Structure> it = structure.parts(); it.hasNext();) {
            final Structure part = it.next();
            encodeStructure(composer, part, includeExtensions, true);
        }

        composer.quoteUpperCaseAscii(subType);
        if (includeExtensions) {
            final List<String> languages = structure.getLanguages();
            nillableQuotes(composer, structure.getParameters());
            bodyFldDsp(structure, composer);
            nillableQuotes(composer, languages);
            nillableQuote(composer, structure.getLocation());
        }
        composer.closeParen();
    }

    private void encodeRfc822Message(ImapResponseComposer composer, Structure structure, String mediaType, String subType, boolean includeExtensions) throws IOException {
        final long lines = structure.getLines();
        final FetchResponse.Envelope envelope = structure.getEnvelope();
        final FetchResponse.Structure embeddedStructure = structure.getBody();

        encodeBodyFields(composer, structure, mediaType, subType);
        encodeEnvelope(composer, envelope, false);
        encodeStructure(composer, embeddedStructure, includeExtensions, false);
        composer.message(lines);

        if (includeExtensions) {
            encodeOnePartBodyExtensions(composer, structure);
        }
        composer.closeParen();
    }

    private void encodeBodyElements(ImapResponseComposer composer, List<FetchResponse.BodyElement> elements) throws IOException {
        if (elements != null) {
            for (FetchResponse.BodyElement element : elements) {
                final String name = element.getName();
                composer.message(name);
                composer.literal(element);
            }
        }
    }

    private void encodeSize(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final Long size = fetchResponse.getSize();
        if (size != null) {
            // TODO: add method to composer
            composer.message(RFC_822_SIZE);
            composer.message(size.intValue());
        }
    }

    private void encodeInternalDate(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final Date internalDate = fetchResponse.getInternalDate();
        if (internalDate != null) {
            // TODO: add method to composer
            composer.message(INTERNALDATE);
            composer.quote(EncoderUtils.encodeDateTime(internalDate));
        }
    }

    private void encodeSaveDate(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final Optional<Date> saveDate = fetchResponse.getSaveDate();
        if (isSaveDateFetched(saveDate)) {
            composer.message(SAVEDATE);
            saveDate.ifPresentOrElse(Throwing.consumer(date -> composer.quote(EncoderUtils.encodeDateTime(date))),
                Throwing.runnable(composer::nil));
        }
    }

    private boolean isSaveDateFetched(Optional<Date> saveDate) {
        return saveDate != null;
    }

    private void encodeUid(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final MessageUid uid = fetchResponse.getUid();
        if (uid != null) {
            composer.message(ImapConstants.UID);
            composer.message(uid.asLong());
        }
    }

    private void encodeEmailId(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final MessageId emailId = fetchResponse.getEmailId();
        if (emailId != null) {
            composer.message(ImapConstants.EMAILID);
            composer.openParen();
            composer.message(emailId.serialize());
            composer.closeParen();
        }
    }

    private void encodeThreadId(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final ThreadId threadId = fetchResponse.getThreadId();
        if (threadId != null) {
            composer.message(ImapConstants.THREADID);
            composer.openParen();
            composer.message(threadId.serialize());
            composer.closeParen();
        }
    }


    private void encodeFlags(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final Flags flags = fetchResponse.getFlags();
        if (flags != null) {
            composer.flags(flags);
        }
    }

    private void encodeEnvelope(ImapResponseComposer composer, FetchResponse fetchResponse) throws IOException {
        final FetchResponse.Envelope envelope = fetchResponse.getEnvelope();
        encodeEnvelope(composer, envelope, true);
    }

    private void encodeEnvelope(ImapResponseComposer composer, FetchResponse.Envelope envelope, boolean prefixWithName) throws IOException {
        if (envelope != null) {
            final String date = envelope.getDate();
            final String subject = envelope.getSubject();
            final FetchResponse.Envelope.Address[] from = envelope.getFrom();
            final FetchResponse.Envelope.Address[] sender = envelope.getSender();
            final FetchResponse.Envelope.Address[] replyTo = envelope.getReplyTo();
            final FetchResponse.Envelope.Address[] to = envelope.getTo();
            final FetchResponse.Envelope.Address[] cc = envelope.getCc();
            final FetchResponse.Envelope.Address[] bcc = envelope.getBcc();
            final String inReplyTo = envelope.getInReplyTo();
            final String messageId = envelope.getMessageId();

            if (prefixWithName) {
                composer.message(ENVELOPE);
            }
            composer.openParen();
            nillableQuote(composer, date);
            nillableQuote(composer, subject);
            encodeAddresses(composer, from);
            encodeAddresses(composer, sender);
            encodeAddresses(composer, replyTo);
            encodeAddresses(composer, to);
            encodeAddresses(composer, cc);
            encodeAddresses(composer, bcc);
            
            nillableQuote(composer, inReplyTo);
            nillableQuote(composer, messageId);
            composer.closeParen();
        }
    }

    private void encodeAddresses(ImapResponseComposer composer, FetchResponse.Envelope.Address[] addresses) throws IOException {
        if (addresses == null || addresses.length == 0) {
            composer.nil();
        } else {
            composer.openParen();
            for (FetchResponse.Envelope.Address address : addresses) {
                encodeAddress(composer, address);
            }
            composer.closeParen();
        }
    }

    private void encodeAddress(ImapResponseComposer composer, FetchResponse.Envelope.Address address) throws IOException {
        final String name = address.getPersonalName();
        final String domainList = address.getAtDomainList();
        final String mailbox = address.getMailboxName();
        final String host = address.getHostName();
        composer.skipNextSpace().openParen();
        nillableQuote(composer, name);
        nillableQuote(composer, domainList);
        nillableQuote(composer, mailbox);
        nillableQuote(composer,host).closeParen();
    }
    


    private ImapResponseComposer nillableQuote(ImapResponseComposer composer, String message) throws IOException {
        if (message == null) {
            composer.nil();
        } else {
            composer.quote(message);
        }
        return composer;
    }


    private ImapResponseComposer nillableQuotes(ImapResponseComposer composer, List<String> quotes) throws IOException {
        if (quotes == null || quotes.size() == 0) {
            composer.nil();
        } else {
            composer.openParen();
            for (String string : quotes) {
                nillableQuote(composer,string);
            }
            composer.closeParen();
        }
        return composer;
    }

}
