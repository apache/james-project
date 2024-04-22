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

/**
 * 
 */
package org.apache.james.imap.processor.fetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.FetchData.Item;
import org.apache.james.imap.api.message.SectionType;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageSequenceNumber;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimePath;
import org.apache.james.mailbox.model.ThreadId;

import reactor.core.publisher.Mono;

public final class FetchResponseBuilder {
    private final EnvelopeBuilder envelopeBuilder;

    private MessageSequenceNumber msn;
    private MessageId messageId;
    private ThreadId threadId;
    private MessageUid uid;
    private Flags flags;
    private Date internalDate;
    private Optional<Date> saveDate;
    private Long size;
    private ModSeq modSeq;
    private List<FetchResponse.BodyElement> elements;
    private FetchResponse.Envelope envelope;
    private FetchResponse.Structure body;
    private FetchResponse.Structure bodystructure;

    public FetchResponseBuilder(EnvelopeBuilder envelopeBuilder) {
        this.envelopeBuilder = envelopeBuilder;
    }

    public void reset(MessageSequenceNumber msn) {
        this.msn = msn;
        messageId = null;
        threadId = null;
        uid = null;
        flags = null;
        internalDate = null;
        saveDate = null;
        size = null;
        body = null;
        bodystructure = null;
        elements = null;
        modSeq = null;
    }

    public void setMessageId(MessageId messageId) {
        this.messageId = messageId;
    }

    public void setThreadId(ThreadId threadId) {
        this.threadId = threadId;
    }

    public void setUid(MessageUid resultUid) {
        this.uid = resultUid;
    }

    private void setModSeq(ModSeq modSeq) {
        this.modSeq = modSeq;
    }

    private void setSaveDate(Optional<Date> saveDate) {
        this.saveDate = saveDate;
    }
    
    public void setFlags(Flags flags) {
        this.flags = flags;
    }

    public FetchResponse build() {
        return new FetchResponse(msn, flags, uid, saveDate, modSeq, internalDate, size, envelope, body, bodystructure, elements, messageId, threadId);
    }

    public Mono<FetchResponse> build(FetchData fetch, MessageResult result, MessageManager mailbox, SelectedMailbox selectedMailbox, MailboxSession mailboxSession) throws MessageRangeException, MailboxException {
        final MessageUid resultUid = result.getUid();
        return selectedMailbox.msn(resultUid).fold(() -> {
            throw new MessageRangeException("No such message found with uid " + resultUid);
        }, msn -> {
            reset(msn);

            // INTERNALDATE response
            if (fetch.contains(Item.INTERNAL_DATE)) {
                setInternalDate(result.getInternalDate());
            }

            // RFC822.SIZE response
            if (fetch.contains(Item.SIZE)) {
                setSize(result.getSize());
            }

            if (fetch.contains(Item.ENVELOPE)) {
                this.envelope = buildEnvelope(result);
            }

            // BODY part responses.
            Collection<BodyFetchElement> elements = fetch.getBodyElements();
            this.elements = new ArrayList<>();
            for (BodyFetchElement fetchElement : elements) {
                final FetchResponse.BodyElement element = bodyFetch(result, fetchElement);
                if (element != null) {
                    this.elements.add(element);
                }
            }

            // Only create when needed
            if (fetch.contains(Item.BODY) || fetch.contains(Item.BODY_STRUCTURE)) {
                // BODY response
                //
                // the STRUCTURE is only needed when no specific element is requested otherwise we don't need
                // to access it and may be able to not parse the message
                //
                // See IMAP-333
                if (fetch.contains(Item.BODY) && this.elements.isEmpty()) {
                    body = new MimeDescriptorStructure(false, result.getMimeDescriptor(), envelopeBuilder);
                }

                // BODYSTRUCTURE response
                if (fetch.contains(Item.BODY_STRUCTURE)) {
                    bodystructure = new MimeDescriptorStructure(true, result.getMimeDescriptor(), envelopeBuilder);
                }
            }

            addUid(fetch, resultUid);

            addThreadId(fetch, result.getThreadId());
            addMessageId(fetch, result.getMessageId());
            addSaveDate(fetch, result.getSaveDate());

            addModSeq(fetch, result.getModSeq());

            // FLAGS response
            // Check if this fetch will cause the "SEEN" flag to be set on this
            // message. If so, update the flags, and ensure that a flags response is
            // included in the response.
            if (fetch.isSetSeen()) {
                return manageSeenAndAddFlags(fetch, mailbox, selectedMailbox, resultUid, mailboxSession, result.getFlags())
                    .then(Mono.fromCallable(this::build));
            } else {
                addFlags(fetch, selectedMailbox, resultUid, result.getFlags());
                return Mono.fromCallable(this::build);
            }
        });
    }

    private void addUid(FetchData fetch, MessageUid resultUid) {
        // UID response
        if (fetch.contains(Item.UID)) {
            setUid(resultUid);
        }
    }

    private void addMessageId(FetchData fetch, MessageId messageId) {
        // EMAILID response
        if (fetch.contains(Item.EMAILID)) {
            setMessageId(messageId);
        }
    }

    private void addThreadId(FetchData fetch, ThreadId threadId) {
        // THREADID response
        if (fetch.contains(Item.THREADID)) {
            setThreadId(threadId);
        }
    }

    private void addSaveDate(FetchData fetch, Optional<Date> saveDate) {
        // SAVEDATE response
        if (fetch.contains(Item.SAVEDATE)) {
            setSaveDate(saveDate);
        }
    }

    private void addModSeq(FetchData fetch, ModSeq modSeq) {
        if (fetch.contains(Item.MODSEQ)) {
            long changedSince = fetch.getChangedSince();
            if (changedSince != -1) {
                // check if the modsequence if higher then the one specified by the CHANGEDSINCE option
                if (changedSince < modSeq.asLong()) {
                    setModSeq(modSeq);
                }
            } else {
                setModSeq(modSeq);
            }
        }
    }

    private Mono<Void> manageSeenAndAddFlags(FetchData fetch, MessageManager mailbox, SelectedMailbox selected, MessageUid resultUid, MailboxSession mailboxSession, Flags flags) {
        return ensureFlagResponse(fetch, mailbox, resultUid, mailboxSession, flags)
            .doOnNext(ensureFlagsResponse -> {
                if (fetch.contains(Item.FLAGS) || ensureFlagsResponse) {
                    if (selected.isRecent(resultUid)) {
                        flags.add(Flags.Flag.RECENT);
                    }
                    setFlags(flags);
                }
            })
            .then();
    }

    private void addFlags(FetchData fetch, SelectedMailbox selected, MessageUid resultUid, Flags flags) {
        if (fetch.contains(Item.FLAGS)) {
            if (selected.isRecent(resultUid)) {
                flags.add(Flags.Flag.RECENT);
            }
            setFlags(flags);
        }
    }

    private Mono<Boolean> ensureFlagResponse(FetchData fetch, MessageManager mailbox, MessageUid resultUid, MailboxSession mailboxSession, Flags flags) {
        if (fetch.isSetSeen() && !flags.contains(Flags.Flag.SEEN)) {
            return Mono.from(mailbox.setFlagsReactive(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD, MessageRange.one(resultUid), mailboxSession))
                .then(Mono.fromCallable(() -> {
                    flags.add(Flags.Flag.SEEN);
                    return true;
                }));
        }
        return Mono.just(false);
    }

    public Mono<FetchResponse> build(FetchData fetch, ComposedMessageIdWithMetaData result, MessageManager mailbox, SelectedMailbox selectedMailbox, MailboxSession mailboxSession) throws MailboxException {
        final MessageUid resultUid = result.getComposedMessageId().getUid();
        return selectedMailbox.msn(resultUid).fold(() -> {
            throw new MessageRangeException("No such message found with uid " + resultUid);
        }, msn -> {

            reset(msn);

            // UID response
            addUid(fetch, resultUid);

            addModSeq(fetch, result.getModSeq());

            // FLAGS response
            // Check if this fetch will cause the "SEEN" flag to be set on this
            // message. If so, update the flags, and ensure that a flags response is
            // included in the response.
            return manageSeenAndAddFlags(fetch, mailbox, selectedMailbox, resultUid, mailboxSession, result.getFlags())
                .then(Mono.fromCallable(this::build));
        });
    }

    private FetchResponse.Envelope buildEnvelope(MessageResult result) throws MailboxException {
        return envelopeBuilder.buildEnvelope(result.getHeaders());
    }

    private void setSize(long size) {
        this.size = size;
    }

    public void setInternalDate(Date internalDate) {
        this.internalDate = internalDate;
    }

    private FetchResponse.BodyElement bodyFetch(MessageResult messageResult, BodyFetchElement fetchElement) throws MailboxException {
        final Long firstOctet = fetchElement.getFirstOctet();
        final Long numberOfOctets = fetchElement.getNumberOfOctets();
        final String name = fetchElement.getResponseName();
        final SectionType specifier = fetchElement.getSectionType();
        final Optional<MimePath> path = Optional.ofNullable(fetchElement.getPath())
                .filter(paths -> paths.length > 0)
                .map(MimePath::new);
        final Collection<String> names = fetchElement.getFieldNames();
        final FetchResponse.BodyElement fullResult = bodyContent(messageResult, name, specifier, path, names);
        return wrapIfPartialFetch(firstOctet, numberOfOctets, fullResult);
    }

    private FetchResponse.BodyElement bodyContent(MessageResult messageResult, String name, SectionType specifier, Optional<MimePath> path, Collection<String> names) throws MailboxException {
        switch (specifier) {
            case CONTENT:
                return content(messageResult, name, path);
            case HEADER_FIELDS:
                return fields(messageResult, name, path, names);
            case HEADER_NOT_FIELDS:
                return fieldsNot(messageResult, name, path, names);
            case MIME:
                return mimeHeaders(messageResult, name, path);
            case HEADER:
                return headers(messageResult, name, path);
            case TEXT:
                return text(messageResult, name, path);
            default:
                return null;
        }
    }

    private FetchResponse.BodyElement wrapIfPartialFetch(Long firstOctet, Long numberOfOctets, FetchResponse.BodyElement fullResult) {
        if (firstOctet == null) {
            return fullResult;
        }
        final Optional<Long> numberOfOctetsAsLong = Optional.ofNullable(numberOfOctets);
        final long firstOctetAsLong = firstOctet;
        return new PartialFetchBodyElement(fullResult, firstOctetAsLong, numberOfOctetsAsLong);
    }

    private FetchResponse.BodyElement text(MessageResult messageResult, String name, Optional<MimePath> path) throws MailboxException {
        Content body = Optional.ofNullable(getTextContent(messageResult, path))
            .orElseGet(EmptyContent::new);
        return new ContentBodyElement(name, body);
    }

    private Content getTextContent(MessageResult messageResult, Optional<MimePath> path) throws MailboxException {
        if (path.isEmpty()) {
            try {
                return messageResult.getBody();
            } catch (IOException e) {
                throw new MailboxException("Unable to get TEXT of body", e);
            }
        }
        return messageResult.getBody(path.get());
    }

    private FetchResponse.BodyElement mimeHeaders(MessageResult messageResult, String name, Optional<MimePath> path) throws MailboxException {
        final Iterator<Header> headers = getMimeHeaders(messageResult, path);
        List<Header> lines = MessageResultUtils.getAll(headers);
        return new MimeBodyElement(name, lines);
    }

    private HeaderBodyElement headerBodyElement(MessageResult messageResult, String name, List<Header> lines, Optional<MimePath> path) throws MailboxException {
        final HeaderBodyElement result = new HeaderBodyElement(name, lines);
        // if the size is 2 we had found not header and just want to write the empty line with CLRF terminated
        // so check if there is a content for it. If not we MUST NOT write the empty line in any case
        // as stated in rfc3501
        if (result.size() == 2) {
            // Check if its base as this can give use a more  correctly working check
            // to see if we need to write the newline out to the client. 
            // This is related to IMAP-298
            if (path.isEmpty()) {
                if (messageResult.getSize() - result.size() <= 0) {
                    // Seems like this mail has no body 
                    result.noBody();
                }
            } else {
                try {
                    if (content(messageResult, name, path).size() <= 0) {
                        // Seems like this mail has no body
                        result.noBody();
                    }
                } catch (IOException e) {
                    throw new MailboxException("Unable to get size of header body element", e);
                }
            }
        }
        return result;
    }
    
    private FetchResponse.BodyElement headers(MessageResult messageResult, String name, Optional<MimePath> path) throws MailboxException {
        if (path.isEmpty()) {
            // if its base we can just return the raw headers without parsing
            // them. See MAILBOX-311 and IMAP-?
            HeadersBodyElement element = new HeadersBodyElement(name, messageResult.getHeaders());
            try {
                if (messageResult.getSize() - element.size() <= 0) {
                    // Seems like this mail has no body
                    element.noBody();
                }
            } catch (IOException e) {
                throw new MailboxException("Unable to get size of header body element", e);
            }
            return element;
        }
        final Iterator<Header> headers = getHeaders(messageResult, path);
        List<Header> lines = MessageResultUtils.getAll(headers);
        return headerBodyElement(messageResult, name, lines, path);
    }

    private FetchResponse.BodyElement fieldsNot(MessageResult messageResult, String name, Optional<MimePath> path, Collection<String> names) throws MailboxException {
        final Iterator<Header> headers = getHeaders(messageResult, path);
        List<Header> lines = MessageResultUtils.getNotMatching(names, headers);
        
        return headerBodyElement(messageResult, name, lines, path);
    }

    private FetchResponse.BodyElement fields(MessageResult messageResult, String name, Optional<MimePath> path, Collection<String> names) throws MailboxException {
        final Iterator<Header> headers = getHeaders(messageResult, path);
        List<Header> lines = MessageResultUtils.getMatching(names, headers);
        return headerBodyElement(messageResult, name, lines, path);
    }

    private Iterator<Header> getHeaders(MessageResult messageResult, Optional<MimePath> path) throws MailboxException {
        if (path.isEmpty()) {
            return messageResult.getHeaders().headers();
        }
        return messageResult.iterateHeaders(path.get());
    }

    private Iterator<Header> getMimeHeaders(MessageResult messageResult, Optional<MimePath> path) throws MailboxException {
        return messageResult.iterateMimeHeaders(path.get());
    }

    private FetchResponse.BodyElement content(MessageResult messageResult, String name, Optional<MimePath> path) throws MailboxException {
        Content full =  Optional.ofNullable(getContent(messageResult, path))
            .orElseGet(EmptyContent::new);;
        return new ContentBodyElement(name, full);
    }

    private Content getContent(MessageResult messageResult, Optional<MimePath> path) throws MailboxException {
        if (path.isEmpty()) {
            try {
                return messageResult.getFullContent();

            } catch (IOException e) {
                throw new MailboxException("Unable to get content", e);
            }
        }
        return messageResult.getMimeBody(path.get());
    }
}