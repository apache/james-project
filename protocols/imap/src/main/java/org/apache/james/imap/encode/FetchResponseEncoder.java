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
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.mail.Flags;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FetchResponse.Structure;
import org.slf4j.Logger;

public class FetchResponseEncoder extends AbstractChainedImapEncoder {
    public static final String ENVELOPE = "ENVELOPE";

    /** Disables all optional BODYSTRUCTURE extensions */
    private final boolean neverAddBodyStructureExtensions;

    /**
     * Constructs an encoder for FETCH messages.
     * 
     * @param next
     *            not null
     * @param neverAddBodyStructureExtensions
     *            true to activate a workaround for broken clients who cannot
     *            parse BODYSTRUCTURE extensions, false to fully support RFC3501
     */
    public FetchResponseEncoder(final ImapEncoder next, final boolean neverAddBodyStructureExtensions) {
        super(next);
        this.neverAddBodyStructureExtensions = neverAddBodyStructureExtensions;
    }

    public boolean isAcceptable(final ImapMessage message) {
        return (message instanceof FetchResponse);
    }

    protected void doEncode(ImapMessage acceptableMessage, ImapResponseComposer composer, ImapSession session) throws IOException {
        if (acceptableMessage instanceof FetchResponse) {
            final FetchResponse fetchResponse = (FetchResponse) acceptableMessage;
            final long messageNumber = fetchResponse.getMessageNumber();
            
        	composer.untagged().message(messageNumber).message(ImapConstants.FETCH_COMMAND_NAME).openParen();

            
            encodeModSeq(composer, fetchResponse);
            encodeFlags(composer, fetchResponse);
            encodeInternalDate(composer, fetchResponse);
            encodeSize(composer, fetchResponse);
            encodeEnvelope(composer, fetchResponse);
            encodeBody(composer, fetchResponse.getBody(), session);
            encodeBodyStructure(composer, fetchResponse.getBodyStructure(), session);
            encodeUid(composer, fetchResponse);
            encodeBodyElements(composer, fetchResponse.getElements());
            
            composer.closeParen().end();
        }
    }

    // Handle the MODSEQ 
    private void encodeModSeq(ImapResponseComposer composer, FetchResponse response) throws IOException {
        Long modSeq = response.getModSeq();
        if (modSeq != null) {
            composer.message(ImapConstants.FETCH_MODSEQ);
            composer.openParen();
            composer.skipNextSpace();
            composer.message(modSeq);
            composer.closeParen();
        }
    }

    
    private void encodeBody(ImapResponseComposer composer, Structure body, ImapSession session) throws IOException {
        if (body != null) {
            composer.message(ImapConstants.FETCH_BODY);
            encodeStructure(composer, body, false, false, session);
        }
    }

    private void encodeBodyStructure(ImapResponseComposer composer, Structure bodyStructure, ImapSession session) throws IOException {
        if (bodyStructure != null) {
            composer.message(ImapConstants.FETCH_BODY_STRUCTURE);
            encodeStructure(composer, bodyStructure, true, false, session);
        }
    }

    private void encodeStructure(final ImapResponseComposer composer, final Structure structure, final boolean includeExtensions, final boolean isInnerPart, ImapSession session) throws IOException {

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
        encodeStructure(composer, structure, includeExtensions, mediaType, subType, isInnerPart, session);
    }

    private void encodeStructure(final ImapResponseComposer composer, final Structure structure, final boolean includeExtensions, final String mediaType, final String subType, boolean isInnerPart, ImapSession session) throws IOException {
        //
        // Workaround for broken clients
        // See IMAP-91
        //
        final boolean includeOptionalExtensions = includeExtensions && !neverAddBodyStructureExtensions;
        if (isInnerPart) {
            composer.skipNextSpace();
        }
        if (ImapConstants.MIME_TYPE_MULTIPART.equalsIgnoreCase(mediaType)) {

            encodeMultipart(composer, structure, subType, includeOptionalExtensions, session);

        } else {
            if (ImapConstants.MIME_TYPE_MESSAGE.equalsIgnoreCase(mediaType) && ImapConstants.MIME_SUBTYPE_RFC822.equalsIgnoreCase(subType)) {

                encodeRfc822Message(composer, structure, mediaType, subType, includeOptionalExtensions, session);
            } else {
                encodeBasic(composer, structure, includeOptionalExtensions, mediaType, subType, session);
            }
        }
    }

    private void encodeBasic(final ImapResponseComposer composer, final Structure structure, final boolean includeExtensions, final String mediaType, final String subType, ImapSession session) throws IOException {
        if (ImapConstants.MIME_TYPE_TEXT.equalsIgnoreCase(mediaType)) {

            final long lines = structure.getLines();

            encodeBodyFields(composer, structure, mediaType, subType);
            composer.message(lines);
        } else {
            encodeBodyFields(composer, structure, mediaType, subType);
        }
        if (includeExtensions) {
            encodeOnePartBodyExtensions(composer, structure, session);
        }
        composer.closeParen();
    }

    private void encodeOnePartBodyExtensions(final ImapResponseComposer composer, final Structure structure, ImapSession session) throws IOException {
        final String md5 = structure.getMD5();
        final List<String> languages = structure.getLanguages();
        final String location = structure.getLocation();
        nillableQuote(composer, md5);
        bodyFldDsp(structure, composer, session);
        nillableQuotes(composer, languages);
        nillableQuote(composer, location);
    }

    private ImapResponseComposer bodyFldDsp(final Structure structure, final ImapResponseComposer composer, ImapSession session) throws IOException {
        final String disposition = structure.getDisposition();
        if (disposition == null) {
            composer.nil();
        } else {
            composer.openParen();
            composer.quote(disposition);
            final Map<String, String> params = structure.getDispositionParams();
            bodyFldParam(params, composer, session);
            composer.closeParen();
        }
        return composer;
    }

    private void bodyFldParam(final Map<String, String> params, final ImapResponseComposer composer, ImapSession session) throws IOException {
        if (params == null || params.isEmpty()) {
            composer.nil();
        } else {
            composer.openParen();
            final Set<String> keySet = params.keySet();
            final Collection<String> names = new TreeSet<String>(keySet);
            for (Iterator<String> iter = names.iterator(); iter.hasNext();) {
                final String name = iter.next();
                final String value = params.get(name);
                if (value == null) {
                    final Logger logger = session.getLog();
                    logger.warn("Disposition parameter name has no value.");
                    if (logger.isDebugEnabled()) {
                        logger.debug("Disposition parameter " + name + " has no matching value");
                    }
                } else {
                    composer.quote(name);
                    composer.quote(value);
                }
            }
            composer.closeParen();
        }
    }

    private void encodeBodyFields(final ImapResponseComposer composer, final Structure structure, final String mediaType, final String subType) throws IOException {
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

    private void encodeMultipart(ImapResponseComposer composer, Structure structure, final String subType, final boolean includeExtensions, ImapSession session) throws IOException {
        composer.openParen();

        for (Iterator<Structure> it = structure.parts(); it.hasNext();) {
            final Structure part = it.next();
            encodeStructure(composer, part, includeExtensions, true, session);
        }

        composer.quoteUpperCaseAscii(subType);
        if (includeExtensions) {
            final List<String> languages = structure.getLanguages();
            nillableQuotes(composer, structure.getParameters());
            bodyFldDsp(structure, composer, session);
            nillableQuotes(composer, languages);
            nillableQuote(composer, structure.getLocation());
        }
        composer.closeParen();
    }

    private void encodeRfc822Message(ImapResponseComposer composer, Structure structure, final String mediaType, final String subType, final boolean includeExtensions, ImapSession session) throws IOException {
        final long lines = structure.getLines();
        final FetchResponse.Envelope envelope = structure.getEnvelope();
        final FetchResponse.Structure embeddedStructure = structure.getBody();

        encodeBodyFields(composer, structure, mediaType, subType);
        encodeEnvelope(composer, envelope, false);
        encodeStructure(composer, embeddedStructure, includeExtensions, false, session);
        composer.message(lines);

        if (includeExtensions) {
            encodeOnePartBodyExtensions(composer, structure, session);
        }
        composer.closeParen();
    }

    private void encodeBodyElements(final ImapResponseComposer composer, final List<FetchResponse.BodyElement> elements) throws IOException {
        if (elements != null) {
            for (final Iterator<FetchResponse.BodyElement> it = elements.iterator(); it.hasNext();) {
                FetchResponse.BodyElement element = it.next();
                final String name = element.getName();
                composer.message(name);
                composer.literal(element);
            }
        }
    }

    private void encodeSize(ImapResponseComposer composer, final FetchResponse fetchResponse) throws IOException {
        final Long size = fetchResponse.getSize();
        if (size != null) {
            // TODO: add method to composer
            composer.message("RFC822.SIZE");
            composer.message(size.intValue());
        }
    }

    private void encodeInternalDate(ImapResponseComposer composer, final FetchResponse fetchResponse) throws IOException {
        final Date internalDate = fetchResponse.getInternalDate();
        if (internalDate != null) {
            // TODO: add method to composer
            composer.message("INTERNALDATE");
            composer.quote(EncoderUtils.encodeDateTime(internalDate));
        }
    }

    private void encodeUid(ImapResponseComposer composer, final FetchResponse fetchResponse) throws IOException {
        final Long uid = fetchResponse.getUid();
        if (uid != null) {
            composer.message(ImapConstants.UID);
            composer.message(uid.longValue());
        }
    }

    private void encodeFlags(ImapResponseComposer composer, final FetchResponse fetchResponse) throws IOException {
        final Flags flags = fetchResponse.getFlags();
        if (flags != null) {
            composer.flags(flags);
        }
    }

    private void encodeEnvelope(final ImapResponseComposer composer, final FetchResponse fetchResponse) throws IOException {
        final FetchResponse.Envelope envelope = fetchResponse.getEnvelope();
        encodeEnvelope(composer, envelope, true);
    }

    private void encodeEnvelope(final ImapResponseComposer composer, final FetchResponse.Envelope envelope, boolean prefixWithName) throws IOException {
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

    private void encodeAddresses(final ImapResponseComposer composer, final FetchResponse.Envelope.Address[] addresses) throws IOException {
        if (addresses == null || addresses.length == 0) {
            composer.nil();
        } else {
            composer.openParen();
            final int length = addresses.length;
            for (int i = 0; i < length; i++) {
                final FetchResponse.Envelope.Address address = addresses[i];
                encodeAddress(composer, address);
            }
            composer.closeParen();
        }
    }

    private void encodeAddress(final ImapResponseComposer composer, final FetchResponse.Envelope.Address address) throws IOException {
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
            for (final String string : quotes) {
            	nillableQuote(composer,string);
            }
            composer.closeParen();
        }
        return composer;
    }

}
