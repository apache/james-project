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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import org.junit.rules.ExpectedException;

public class AttachmentLoaderTest {

    private AttachmentMapper attachmentMapper;
    private AttachmentLoader testee;

    @Before
    public void setup() {
        attachmentMapper = mock(AttachmentMapper.class);
        testee = new AttachmentLoader(attachmentMapper);
    }

    @Test
    public void attachmentsByIdShouldReturnMapWhenExist() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId, attachmentId2);

        Attachment attachment = Attachment.builder()
                .attachmentId(attachmentId)
                .bytes("attachment".getBytes())
                .type("type")
                .build();
        Attachment attachment2 = Attachment.builder()
                .attachmentId(attachmentId2)
                .bytes("attachment2".getBytes())
                .type("type")
                .build();
        when(attachmentMapper.getAttachments(attachmentIds))
            .thenReturn(ImmutableList.of(attachment, attachment2));

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds);

        assertThat(attachmentsById).hasSize(2)
                .containsOnly(MapEntry.entry(attachmentId, attachment), MapEntry.entry(attachmentId2, attachment2));
    }

    @Test
    public void attachmentsByIdShouldReturnEmptyMapWhenAttachmentsDontExists() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        Set<AttachmentId> attachmentIds = ImmutableSet.of(attachmentId, attachmentId2);

        Attachment attachment = Attachment.builder()
                .attachmentId(attachmentId)
                .bytes("attachment".getBytes())
                .type("type")
                .build();
        Attachment attachment2 = Attachment.builder()
                .attachmentId(attachmentId2)
                .bytes("attachment2".getBytes())
                .type("type")
                .build();
        when(attachmentMapper.getAttachments(attachmentIds))
                .thenReturn(ImmutableList.of());

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds);

        assertThat(attachmentsById).hasSize(0);
    }

}
