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
package org.apache.james.mailbox.backup;

import static org.apache.james.mailbox.backup.MailboxMessageFixture.ANNOTATION_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.ANNOTATION_1_BIS;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.ANNOTATION_1_BIS_CONTENT;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.ANNOTATION_1_CONTENT;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.ANNOTATION_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.ANNOTATION_2_CONTENT;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_1_SUB_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MAILBOX_ID_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_CONTENT_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_CONTENT_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_ID_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_ID_2;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.MESSAGE_UID_1_VALUE;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.NO_ANNOTATION;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.SIZE_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.WITH_ANNOTATION_1;
import static org.apache.james.mailbox.backup.MailboxMessageFixture.WITH_ANNOTATION_1_AND_2;
import static org.apache.james.mailbox.backup.ZipAssert.EntryChecks.hasName;
import static org.apache.james.mailbox.backup.ZipAssert.assertThatZip;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Stream;

import org.apache.james.mailbox.backup.zip.FlagsExtraField;
import org.apache.james.mailbox.backup.zip.InternalDateExtraField;
import org.apache.james.mailbox.backup.zip.MailboxIdExtraField;
import org.apache.james.mailbox.backup.zip.MessageIdExtraField;
import org.apache.james.mailbox.backup.zip.SizeExtraField;
import org.apache.james.mailbox.backup.zip.UidExtraField;
import org.apache.james.mailbox.backup.zip.UidValidityExtraField;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MessageResultImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ZipperTest {
    private static final List<MailboxWithAnnotations> NO_MAILBOXES = ImmutableList.of();
    private static final MailboxWithAnnotations MAILBOX_1_WITHOUT_ANNOTATION = new MailboxWithAnnotations(MAILBOX_1, NO_ANNOTATION);
    private static final MailboxWithAnnotations MAILBOX_1_SUB_1_WITHOUT_ANNOTATION = new MailboxWithAnnotations(MAILBOX_1_SUB_1, NO_ANNOTATION);
    private static final MailboxWithAnnotations MAILBOX_2_WITHOUT_ANNOTATION = new MailboxWithAnnotations(MAILBOX_2, NO_ANNOTATION);

    private Zipper testee;
    private ByteArrayOutputStream output;

    private static MessageResult MESSAGE_RESULT_1;
    private static MessageResult MESSAGE_RESULT_2;

    @BeforeAll
    static void beforeAll() throws Exception {
        MESSAGE_RESULT_1 = new MessageResultImpl(MESSAGE_1);
        MESSAGE_RESULT_2 = new MessageResultImpl(MESSAGE_2);
    }

    @BeforeEach
    void beforeEach() {
        testee = new Zipper();
        output = new ByteArrayOutputStream();
    }

    @Test
    void archiveShouldWriteEmptyValidArchiveWhenNoMessage() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(), output);
        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.hasNoEntry();
        }
    }

    @Test
    void archiveShouldWriteOneMessageWhenOne() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(new MessageResultImpl(MESSAGE_1)), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID_1.serialize())
                        .hasStringContent(MESSAGE_CONTENT_1));
        }
    }

    @Test
    void archiveShouldWriteTwoMessagesWhenTwo() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(MESSAGE_RESULT_1, MESSAGE_RESULT_2), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID_1.serialize())
                        .hasStringContent(MESSAGE_CONTENT_1),
                    hasName(MESSAGE_ID_2.serialize())
                        .hasStringContent(MESSAGE_CONTENT_2));
        }
    }

    @Test
    void archiveShouldWriteMetadata() throws Exception {
        testee.archive(NO_MAILBOXES, Stream.of(MESSAGE_RESULT_1), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MESSAGE_ID_1.serialize())
                        .containsExtraFields(new SizeExtraField(SIZE_1))
                        .containsExtraFields(new UidExtraField(MESSAGE_UID_1_VALUE))
                        .containsExtraFields(new MessageIdExtraField(MESSAGE_ID_1.serialize()))
                        .containsExtraFields(new MailboxIdExtraField(MAILBOX_ID_1))
                        .containsExtraFields(new InternalDateExtraField(MESSAGE_1.getInternalDate()))
                        .containsExtraFields(new FlagsExtraField(MESSAGE_1.createFlags())));
        }
    }

    @Test
    void archiveShouldWriteOneMailboxWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_WITHOUT_ANNOTATION), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxesWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_WITHOUT_ANNOTATION, MAILBOX_2_WITHOUT_ANNOTATION), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_2.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxHierarchyWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_WITHOUT_ANNOTATION, MAILBOX_1_SUB_1_WITHOUT_ANNOTATION, MAILBOX_2_WITHOUT_ANNOTATION), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_1_SUB_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_2.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxHierarchyWhenMissingParent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_SUB_1_WITHOUT_ANNOTATION, MAILBOX_2_WITHOUT_ANNOTATION), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1_SUB_1.getName() + "/")
                        .isDirectory(),
                    hasName(MAILBOX_2.getName() + "/")
                        .isDirectory());
        }
    }

    @Test
    void archiveShouldWriteMailboxMetadataWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_WITHOUT_ANNOTATION), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                        .containsExtraFields(
                            new MailboxIdExtraField(MAILBOX_1.getMailboxId()),
                            new UidValidityExtraField(MAILBOX_1.getUidValidity().asLong())));
        }
    }

    @Test
    void archiveShouldWriteMailBoxWithoutAnAnnotationSubDirWhenEmpty() throws Exception {
        testee.archive(ImmutableList.of(MAILBOX_1_WITHOUT_ANNOTATION), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/")
                );
        }
    }

    @Test
    void archiveShouldWriteMailboxAnnotationsInASubDirWhenPresent() throws Exception {
        testee.archive(ImmutableList.of(new MailboxWithAnnotations(MAILBOX_1, WITH_ANNOTATION_1)), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/"),
                    hasName(MAILBOX_1.getName() + "/annotations/").isDirectory(),
                    hasName(MAILBOX_1.getName() + "/annotations/" + ANNOTATION_1.getKey().asString())
                );
        }
    }

    @Test
    void archiveShouldWriteMailboxAnnotationsInASubDirWhenTwoPresent() throws Exception {
        testee.archive(ImmutableList.of(new MailboxWithAnnotations(MAILBOX_1, WITH_ANNOTATION_1_AND_2)), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/"),
                    hasName(MAILBOX_1.getName() + "/annotations/").isDirectory(),
                    hasName(MAILBOX_1.getName() + "/annotations/" + ANNOTATION_1.getKey().asString())
                        .hasStringContent(ANNOTATION_1_CONTENT),
                    hasName(MAILBOX_1.getName() + "/annotations/" + ANNOTATION_2.getKey().asString())
                        .hasStringContent(ANNOTATION_2_CONTENT)
                );
        }
    }

    @Test
    void archiveShouldWriteMailboxAnnotationsInASubDirWhenTwoPresentWithTheSameName() throws Exception {
        testee.archive(ImmutableList.of(new MailboxWithAnnotations(MAILBOX_1, ImmutableList.of(ANNOTATION_1, ANNOTATION_1_BIS))), Stream.of(), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsOnlyEntriesMatching(
                    hasName(MAILBOX_1.getName() + "/"),
                    hasName(MAILBOX_1.getName() + "/annotations/").isDirectory(),
                    hasName(MAILBOX_1.getName() + "/annotations/" + ANNOTATION_1.getKey().asString())
                        .hasStringContent(ANNOTATION_1_CONTENT),
                    hasName(MAILBOX_1.getName() + "/annotations/" + ANNOTATION_1.getKey().asString())
                        .hasStringContent(ANNOTATION_1_BIS_CONTENT)
                );
        }
    }

    @Test
    void entriesInArchiveShouldBeOrderedLikeMailboxWithItsAnnotationsThenMessages() throws Exception {
        MailboxWithAnnotations mailbox1 = new MailboxWithAnnotations(MAILBOX_1, ImmutableList.of(ANNOTATION_1));
        MailboxWithAnnotations mailbox2 = new MailboxWithAnnotations(MAILBOX_2, ImmutableList.of(ANNOTATION_2));

        testee.archive(ImmutableList.of(mailbox1, mailbox2), Stream.of(MESSAGE_RESULT_1, MESSAGE_RESULT_2), output);

        try (ZipAssert zipAssert = assertThatZip(output)) {
            zipAssert.containsExactlyEntriesMatching(
                //MAILBOX 1 with its annotations
                hasName(MAILBOX_1.getName() + "/"),
                hasName(MAILBOX_1.getName() + "/annotations/").isDirectory(),
                hasName(MAILBOX_1.getName() + "/annotations/" + ANNOTATION_1.getKey().asString())
                    .hasStringContent(ANNOTATION_1_CONTENT),
                //MAILBOX 2 with its annotations
                hasName(MAILBOX_2.getName() + "/"),
                hasName(MAILBOX_2.getName() + "/annotations/").isDirectory(),
                hasName(MAILBOX_2.getName() + "/annotations/" + ANNOTATION_2.getKey().asString())
                    .hasStringContent(ANNOTATION_2_CONTENT),
                //the messages
                hasName(MESSAGE_ID_1.serialize())
                    .hasStringContent(MESSAGE_CONTENT_1),
                hasName(MESSAGE_ID_2.serialize())
                    .hasStringContent(MESSAGE_CONTENT_2));

        }
    }
}
