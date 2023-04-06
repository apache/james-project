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

import java.util.List;
import java.util.Set;

import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxPath;
import org.reactivestreams.Publisher;

/**
 * <p>
 * This class intends to manage mailbox annotations.
 *
 * Work will be delegated to it by {@link MailboxManager}
 * </p>
 */

public interface MailboxAnnotationManager {

    /**
     * Return all mailbox's annotation as the {@link List} of {@link MailboxAnnotation} without order and
     * do not contain any two annotations with the same key
     * 
     * @param mailboxPath   the current mailbox
     * @param session       the current session
     * @return              List<MailboxAnnotation>
     * @throws MailboxException in case of selected mailbox does not exist
     */
    List<MailboxAnnotation> getAllAnnotations(MailboxPath mailboxPath, MailboxSession session) throws MailboxException;

    Publisher<MailboxAnnotation> getAllAnnotationsReactive(MailboxPath mailboxPath, MailboxSession session);

    /**
     * Return all mailbox's annotation filter by the list of the keys without order and
     * do not contain any two annotations with the same key
     * 
     * @param mailboxPath   the current mailbox
     * @param session       the current session
     * @param keys          list of the keys should be filter
     * @return              List<MailboxAnnotation>
     * @throws MailboxException in case of selected mailbox does not exist
     */
    List<MailboxAnnotation> getAnnotationsByKeys(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) throws MailboxException;

    Publisher<MailboxAnnotation> getAnnotationsByKeysReactive(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys);

    /**
     * Return all mailbox's annotation by the list of the keys and its children entries without order and
     * do not contain any two annotations with the same key
     *
     * @param mailboxPath   the current mailbox
     * @param session       the current session
     * @param keys          list of the keys should be filter
     * @return              List<MailboxAnnotation>
     * @throws MailboxException in case of selected mailbox does not exist
     */
    List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) throws MailboxException;

    Publisher<MailboxAnnotation> getAnnotationsByKeysWithOneDepthReactive(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys);

    /**
     * Return all mailbox's annotation by the list of the keys and its below entries without order and
     * do not contain any two annotations with the same key
     *
     * @param mailboxPath   the current mailbox
     * @param session       the current session
     * @param keys          list of the keys should be filter
     * @return              List<MailboxAnnotation>
     * @throws MailboxException in case of selected mailbox does not exist
     */
    List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys) throws MailboxException;

    Publisher<MailboxAnnotation> getAnnotationsByKeysWithAllDepthReactive(MailboxPath mailboxPath, MailboxSession session, Set<MailboxAnnotationKey> keys);

    /**
     * Update the mailbox's annotations. This method can:
     * - Insert new annotation if it does not exist
     * - Update the new value for existed annotation
     * - Delete the existed annotation if its value is nil
     * 
     * @param mailboxPath   the current mailbox
     * @param session       the current session
     * @param mailboxAnnotations    the list of annotation should be insert/udpate/delete
     * @throws MailboxException in case of selected mailbox does not exist
     */
    void updateAnnotations(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations) throws MailboxException, AnnotationException;

    Publisher<Void> updateAnnotationsReactive(MailboxPath mailboxPath, MailboxSession session, List<MailboxAnnotation> mailboxAnnotations);
}
