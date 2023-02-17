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

package org.apache.james.mailbox;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * <p>
 * Central MailboxManager which creates, lists, provides, renames and deletes
 * Mailboxes
 * </p>
 * <p>
 * An important goal is to be JavaMail feature compatible. That means JavaMail
 * could be used in both directions: As a backend for e.g. accessing a Maildir
 * JavaMail store or as a frontend to access a JDBC MailboxManager through
 * JavaMail. This should be possible by not too complicated wrapper classes. Due
 * to the complexity of JavaMail it might be impossible to avoid some
 * limitations.
 * </p>
 * <p>
 * Internally MailboxManager deals with named repositories that could have
 * different implementations. E.g. JDBC connections to different hosts or
 * Maildir / Mbox like stores. These repositories are identified by their names
 * and maybe are configured in config.xml. The names of the mailboxes have to be
 * mapped to the corresponding repository name. For user mailboxes this could be
 * done by a "User.getRepositoryName()" property. It is imaginable that
 * repositories lookup further properties from the user object like a path name
 * for a file based storage method. Until Milestone 6 there is only one named
 * repository: "default".
 * </p>
 * <p>
 * The only operation that requires dealing with the named repositories directly
 * is the quota management. It is probably really difficult to implement a quota
 * system that spans multiple repository implementations. That is why quotas are
 * created for a specific repository. To be able to administer, repositories and
 * theier belonging mailboxes can be listet.
 * </p>
 */

public interface MailboxManager extends RequestAware, RightManager, MailboxAnnotationManager, SessionProvider {

    int MAX_MAILBOX_NAME_LENGTH = 200;

    enum MailboxCapabilities {
        Annotation,
        Move,
        Namespace,
        UserFlag,
        ACL,
        Quota
    }

    EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities();

    boolean hasCapability(MailboxCapabilities capability);

    enum MessageCapabilities {
        UniqueID
    }

    EnumSet<MessageCapabilities> getSupportedMessageCapabilities();

    enum SearchCapabilities {
        MultimailboxSearch,
        PartialEmailMatch,
        /**
         *  The implementation supporting this capability should
         *  provide an index on the fields: 
         *  From, To, Cc, Bcc, Subjects, textBody & htmlBody
         */
        Text,
        FullText,
        Attachment,
        AttachmentFileName
    }
    
    EnumSet<SearchCapabilities> getSupportedSearchCapabilities();

    /**
     * Gets an object managing the given mailbox.
     * 
     * @param mailboxPath
     *            the Path of the mailbox, not null
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     *            when the mailbox cannot be opened
     * @throws MailboxNotFoundException
     *            when the given mailbox does not exist
     */
    MessageManager getMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    /**
     * Gets an object managing the given mailbox.
     * 
     * @param mailboxId
     *            the Id of the mailbox, not null
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     *            when the mailbox cannot be opened
     * @throws MailboxNotFoundException
     *            when the given mailbox does not exist
     */
    MessageManager getMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException;

    Publisher<MessageManager> getMailboxReactive(MailboxId mailboxId, MailboxSession session);

    Publisher<MessageManager> getMailboxReactive(MailboxPath mailboxPath, MailboxSession session);

    MessageManager getMailbox(Mailbox mailbox, MailboxSession session) throws MailboxException;


    enum CreateOption {
        NONE, CREATE_SUBSCRIPTION
    }

    /**
     * Creates a new mailbox. Any intermediary mailboxes missing from the
     * hierarchy should be created.
     *
     * @param mailboxSession
     *            the context for this call, not null
     * @throws MailboxException
     *            when creation fails
     * @return Empty optional when the mailbox name is empty. If mailbox is created, the id of the mailboxPath specified as
     *  parameter is returned (and potential mailboxIds of parent mailboxes created in the process will be omitted)
     */
    default Optional<MailboxId> createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException {
        return createMailbox(mailboxPath, CreateOption.NONE, mailboxSession);
    }

    default Publisher<MailboxId> createMailboxReactive(MailboxPath mailboxPath, MailboxSession mailboxSession) {
        return createMailboxReactive(mailboxPath, CreateOption.NONE, mailboxSession);
    }

    Optional<MailboxId> createMailbox(MailboxPath mailboxPath, CreateOption createOption, MailboxSession mailboxSession) throws MailboxException;

    default Publisher<MailboxId> createMailboxReactive(MailboxPath mailboxPath, CreateOption createOption, MailboxSession mailboxSession) {
        return Mono.fromCallable(() -> createMailbox(mailboxPath, createOption, mailboxSession))
            .handle((optional, sink) -> optional.ifPresent(sink::next));
    }

    /**
     * Delete the mailbox with the name
     */
    void deleteMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    default Mono<Void> deleteMailboxReactive(MailboxPath mailboxPath, MailboxSession session) {
        return Mono.fromRunnable(Throwing.runnable(() -> deleteMailbox(mailboxPath, session)));
    }

    /**
     * Delete the mailbox with the given id
     *
     * @return the Mailbox when deleted
     */
    Mailbox deleteMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException;

    default Mono<Mailbox> deleteMailboxReactive(MailboxId mailboxId, MailboxSession session) {
        return Mono.fromCallable(() -> deleteMailbox(mailboxId, session));
    }

    class MailboxRenamedResult {
        private final MailboxId mailboxId;
        private final MailboxPath originPath;
        private final MailboxPath destinationPath;

        public MailboxRenamedResult(MailboxId mailboxId, MailboxPath originPath, MailboxPath destinationPath) {
            this.mailboxId = mailboxId;
            this.originPath = originPath;
            this.destinationPath = destinationPath;
        }

        public MailboxId getMailboxId() {
            return mailboxId;
        }

        public MailboxPath getOriginPath() {
            return originPath;
        }

        public MailboxPath getDestinationPath() {
            return destinationPath;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof MailboxRenamedResult) {
                MailboxRenamedResult that = (MailboxRenamedResult) o;

                return Objects.equals(this.mailboxId, that.mailboxId)
                    && Objects.equals(this.originPath, that.originPath)
                    && Objects.equals(this.destinationPath, that.destinationPath);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(mailboxId, originPath, destinationPath);
        }
    }

    enum RenameOption {
        NONE, RENAME_SUBSCRIPTIONS
    }

    /**
     * Renames a mailbox.
     * 
     * @param from
     *            original mailbox path
     * @param to
     *            new mailbox path
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     *            upon unexpected failure
     * @throws MailboxExistsException
     *            when the <code>to</code> mailbox exists
     * @throws MailboxNotFoundException
     *            when the <code>from</code> mailbox does not exist
     */
    List<MailboxRenamedResult> renameMailbox(MailboxPath from, MailboxPath to, RenameOption option, MailboxSession session) throws MailboxException;

    default List<MailboxRenamedResult> renameMailbox(MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException {
        return renameMailbox(from, to, RenameOption.NONE, session);
    }

    default Publisher<List<MailboxRenamedResult>> renameMailboxReactive(MailboxPath from, MailboxPath to, RenameOption option, MailboxSession session) {
        return Mono.fromCallable(() -> renameMailbox(from, to, option, session));
    }

    /**
     * Renames a mailbox.
     *
     * @param mailboxId
     *            original mailbox
     * @param newMailboxPath
     *            new mailbox path
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     *            upon unexpected failure
     * @throws MailboxExistsException
     *            when the <code>newMailboxPath</code> mailbox exists
     * @throws MailboxNotFoundException
     *            when the <code>mailboxId</code> original mailbox does not exist
     */
    List<MailboxRenamedResult> renameMailbox(MailboxId mailboxId, MailboxPath newMailboxPath, RenameOption option, MailboxSession session) throws MailboxException;

    default List<MailboxRenamedResult> renameMailbox(MailboxId mailboxId, MailboxPath newMailboxPath, MailboxSession session) throws MailboxException {
        return renameMailbox(mailboxId, newMailboxPath, RenameOption.NONE, session);
    }

    default Publisher<List<MailboxRenamedResult>> renameMailboxReactive(MailboxId mailboxId, MailboxPath to, RenameOption option, MailboxSession session) {
        return Mono.fromCallable(() -> renameMailbox(mailboxId, to, option, session));
    }

    /**
     * Copy the given {@link MessageRange} from one Mailbox to the other. 
     * 
     * Be aware that the copied Messages MUST get the \RECENT flag set!
     * 
     * @param set
     *            messages to copy
     * @param from
     *            name of the source mailbox
     * @param to
     *            name of the destination mailbox
     * @param session
     *            <code>MailboxSession</code>, not null
     * @return a list of MessageRange - uids assigned to copied messages
     */
    List<MessageRange> copyMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException;

    List<MessageRange> copyMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException;

    default Publisher<MessageRange> copyMessagesReactive(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) {
        return Mono.fromCallable(() -> copyMessages(set, from, to, session))
            .flatMapIterable(Function.identity());
    }

    default Publisher<MessageRange> copyMessagesReactive(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) {
        return Mono.fromCallable(() -> copyMessages(set, from, to, session))
            .flatMapIterable(Function.identity());
    }
    
    /**
     * Move the given {@link MessageRange} from one Mailbox to the other. 
     * 
     * Be aware that the moved Messages MUST get the \RECENT flag set!
     * 
     * @param set
     *            messages to move
     * @param from
     *            name of the source mailbox
     * @param to
     *            name of the destination mailbox
     * @param session
     *            <code>MailboxSession</code>, not null
     * @return a list of MessageRange - uids assigned to moved messages
     */
    List<MessageRange> moveMessages(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException;

    List<MessageRange> moveMessages(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) throws MailboxException;

    default Publisher<MessageRange> moveMessagesReactive(MessageRange set, MailboxPath from, MailboxPath to, MailboxSession session) {
        return Mono.fromCallable(() -> moveMessages(set, from, to, session))
            .flatMapIterable(Function.identity());
    }

    default Publisher<MessageRange> moveMessagesReactive(MessageRange set, MailboxId from, MailboxId to, MailboxSession session) {
        return Mono.fromCallable(() -> moveMessages(set, from, to, session))
            .flatMapIterable(Function.identity());
    }

    enum MailboxSearchFetchType {
        Minimal,
        Counters
    }

    /**
     * Searches for mailboxes matching the given query.
     * 
     * @param expression
     *            not null
     * @param session
     *            the context for this call, not null
     */
    default Flux<MailboxMetaData> search(MailboxQuery expression, MailboxSession session) {
        return search(expression, MailboxSearchFetchType.Counters, session);
    }

    Flux<MailboxMetaData> search(MailboxQuery expression, MailboxSearchFetchType fetchType, MailboxSession session);

    /**
     * Searches for messages matching the given query.
     * 
     * @param expression
     *            not null
     * @param session
     *            the context for this call, not null
     */
    Publisher<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit);

    /**
     * Returns the list of MessageId of messages belonging to that Thread
     *
     * @param threadId
     *          target Thread
     * @param session
     *          the context for this call, not null
     * @return  the list of MessageId of messages belonging to that Thread
     */
    Publisher<MessageId> getThread(ThreadId threadId, MailboxSession session);

    /**
     * Does the given mailbox exist?
     * 
     * @param mailboxPath
     *            not null
     * @param session
     *            the context for this call, not null
     * @return A publisher holding true when the mailbox exists and is accessible for the given
     *            user, false otherwise
     */
    Publisher<Boolean> mailboxExists(MailboxPath mailboxPath, MailboxSession session);

    /**
     * Does the user INBOX exist?
     *
     * @param session
     *            the context for this call, not null
     * @return true when the INBOX exists and is accessible for the given
     *            user, false otherwise
     */
    default Publisher<Boolean> hasInbox(MailboxSession session) {
        return mailboxExists(MailboxPath.inbox(session), session);
    }

    /**
     * Return a unmodifiable {@link List} of {@link MailboxPath} objects
     */
    List<MailboxPath> list(MailboxSession session) throws MailboxException;

    boolean hasChildren(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    Publisher<Boolean> hasChildrenReactive(MailboxPath mailboxPath, MailboxSession session);

    default <T> Mono<T> manageProcessing(Mono<T> toBeWrapped, MailboxSession mailboxSession) {
        return Mono.<T, Runnable>using(
            () -> {
                startProcessingRequest(mailboxSession);
                return () -> endProcessingRequest(mailboxSession);
            },
            c -> toBeWrapped,
            Runnable::run);
    }

    MessageId.Factory getMessageIdFactory();

}
