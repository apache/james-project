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
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.transaction.Mapper;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public interface AnnotationMapper extends Mapper {
    /**
     * Return all <code>MailboxAnnotation</code> of specific mailbox.
     * The result is not ordered and should not contain duplicate by key
     *
     * @param mailboxId the selected mailbox
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAllAnnotations(MailboxId mailboxId);

    default Publisher<MailboxAnnotation> getAllAnnotationsReactive(MailboxId mailboxId) {
        return Flux.fromIterable(getAllAnnotations(mailboxId))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Search all the <code>MailboxAnnotation</code> of selected mailbox by the set of annotation's keys. The result is not ordered and should not
     * contain duplicate by key
     *
     * @param mailboxId the selected mailbox
     * @param keys the set of the key should be filtered
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAnnotationsByKeys(MailboxId mailboxId, Set<MailboxAnnotationKey> keys);

    default Publisher<MailboxAnnotation> getAnnotationsByKeysReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(getAnnotationsByKeys(mailboxId, keys))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Search all the <code>MailboxAnnotation</code> of selected mailbox by the set of annotation's keys as well as its children entries
     * The result is not ordered and should not contain duplicate by key
     *
     * @param mailboxId the selected mailbox
     * @param keys the set of the key should be filtered
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys);

    default Publisher<MailboxAnnotation> getAnnotationsByKeysWithOneDepthReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(getAnnotationsByKeysWithOneDepth(mailboxId, keys))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Search all the <code>MailboxAnnotation</code> of selected mailbox by the set of annotation's keys and entries below the keys
     * The result is not ordered and should not contain duplicate by key
     *
     * @param mailboxId the selected mailbox
     * @param keys the set of the key should be filtered
     * @return List<MailboxAnnotation>
     */
    List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys);

    default Publisher<MailboxAnnotation> getAnnotationsByKeysWithAllDepthReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(getAnnotationsByKeysWithAllDepth(mailboxId, keys))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Delete the annotation of selected mailbox by its key.
     *
     * @param mailboxId the selected mailbox
     * @param key the key of annotation should be deleted
     */
    void deleteAnnotation(MailboxId mailboxId, MailboxAnnotationKey key);

    default Publisher<Void> deleteAnnotationReactive(MailboxId mailboxId, MailboxAnnotationKey key) {
        return Mono.fromRunnable(() -> deleteAnnotation(mailboxId, key))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    /**
     * - Insert new annotation if it does not exist on store
     * - Update the new value for existed annotation
     *
     * @param mailboxId the selected mailbox
     * @param mailboxAnnotation the selected annotation
     */
    void insertAnnotation(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation);

    default Publisher<Void> insertAnnotationReactive(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return Mono.fromRunnable(() -> insertAnnotation(mailboxId, mailboxAnnotation))
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    /**
     * Checking the current annotation of selected mailbox exists on store or not. It's checked by annotation key, not by its value.
     *
     * @param mailboxId the selected mailbox
     * @param mailboxAnnotation current annotation should be checked
     * @return boolean should be 'true' if it already existed on store
     */
    boolean exist(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation);

    default Publisher<Boolean> existReactive(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return Mono.fromCallable(() -> exist(mailboxId, mailboxAnnotation))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Getting total number of current annotation on mailbox
     *
     */
    int countAnnotations(MailboxId mailboxId);

    default Publisher<Integer> countAnnotationsReactive(MailboxId mailboxId) {
        return Mono.fromCallable(() -> countAnnotations(mailboxId))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
