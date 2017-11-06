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
import java.util.Optional;

import org.apache.james.mailbox.exception.BadCredentialsException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxExistsException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.search.MailboxQuery;

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

public interface MailboxManager extends RequestAware, MailboxListenerSupport, RightManager, MailboxAnnotationManager {

    enum MailboxCapabilities {
        Annotation,
        Move,
        Namespace,
        UserFlag,
        ACL
    }

    EnumSet<MailboxCapabilities> getSupportedMailboxCapabilities();

    boolean hasCapability(MailboxCapabilities capability);

    enum MessageCapabilities {
        Attachment,
        UniqueID
    }

    EnumSet<MessageCapabilities> getSupportedMessageCapabilities();

    enum SearchCapabilities {
        MultimailboxSearch,
        /**
         *  The implementation supporting this capability should
         *  provide an index on the fields: 
         *  From, To, Cc, Bcc, Subjects, textBody & htmlBody
         */
        Text,
        FullText,
        Attachment
    }
    
    EnumSet<SearchCapabilities> getSupportedSearchCapabilities();

    
    /**
     * Return the delimiter to use for folders
     * 
     * @return delimiter
     */
    char getDelimiter();

    /**
     * Gets an object managing the given mailbox.
     * 
     * @param mailboxPath
     *            the Path of the mailbox, not null
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     *             when the mailbox cannot be opened
     * @throws MailboxNotFoundException
     *             when the given mailbox does not exist
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
     *             when the mailbox cannot be opened
     * @throws MailboxNotFoundException
     *             when the given mailbox does not exist
     */
    MessageManager getMailbox(MailboxId mailboxId, MailboxSession session) throws MailboxException;

    /**
     * Creates a new mailbox. Any intermediary mailboxes missing from the
     * hierarchy should be created.
     * 
     * @param mailboxPath
     * @param mailboxSession
     *            the context for this call, not null
     * @throws MailboxException
     *             when creation fails
     * @return Empty optional when the mailbox name is empty. If mailbox is created, the id of the mailboxPath specified as
     *  parameter is returned (and potential mailboxIds of parent mailboxes created in the process will be omitted)
     */
    Optional<MailboxId> createMailbox(MailboxPath mailboxPath, MailboxSession mailboxSession) throws MailboxException;

    /**
     * Delete the mailbox with the name
     * 
     * @param mailboxPath
     * @param session
     * @throws MailboxException
     */
    void deleteMailbox(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    /**
     * Renames a mailbox.
     * 
     * @param from
     *            original mailbox
     * @param to
     *            new mailbox
     * @param session
     *            the context for this call, not nul
     * @throws MailboxException
     *             otherwise
     * @throws MailboxExistsException
     *             when the <code>to</code> mailbox exists
     * @throws MailboxNotFound
     *             when the <code>from</code> mailbox does not exist
     */
    void renameMailbox(MailboxPath from, MailboxPath to, MailboxSession session) throws MailboxException;

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

    /**
     * Searches for mailboxes matching the given query.
     * 
     * @param expression
     *            not null
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     */
    List<MailboxMetaData> search(MailboxQuery expression, MailboxSession session) throws MailboxException;

    /**
     * Searches for messages matching the given query.
     * 
     * @param expression
     *            not null
     * @param session
     *            the context for this call, not null
     * @throws MailboxException
     */
    List<MessageId> search(MultimailboxesSearchQuery expression, MailboxSession session, long limit) throws MailboxException;

    /**
     * Does the given mailbox exist?
     * 
     * @param mailboxPath
     *            not null
     * @param session
     *            the context for this call, not null
     * @return true when the mailbox exists and is accessible for the given
     *         user, false otherwise
     * @throws MailboxException
     */
    boolean mailboxExists(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    /**
     * Creates a new system session.<br>
     * A system session is intended to be used for programmatic access.<br>
     * Use {@link #login(String, String)} when accessing this API from a
     * protocol.
     * 
     * @param userName
     *            the name of the user whose session is being created
     * @return <code>MailboxSession</code>, not null
     * @throws BadCredentialsException
     *             when system access is not allowed for the given user
     * @throws MailboxException
     *             when the creation fails for other reasons
     */
    MailboxSession createSystemSession(String userName) throws BadCredentialsException, MailboxException;

    /**
     * Autenticates the given user against the given password.<br>
     * When authenticated and authorized, a session will be supplied
     * 
     * @param userid
     *            user name
     * @param passwd
     *            password supplied
     * @return a <code>MailboxSession</code> when the user is authenticated and
     *         authorized to access
     * @throws BadCredentialsException
     *             when system access is denied for the given user
     * @throws MailboxException
     *             when the creation fails for other reasons
     */
    MailboxSession login(String userid, String passwd) throws BadCredentialsException, MailboxException;

    /**
     * Autenticates the given administrator against the given password,
     * then switch to an other user<br>
     * When authenticated and authorized, a session for the other user will be supplied
     * 
     * @param adminUserId
     *            user name of the admin user, matching the credentials
     * @param passwd
     *            password supplied for the admin user
     * @param otherUserId
     *            user name of the real user
     * @return a <code>MailboxSession</code> for the real user
     *         when the admin is authenticated and authorized to access
     * @throws BadCredentialsException
     *             when system access is denied for the given user
     * @throws MailboxException
     *             when the creation fails for other reasons
     */
    MailboxSession loginAsOtherUser(String adminUserId, String passwd, String otherUserId) throws BadCredentialsException, MailboxException;

    /**
     * <p>
     * Logs the session out, freeing any resources. Clients who open session
     * should make best efforts to call this when the session is closed.
     * </p>
     * <p>
     * Note that clients may not always be able to call logout (whether forced
     * or not). Mailboxes that create sessions which are expensive to maintain
     * <code>MUST</code> retain a reference and periodically check
     * {@link MailboxSession#isOpen()}.
     * </p>
     * <p>
     * Note that implementations much be aware that it is possible that this
     * method may be called more than once with the same session.
     * </p>
     * 
     * @param session
     *            not null
     * @param force
     *            true when the session logout is forced by premature connection
     *            termination
     * @throws MailboxException
     *             when logout fails
     */
    void logout(MailboxSession session, boolean force) throws MailboxException;

    /**
     * Return a unmodifiable {@link List} of {@link MailboxPath} objects
     * 
     * @param session
     * @return pathList
     * @throws MailboxException
     */
    List<MailboxPath> list(MailboxSession session) throws MailboxException;

    boolean hasChildren(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;
}
