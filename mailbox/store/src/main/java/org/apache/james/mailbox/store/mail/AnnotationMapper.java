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

import java.util.List;
import java.util.Set;

import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.store.transaction.Mapper;

public interface AnnotationMapper extends Mapper {

    /**
     * Return all <code>MailboxAnnotation</code>.
     * The result is not ordered and should not contain duplicate by key
     * 
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAllAnnotations();

    /**
     * Search all the <code>MailboxAnnotation</code> by the set of annotation's keys. The result is not ordered and should not
     * contain duplicate by key
     * 
     * @param keys the set of the key should be filtered
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAnnotationsByKeys(Set<MailboxAnnotationKey> keys);

    /**
     * Search all the <code>MailboxAnnotation</code> by the set of annotation's keys as well as its children entries 
     * The result is not ordered and should not contain duplicate by key
     * 
     * @param keys the set of the key should be filtered
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(Set<MailboxAnnotationKey> keys);

    /**
     * Search all the <code>MailboxAnnotation</code> by the set of annotation's keys and entries below the keys 
     * The result is not ordered and should not contain duplicate by key
     * 
     * @param keys the set of the key should be filtered
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(Set<MailboxAnnotationKey> keys);

    /**
     * Delete the annotation by its key.
     * 
     * @param key the key of annotation should be deleted
     */
    void deleteAnnotation(MailboxAnnotationKey key);

    /**
     * - Insert new annotation if it does not exist on store
     * - Update the new value for existed annotation
     * 
     * @param mailboxAnnotation the selected annotation
     */
    void insertAnnotation(MailboxAnnotation mailboxAnnotation);

}
