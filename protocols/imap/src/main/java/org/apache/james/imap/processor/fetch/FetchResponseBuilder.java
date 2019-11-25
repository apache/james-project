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

import javax.mail.Flags;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.api.process.SelectedMailbox;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MessageRangeException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimePath;

public final class FetchResponseBuilder {

    private final EnvelopeBuilder envelopeBuilder;

    private int msn;

    private MessageUid uid;

    private Flags flags;

    private Date internalDate;

    private Long size;
    
    private ModSeq modSeq;

    private List<FetchResponse.BodyElement> elements;

    private FetchResponse.Envelope envelope;

    private FetchResponse.Structure body;

    private FetchResponse.Structure bodystructure;

    public FetchResponseBuilder(EnvelopeBuilder envelopeBuilder) {
        super();
        this.envelopeBuilder = envelopeBuilder;
    }

    public void reset(int msn) {
        this.msn = msn;
        uid = null;
        flags = null;
        internalDate = null;
        size = null;
        body = null;
        bodystructure = null;
        elements = null;
        modSeq = null;
    }

    public void setUid(MessageUid resultUid) {
        this.uid = resultUid;
    }

    private void setModSeq(ModSeq modSeq) {
        this.modSeq = modSeq;
    }

    
    public void setFlags(Flags flags) {
        this.flags = flags;
    }

    public FetchResponse build() {
        return new FetchResponse(msn, flags, uid, modSeq, internalDate, size, envelope, body, bodystructure, elements);
    }

    public FetchResponse build(FetchData fetch, MessageResult result, MessageManager mailbox, ImapSession session, boolean useUids) throws MessageRangeException, MailboxException {
        final SelectedMailbox selected = session.getSelected();
        final MessageUid resultUid = result.getUid();
        final int resultMsn = selected.msn(resultUid);

        if (resultMsn == SelectedMailbox.NO_SUCH_MESSAGE) {
            throw new MessageRangeException("No such message found with uid " + resultUid);
        }

        reset(resultMsn);
        // setMsn(resultMsn);

        // Check if this fetch will cause the "SEEN" flag to be set on this
        // message. If so, update the flags, and ensure that a flags response is
        // included in the response.
        final MailboxSession mailboxSession = ImapSessionUtils.getMailboxSession(session);
        boolean ensureFlagsResponse = false;
        final Flags resultFlags = result.getFlags();
        if (fetch.isSetSeen() && !resultFlags.contains(Flags.Flag.SEEN)) {
            mailbox.setFlags(new Flags(Flags.Flag.SEEN), MessageManager.FlagsUpdateMode.ADD, MessageRange.one(resultUid), mailboxSession);
            resultFlags.add(Flags.Flag.SEEN);
            ensureFlagsResponse = true;
        }

        // FLAGS response
        if (fetch.isFlags() || ensureFlagsResponse) {
            if (selected.isRecent(resultUid)) {
                resultFlags.add(Flags.Flag.RECENT);
            }
            setFlags(resultFlags);
        }

        // INTERNALDATE response
        if (fetch.isInternalDate()) {
            setInternalDate(result.getInternalDate());
        }

        // RFC822.SIZE response
        if (fetch.isSize()) {
            setSize(result.getSize());
        }

        if (fetch.isEnvelope()) {
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
        if (fetch.isBody() || fetch.isBodyStructure()) {
            // BODY response
            //
            // the STRUCTURE is only needed when no specific element is requested otherwise we don't need 
            // to access it and may be able to not parse the message
            //
            // See IMAP-333
            if (fetch.isBody() && this.elements.isEmpty()) {
                body = new MimeDescriptorStructure(false, result.getMimeDescriptor(), envelopeBuilder);
            }

            // BODYSTRUCTURE response
            if (fetch.isBodyStructure()) {
                bodystructure = new MimeDescriptorStructure(true, result.getMimeDescriptor(), envelopeBuilder);
            }
        }
        // UID response
        if (fetch.isUid()) {
            setUid(resultUid);
        }

        
        if (fetch.isModSeq()) {
            long changedSince = fetch.getChangedSince();
            if (changedSince != -1) {
                // check if the modsequence if higher then the one specified by the CHANGEDSINCE option
                if (changedSince < result.getModSeq().asLong()) {
                    setModSeq(result.getModSeq());
                }
            } else {
                setModSeq(result.getModSeq());
            }
        }
        return build();
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
        final int specifier = fetchElement.getSectionType();
        final int[] path = fetchElement.getPath();
        final Collection<String> names = fetchElement.getFieldNames();
        final boolean isBase = (path == null || path.length == 0);
        final FetchResponse.BodyElement fullResult = bodyContent(messageResult, name, specifier, path, names, isBase);
        return wrapIfPartialFetch(firstOctet, numberOfOctets, fullResult);

    }

    private FetchResponse.BodyElement bodyContent(MessageResult messageResult, String name, int specifier, int[] path, Collection<String> names, boolean isBase) throws MailboxException {
        final FetchResponse.BodyElement fullResult;

        switch (specifier) {
        case BodyFetchElement.CONTENT:
            fullResult = content(messageResult, name, path, isBase);
            break;

        case BodyFetchElement.HEADER_FIELDS:
            fullResult = fields(messageResult, name, path, names, isBase);
            break;

        case BodyFetchElement.HEADER_NOT_FIELDS:
            fullResult = fieldsNot(messageResult, name, path, names, isBase);
            break;

        case BodyFetchElement.MIME:
            fullResult = mimeHeaders(messageResult, name, path, isBase);
            break;
        case BodyFetchElement.HEADER:
            fullResult = headers(messageResult, name, path, isBase);
            break;

        case BodyFetchElement.TEXT:
            fullResult = text(messageResult, name, path, isBase);
            break;

        default:
            fullResult = null;
            break;
        }
        return fullResult;
    }

    private FetchResponse.BodyElement wrapIfPartialFetch(Long firstOctet, Long numberOfOctets, FetchResponse.BodyElement fullResult) {
        final FetchResponse.BodyElement result;
        if (firstOctet == null) {
            result = fullResult;
        } else {
            final long numberOfOctetsAsLong;
            if (numberOfOctets == null) {
                numberOfOctetsAsLong = Long.MAX_VALUE;
            } else {
                numberOfOctetsAsLong = numberOfOctets;
            }
            final long firstOctetAsLong = firstOctet;

            result = new PartialFetchBodyElement(fullResult, firstOctetAsLong, numberOfOctetsAsLong);
            
           
        }
        return result;
    }

    private FetchResponse.BodyElement text(MessageResult messageResult, String name, int[] path, boolean isBase) throws MailboxException {
        final FetchResponse.BodyElement result;
        Content body;
        if (isBase) {
            try {
                body = messageResult.getBody();
            } catch (IOException e) {
                throw new MailboxException("Unable to get TEXT of body", e);
            }
        } else {
            MimePath mimePath = new MimePath(path);
            body = messageResult.getBody(mimePath);
        }
        if (body == null) {
            body = new EmptyContent();
        }
        result = new ContentBodyElement(name, body);
        return result;
    }

    private FetchResponse.BodyElement mimeHeaders(MessageResult messageResult, String name, int[] path, boolean isBase) throws MailboxException {
        final FetchResponse.BodyElement result;
        final Iterator<Header> headers = getMimeHeaders(messageResult, path, isBase);
        List<Header> lines = MessageResultUtils.getAll(headers);
        result = new MimeBodyElement(name, lines);
        return result;
    }

    private HeaderBodyElement headerBodyElement(MessageResult messageResult, String name, List<Header> lines, int[] path, boolean isBase) throws MailboxException {
        final HeaderBodyElement result = new HeaderBodyElement(name, lines);
        // if the size is 2 we had found not header and just want to write the empty line with CLRF terminated
        // so check if there is a content for it. If not we MUST NOT write the empty line in any case
        // as stated in rfc3501
        if (result.size() == 2) {
            // Check if its base as this can give use a more  correctly working check
            // to see if we need to write the newline out to the client. 
            // This is related to IMAP-298
            if (isBase) {
                if (messageResult.getSize() - result.size() <= 0) {
                    // Seems like this mail has no body 
                    result.noBody();
                }
              
            } else {
                try {
                    if (content(messageResult, name, path, isBase).size() <= 0) {
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
    
    private FetchResponse.BodyElement headers(MessageResult messageResult, String name, int[] path, boolean isBase) throws MailboxException {      
        if (isBase) {
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
        } else {
            final Iterator<Header> headers = getHeaders(messageResult, path, isBase);
            List<Header> lines = MessageResultUtils.getAll(headers);
            return headerBodyElement(messageResult, name, lines, path, isBase);
        }
    }

    private FetchResponse.BodyElement fieldsNot(MessageResult messageResult, String name, int[] path, Collection<String> names, boolean isBase) throws MailboxException {
        final Iterator<Header> headers = getHeaders(messageResult, path, isBase);
        List<Header> lines = MessageResultUtils.getNotMatching(names, headers);
        
        return headerBodyElement(messageResult, name, lines, path, isBase);
    }

    private FetchResponse.BodyElement fields(MessageResult messageResult, String name, int[] path, Collection<String> names, boolean isBase) throws MailboxException {
        final Iterator<Header> headers = getHeaders(messageResult, path, isBase);
        List<Header> lines = MessageResultUtils.getMatching(names, headers);
        return headerBodyElement(messageResult, name, lines, path, isBase);
    }

    private Iterator<Header> getHeaders(MessageResult messageResult, int[] path, boolean isBase) throws MailboxException {
        final Iterator<Header> headers;
        if (isBase) {
            headers = messageResult.getHeaders().headers();
        } else {
            MimePath mimePath = new MimePath(path);
            headers = messageResult.iterateHeaders(mimePath);
        }
        return headers;
    }

    private Iterator<Header> getMimeHeaders(MessageResult messageResult, int[] path, boolean isBase) throws MailboxException {
        MimePath mimePath = new MimePath(path);
        return messageResult.iterateMimeHeaders(mimePath);
    }

    private FetchResponse.BodyElement content(MessageResult messageResult, String name, int[] path, boolean isBase) throws MailboxException {
        final FetchResponse.BodyElement result;
        Content full;
        if (isBase) {
            try {
                full = messageResult.getFullContent();

            } catch (IOException e) {
                throw new MailboxException("Unable to get content", e);
            }
        } else {
            MimePath mimePath = new MimePath(path);
            full = messageResult.getMimeBody(mimePath);
        }

        if (full == null) {
            full = new EmptyContent();
        }
        result = new ContentBodyElement(name, full);
        return result;
    }
}