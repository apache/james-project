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

package org.apache.james.mailbox.cassandra.object.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobIdUpdater;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.mailbox.cassandra.object.compaction.CassandraBlobReferenceDAO.Reference;
import org.apache.james.mailbox.cassandra.object.compaction.CassandraBlobReferenceDAO.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class CassandraBlobIdUpdaterFactoryTest {
    private static final BlobId OLD = new PlainBlobId.Factory().of("old");
    private static final BlobId NEW = new PlainBlobId.Factory().of("new");
    private static final Reference REFERENCE = new Reference(OLD, Type.HEADER, UUID.randomUUID());

    private CassandraBlobReferenceDAO references;
    private CassandraBlobReferenceIndexDAO index;

    private CassandraBlobIdUpdater.Factory testee;

    @BeforeEach
    void setUp() {
        references = mock(CassandraBlobReferenceDAO.class);
        index = mock(CassandraBlobReferenceIndexDAO.class);
        testee = new CassandraBlobIdUpdater.Factory(references, index);
        when(index.store(any(), any())).thenReturn(Mono.empty());
        when(index.cleanup(any())).thenReturn(Mono.empty());
    }

    @Test
    void shouldPropagateSourceFailureAndCleanRun() {
        IllegalStateException failure = new IllegalStateException("scan failed");
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE).concatWith(Mono.error(failure)));

        assertThatThrownBy(() -> testee.forPredicate(id -> true, id -> { }).block()).isSameAs(failure);

        verify(index).store(any(), eq(REFERENCE));
        verify(index).cleanup(any());
        verify(references, never()).replace(any(), any());
    }

    @Test
    void shouldPropagateIndexFailureAndCleanRun() {
        IllegalStateException failure = new IllegalStateException("index failed");
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE));
        when(index.store(any(), any())).thenReturn(Mono.error(failure));

        assertThatThrownBy(() -> testee.forPredicate(id -> true, id -> { }).block()).isSameAs(failure);

        verify(index).cleanup(any());
    }

    @Test
    void shouldCleanRunWhenPredicateFails() {
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE));

        assertThatThrownBy(() -> testee.forPredicate(id -> {
            throw new IllegalArgumentException("predicate failed");
        }, id -> { }).block()).hasMessage("predicate failed");

        verify(index).cleanup(any());
        verify(index, never()).store(any(), any());
    }

    @Test
    void shouldCleanRunWhenPreparationIsCancelled() {
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE).concatWith(Flux.never()));

        Disposable subscription = testee.forPredicate(id -> true, id -> { }).subscribe();
        verify(index).store(any(), eq(REFERENCE));
        subscription.dispose();

        verify(index).cleanup(any());
    }

    @Test
    void eachSubscriptionShouldPrepareAnIsolatedRun() {
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE));
        Mono<BlobIdUpdater> preparation = testee.forPredicate(id -> true, id -> { });

        BlobIdUpdater first = preparation.block();
        BlobIdUpdater second = preparation.block();

        ArgumentCaptor<RunId> runIds = ArgumentCaptor.forClass(RunId.class);
        verify(index, times(2)).store(runIds.capture(), eq(REFERENCE));
        assertThat(runIds.getAllValues().get(0)).isNotEqualTo(runIds.getAllValues().get(1));
        verify(index, never()).cleanup(any());
        first.cleanup().block();
        second.cleanup().block();
    }

    @Test
    void cleanupFailureShouldBeRetryableAndKeepUpdaterClosed() {
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE));
        BlobIdUpdater updater = testee.forPredicate(id -> true, id -> { }).block();
        when(index.cleanup(any())).thenReturn(Mono.error(new IllegalStateException("cleanup failed")), Mono.empty());

        assertThatThrownBy(() -> updater.cleanup().block()).hasMessage("cleanup failed");
        assertThatThrownBy(() -> updater.replaceReferences(OLD, NEW).block()).isInstanceOf(IllegalStateException.class);
        updater.cleanup().block();

        verify(index, times(2)).cleanup(any());
        verify(index, never()).retrieve(any(), any());
    }

    @Test
    void successfulReplacementShouldKeepSnapshotUntilExplicitCleanup() {
        when(references.listReferences()).thenReturn(Flux.just(REFERENCE));
        when(index.retrieve(any(), eq(OLD))).thenReturn(Flux.just(REFERENCE));
        when(references.replace(REFERENCE, NEW)).thenReturn(Mono.empty());
        BlobIdUpdater updater = testee.forPredicate(id -> true, id -> { }).block();

        updater.replaceReferences(OLD, NEW).block();

        verify(index, never()).cleanup(any());
        updater.cleanup().block();
        verify(index).cleanup(any());
    }
}
