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
package org.apache.james.mailbox.store.mail;

import static javax.mail.Flags.Flag.RECENT;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageManager.FlagsUpdateMode;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.streams.Iterators;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Maps {@link MailboxMessage} in a {@link org.apache.james.mailbox.MessageManager}. A {@link MessageMapper} has a lifecycle from the start of a request
 * to the end of the request.
 */
public interface MessageMapper extends Mapper {
    int UNLIMITED = -1;

    /**
     * Return a {@link Iterator} which holds the messages for the given criterias
     * The list must be ordered by the {@link MailboxMessage} uid
     * 
     * @param mailbox The mailbox to search
     * @param set message range for batch processing
     * @param limit the maximal limit of returned {@link MailboxMessage}'s. Use -1 to set no limit. In any case the caller MUST not expect the limit to get applied in all cases as the implementation
     *              MAY just ignore it
     */
    Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType type, int limit)
            throws MailboxException;

    default Flux<ComposedMessageIdWithMetaData> listMessagesMetadata(Mailbox mailbox, MessageRange set) {
        return findInMailboxReactive(mailbox, set, FetchType.METADATA, UNLIMITED)
            .map(message -> new ComposedMessageIdWithMetaData(
                new ComposedMessageId(
                    message.getMailboxId(),
                    message.getMessageId(),
                    message.getUid()),
                message.createFlags(),
                message.getModSeq(),
                message.getThreadId()));
    }

    default Flux<MailboxMessage> findInMailboxReactive(Mailbox mailbox, MessageRange set, FetchType type, int limit) {
        try {
            return Iterators.toFlux(findInMailbox(mailbox, set, type, limit));
        } catch (MailboxException e) {
            return Flux.error(e);
        }
    }

    /**
     * Returns a list of {@link MessageUid} which are marked as deleted
     */
    List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) throws MailboxException;

    default Flux<MessageUid> retrieveMessagesMarkedForDeletionReactive(Mailbox mailbox, MessageRange messageRange) {
        return Flux.defer(Throwing.supplier(() -> Flux.fromIterable(retrieveMessagesMarkedForDeletion(mailbox, messageRange))).sneakyThrow())
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Return the count of messages in the mailbox
     */
    long countMessagesInMailbox(Mailbox mailbox)
            throws MailboxException;

    MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException;

    default Mono<MailboxCounters> getMailboxCountersReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> getMailboxCounters(mailbox))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Delete the given {@link MailboxMessage}
     */
    void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException;

    /**
     * Delete the given list of {@link MessageUid}
     * and return a {@link Map} which holds the uids and metadata for all deleted messages
     */
    Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException;

    default Mono<Map<MessageUid, MessageMetaData>> deleteMessagesReactive(Mailbox mailbox, List<MessageUid> uids) {
        return Mono.fromCallable(() -> deleteMessages(mailbox, uids))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Return the uid of the first unseen message. If non can be found null will get returned
     */
    MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException;

    default Mono<Optional<MessageUid>> findFirstUnseenMessageUidReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> Optional.ofNullable(findFirstUnseenMessageUid(mailbox)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Return a List of {@link MailboxMessage} which are recent.
     * The list must be ordered by the {@link MailboxMessage} uid.
     */
    List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException;

    default Mono<List<MessageUid>> findRecentMessageUidsInMailboxReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> findRecentMessageUidsInMailbox(mailbox))
            .subscribeOn(Schedulers.boundedElastic());
    }


    /**
     * Add the given {@link MailboxMessage} to the underlying storage. Be aware that implementation may choose to replace the uid of the given message while storing.
     * So you should only depend on the returned uid.
     */
    MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException;

    default Publisher<MessageMetaData> addReactive(Mailbox mailbox, MailboxMessage message) {
        return Mono.fromCallable(() -> add(mailbox, message))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER);
    }

    /**
     * Update flags for the given {@link MessageRange}. Only the flags may be modified after a message was saved to a mailbox.
     *
     * @param flagsUpdateCalculator How to update flags
     */
    Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator,
            final MessageRange set) throws MailboxException;

    default Mono<List<UpdatedFlags>> updateFlagsReactive(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange set) {
        return Mono.fromCallable(() -> (List<UpdatedFlags>) ImmutableList.copyOf(updateFlags(mailbox, flagsUpdateCalculator, set)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    default Optional<UpdatedFlags> updateFlags(Mailbox mailbox, MessageUid uid, FlagsUpdateCalculator flagsUpdateCalculator) throws MailboxException {
        return Iterators.toStream(updateFlags(mailbox, flagsUpdateCalculator, MessageRange.one(uid)))
            .findFirst();
    }

    default List<UpdatedFlags> resetRecent(Mailbox mailbox) throws MailboxException {
        final List<MessageUid> members = findRecentMessageUidsInMailbox(mailbox);
        ImmutableList.Builder<UpdatedFlags> result = ImmutableList.builder();

        FlagsUpdateCalculator calculator = new FlagsUpdateCalculator(new Flags(RECENT), FlagsUpdateMode.REMOVE);
        // Convert to MessageRanges so we may be able to optimize the flag update
        List<MessageRange> ranges = MessageRange.toRanges(members);
        for (MessageRange range : ranges) {
            result.addAll(updateFlags(mailbox, calculator, range));
        }
        return result.build();
    }

    default Mono<List<UpdatedFlags>> resetRecentReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> resetRecent(mailbox))
            .subscribeOn(Schedulers.boundedElastic());
    }

    
    /**
     * Copy the given {@link MailboxMessage} to a new mailbox and return the uid of the copy. Be aware that the given uid is just a suggestion for the uid of the copied
     * message. Implementation may choose to use a different one, so only depend on the returned uid!
     * 
     * @param mailbox the Mailbox to copy to
     * @param original the original to copy
     */
    MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException;

    default List<MessageMetaData> copy(Mailbox mailbox, List<MailboxMessage> original) throws MailboxException {
        return original.stream()
            .map(Throwing.<MailboxMessage, MessageMetaData>function(message -> copy(mailbox, message)).sneakyThrow())
            .collect(ImmutableList.toImmutableList());
    }

    default Mono<MessageMetaData> copyReactive(Mailbox mailbox, MailboxMessage original) {
        return Mono.fromCallable(() -> copy(mailbox, original))
            .subscribeOn(Schedulers.boundedElastic());
    }

    default Mono<List<MessageMetaData>> copyReactive(Mailbox mailbox, List<MailboxMessage> original) {
        return Mono.fromCallable(() -> copy(mailbox, original))
            .subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * Move the given {@link MailboxMessage} to a new mailbox and return the uid of the moved. Be aware that the given uid is just a suggestion for the uid of the moved
     * message. Implementation may choose to use a different one, so only depend on the returned uid!
     * 
     * @param mailbox the Mailbox to move to
     * @param original the original to move
     */
    MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException;

    default List<MessageMetaData> move(Mailbox mailbox, List<MailboxMessage> original) throws MailboxException {
        return original.stream()
            .map(Throwing.<MailboxMessage, MessageMetaData>function(message -> move(mailbox, message)).sneakyThrow())
            .collect(ImmutableList.toImmutableList());
    }

    default Mono<MessageMetaData> moveReactive(Mailbox mailbox, MailboxMessage original) {
        return Mono.fromCallable(() -> move(mailbox, original))
            .subscribeOn(Schedulers.boundedElastic());
    }

    default Mono<List<MessageMetaData>> moveReactive(Mailbox mailbox, List<MailboxMessage> original) {
        return Flux.fromIterable(original)
            .concatMap(message -> moveReactive(mailbox, message), ReactorUtils.DEFAULT_CONCURRENCY)
            .collectList();
    }

    
    /**
     * Return the last uid which were used for storing a MailboxMessage in the {@link Mailbox} or null if no
     */
    Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException;

    default Mono<Optional<MessageUid>> getLastUidReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> getLastUid(mailbox))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Return the higest mod-sequence which were used for storing a MailboxMessage in the {@link Mailbox}
     */
    ModSeq getHighestModSeq(Mailbox mailbox) throws MailboxException;

    default Mono<ModSeq> getHighestModSeqReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> getHighestModSeq(mailbox))
            .subscribeOn(Schedulers.boundedElastic());
    }

    Flags getApplicableFlag(Mailbox mailbox) throws MailboxException;

    default Mono<Flags> getApplicableFlagReactive(Mailbox mailbox) {
        return Mono.fromCallable(() -> getApplicableFlag(mailbox))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Return a list containing all MessageUid of Messages that belongs to given {@link Mailbox}
     */
    Flux<MessageUid> listAllMessageUids(Mailbox mailbox);

    /**
     * Specify what data needs to get filled in a {@link MailboxMessage} before returning it
     * 
     *
     */
    enum FetchType {
        /**
         * Fetch only the meta data of the {@link MailboxMessage} which includes:
         * <p>
         *  {@link MailboxMessage#getUid()}
         *  {@link MailboxMessage#getModSeq()}
         *  {@link MailboxMessage#getBodyOctets()}
         *  {@link MailboxMessage#getFullContentOctets()}
         *  {@link MailboxMessage#getInternalDate()}
         *  {@link MailboxMessage#getSaveDate()}
         *  {@link MailboxMessage#getMailboxId()}
         *  {@link MailboxMessage#getMediaType()}
         *  {@link MailboxMessage#getModSeq()}
         *  {@link MailboxMessage#getSubType()}
         *  {@link MailboxMessage#getTextualLineCount()}
         * </p>
         */
        METADATA,
        /**
         * Fetch the {@link #METADATA}, {@link Property}'s and the {@link #HEADERS}'s for the {@link MailboxMessage}. This includes:
         * 
         * <p>
         * {@link MailboxMessage#getProperties()}
         * {@link MailboxMessage#getHeaderContent()}
         * </p>
         */
        HEADERS,
        /**
         * Fetch the {@link #HEADERS}, {@link Property}'s and the {@link #ATTACHMENTS_METADATA}'s for the {@link MailboxMessage}. This includes:
         *
         * <p>
         * {@link MailboxMessage#getAttachments()}
         * </p>
         */
        ATTACHMENTS_METADATA,
        /**
         * Fetch the complete {@link MailboxMessage}
         * 
         */
        FULL;
    }

}