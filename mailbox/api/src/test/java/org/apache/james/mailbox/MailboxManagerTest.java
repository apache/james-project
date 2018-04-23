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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxManager.MailboxCapabilities;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.exception.AnnotationException;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxManager;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxMetaData;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.Quota;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.mailbox.util.EventCollector;
import org.apache.james.mime4j.dom.Message;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Test the {@link MailboxManager} methods that
 * are not covered by the protocol-tester suite.
 * 
 * This class needs to be extended by the different mailbox 
 * implementations which are responsible to setup and 
 * implement the test methods.
 * 
 */
public abstract class MailboxManagerTest {

    public static final String USER_1 = "USER_1";
    public static final String USER_2 = "USER_2";
    private static final int DEFAULT_MAXIMUM_LIMIT = 256;

    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey PRIVATE_CHILD_KEY = new MailboxAnnotationKey("/private/comment/user");
    private static final MailboxAnnotationKey PRIVATE_GRANDCHILD_KEY = new MailboxAnnotationKey("/private/comment/user/name");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_CHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_CHILD_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_GRANDCHILD_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_GRANDCHILD_KEY, "My private comment");
    private static final MailboxAnnotation PRIVATE_ANNOTATION_UPDATE = MailboxAnnotation.newInstance(PRIVATE_KEY, "My updated private comment");
    private static final MailboxAnnotation SHARED_ANNOTATION =  MailboxAnnotation.newInstance(SHARED_KEY, "My shared comment");

    private static final List<MailboxAnnotation> ANNOTATIONS = ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION);

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private MailboxManager mailboxManager;
    private MailboxSession session;
    private Message.Builder message;

    protected abstract MailboxManager provideMailboxManager() throws MailboxException;

    public void setUp() throws Exception {
        this.mailboxManager = new MockMailboxManager(provideMailboxManager()).getMockMailboxManager();

        this.message = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8);
    }

    public void tearDown() throws Exception {
        mailboxManager.logout(session, false);
        mailboxManager.endProcessingRequest(session);
    }
    
    @Test
    public void createUser1SystemSessionShouldReturnValidSession() throws UnsupportedEncodingException, MailboxException {
        session = mailboxManager.createSystemSession(USER_1);
        
        assertThat(session.getUser().getUserName()).isEqualTo(USER_1);
    }

    @Test
    public void user1ShouldNotHaveAnInbox() throws UnsupportedEncodingException, MailboxException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);
        
        MailboxPath inbox = MailboxPath.inbox(session);
        assertThat(mailboxManager.mailboxExists(inbox, session)).isFalse();
    }

    @Test
    public void createMailboxShouldReturnRightId() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath mailboxPath = MailboxPath.forUser(USER_1, "name.subfolder");
        Optional<MailboxId> mailboxId = mailboxManager.createMailbox(mailboxPath, session);
        MessageManager retrievedMailbox = mailboxManager.getMailbox(mailboxPath, session);

        assertThat(mailboxId.isPresent()).isTrue();
        assertThat(mailboxId.get()).isEqualTo(retrievedMailbox.getId());
    }

    @Test
    public void user1ShouldBeAbleToCreateInbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        
        assertThat(mailboxManager.mailboxExists(inbox, session)).isTrue();
    }

    @Test
    public void user1ShouldNotBeAbleToCreateInboxTwice() throws MailboxException, UnsupportedEncodingException {
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        mailboxManager.createMailbox(inbox, session);
    }

    @Test
    public void user1ShouldNotHaveTestSubmailbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        
        assertThat(mailboxManager.mailboxExists(new MailboxPath(inbox, "INBOX.Test"), session)).isFalse();
    }
    
    @Test
    public void user1ShouldBeAbleToCreateTestSubmailbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        mailboxManager.createMailbox(inboxSubMailbox, session);
        
        assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isTrue();
    }
    
    @Test
    public void user1ShouldBeAbleToDeleteInbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        mailboxManager.createMailbox(inboxSubMailbox, session);
        
        mailboxManager.deleteMailbox(inbox, session);
        
        assertThat(mailboxManager.mailboxExists(inbox, session)).isFalse();
        assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isTrue();
    }
    
    @Test
    public void user1ShouldBeAbleToDeleteSubmailbox() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);
     
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        MailboxPath inboxSubMailbox = new MailboxPath(inbox, "INBOX.Test");
        mailboxManager.createMailbox(inboxSubMailbox, session);
        
        mailboxManager.deleteMailbox(inboxSubMailbox, session);
        
        assertThat(mailboxManager.mailboxExists(inbox, session)).isTrue();
        assertThat(mailboxManager.mailboxExists(inboxSubMailbox, session)).isFalse();
    }

    @Test
    public void closingSessionShouldWork() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.startProcessingRequest(session);

        mailboxManager.logout(session, false);
        mailboxManager.endProcessingRequest(session);
        
        assertThat(session.isOpen()).isFalse();
    }

    @Test
    public void listShouldReturnMailboxes() throws MailboxException, UnsupportedEncodingException {
        session = mailboxManager.createSystemSession("manager");
        mailboxManager.startProcessingRequest(session);
        
        assertThat(mailboxManager.list(session)).hasSize(MockMailboxManager.EXPECTED_MAILBOXES_COUNT);
    }

    @Test
    public void user2ShouldBeAbleToCreateRootlessFolder() throws MailboxException {
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath trash = MailboxPath.forUser(USER_2, "Trash");
        mailboxManager.createMailbox(trash, session);
        
        assertThat(mailboxManager.mailboxExists(trash, session)).isTrue();
    }
    
    @Test
    public void user2ShouldBeAbleToCreateNestedFoldersWithoutTheirParents() throws Exception {
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath nestedFolder = MailboxPath.forUser(USER_2, "INBOX.testfolder");
        mailboxManager.createMailbox(nestedFolder, session);
        
        assertThat(mailboxManager.mailboxExists(nestedFolder, session)).isTrue();
        mailboxManager.getMailbox(MailboxPath.inbox(session), session)
            .appendMessage(AppendCommand.from(message), session);
    }

    @Test
    public void searchShouldNotReturnResultsFromOtherNamespaces() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Namespace));
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.createMailbox(new MailboxPath("other_namespace", USER_1, "Other"), session);
        mailboxManager.createMailbox(MailboxPath.inbox(session), session);
        List<MailboxMetaData> metaDatas = mailboxManager.search(
            MailboxQuery.privateMailboxesBuilder(session)
                .matchesAllMailboxNames()
                .build(),
            session);
        assertThat(metaDatas).hasSize(1);
        assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
    }

    @Test
    public void searchShouldNotReturnResultsFromOtherUsers() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);
        mailboxManager.createMailbox(MailboxPath.forUser(USER_2, "Other"), session);
        mailboxManager.createMailbox(MailboxPath.inbox(session), session);
        List<MailboxMetaData> metaDatas = mailboxManager.search(
            MailboxQuery.privateMailboxesBuilder(session)
                .matchesAllMailboxNames()
                .build(),
            session);
        assertThat(metaDatas).hasSize(1);
        assertThat(metaDatas.get(0).getPath()).isEqualTo(MailboxPath.inbox(session));
    }

    @Test
    public void updateAnnotationsShouldUpdateStoredAnnotation() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION));

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION_UPDATE));
        assertThat(mailboxManager.getAllAnnotations(inbox, session)).containsOnly(PRIVATE_ANNOTATION_UPDATE);
    }

    @Test
    public void updateAnnotationsShouldDeleteAnnotationWithNilValue() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION));

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(MailboxAnnotation.nil(PRIVATE_KEY)));
        assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEmpty();
    }

    @Test
    public void updateAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION));
    }

    @Test
    public void getAnnotationsShouldReturnEmptyForNonStoredAnnotation() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEmpty();
    }

    @Test
    public void getAllAnnotationsShouldRetrieveStoredAnnotations() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ANNOTATIONS);

        assertThat(mailboxManager.getAllAnnotations(inbox, session)).isEqualTo(ANNOTATIONS);
    }

    @Test
    public void getAllAnnotationsShouldThrowExceptionIfMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.getAllAnnotations(inbox, session);
    }

    @Test
    public void getAnnotationsByKeysShouldRetrieveStoresAnnotationsByKeys() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ANNOTATIONS);

        assertThat(mailboxManager.getAnnotationsByKeys(inbox, session, ImmutableSet.of(PRIVATE_KEY)))
            .containsOnly(PRIVATE_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysShouldThrowExceptionIfMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.getAnnotationsByKeys(inbox, session, ImmutableSet.of(PRIVATE_KEY));
    }

    @Test
    public void getAnnotationsByKeysWithOneDepthShouldRetriveAnnotationsWithOneDepth() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION));

        assertThat(mailboxManager.getAnnotationsByKeysWithOneDepth(inbox, session, ImmutableSet.of(PRIVATE_KEY)))
            .contains(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION);
    }

    @Test
    public void getAnnotationsByKeysWithAllDepthShouldThrowExceptionWhenMailboxDoesNotExist() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);

        mailboxManager.getAnnotationsByKeysWithAllDepth(inbox, session, ImmutableSet.of(PRIVATE_KEY));
    }

    @Test
    public void getAnnotationsByKeysWithAllDepthShouldRetriveAnnotationsWithAllDepth() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION));

        assertThat(mailboxManager.getAnnotationsByKeysWithAllDepth(inbox, session, ImmutableSet.of(PRIVATE_KEY)))
            .contains(PRIVATE_ANNOTATION, PRIVATE_CHILD_ANNOTATION, PRIVATE_GRANDCHILD_ANNOTATION);
    }

    @Test
    public void updateAnnotationsShouldThrowExceptionIfAnnotationDataIsOverLimitation() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(AnnotationException.class);
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        mailboxManager.updateAnnotations(inbox, session, ImmutableList.of(MailboxAnnotation.newInstance(PRIVATE_KEY, "The limitation of data is less than 30")));
    }

    @Test
    public void shouldUpdateAnnotationWhenRequestCreatesNewAndMailboxIsNotOverLimit() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        ImmutableList.Builder<MailboxAnnotation> builder = ImmutableList.builder();
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment1"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment2"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment3"), "AnyValue"));

        mailboxManager.updateAnnotations(inbox, session, builder.build());
    }

    @Test
    public void updateAnnotationsShouldThrowExceptionIfRequestCreateNewButMailboxIsOverLimit() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Annotation));
        expected.expect(MailboxException.class);
        session = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        ImmutableList.Builder<MailboxAnnotation> builder = ImmutableList.builder();
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment1"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment2"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment3"), "AnyValue"));
        builder.add(MailboxAnnotation.newInstance(new MailboxAnnotationKey("/private/comment4"), "AnyValue"));

        mailboxManager.updateAnnotations(inbox, session, builder.build());
    }

    @Test
    public void searchShouldNotDuplicateMailboxWhenReportedAsUserMailboxesAndUserHasRightOnMailboxes() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_1)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .matchesAllMailboxNames()
            .build();

        assertThat(mailboxManager.search(mailboxQuery, session1))
            .extracting(MailboxMetaData::getPath)
            .hasSize(1)
            .containsOnly(inbox1);
    }

    @Test
    public void searchShouldIncludeDelegatedMailboxes() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .matchesAllMailboxNames()
            .build();

        assertThat(mailboxManager.search(mailboxQuery, session2))
            .extracting(MailboxMetaData::getPath)
            .containsOnly(inbox1);
    }

    @Test
    public void searchShouldCombinePrivateAndDelegatedMailboxes() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        MailboxPath inbox2 = MailboxPath.inbox(session2);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.createMailbox(inbox2, session2);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .matchesAllMailboxNames()
            .build();

        assertThat(mailboxManager.search(mailboxQuery, session2))
            .extracting(MailboxMetaData::getPath)
            .containsOnly(inbox1, inbox2);
    }

    @Test
    public void searchShouldAllowUserFiltering() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        MailboxPath inbox2 = MailboxPath.inbox(session2);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.createMailbox(inbox2, session2);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .username(USER_1)
            .matchesAllMailboxNames()
            .build();

        assertThat(mailboxManager.search(mailboxQuery, session2))
            .extracting(MailboxMetaData::getPath)
            .containsOnly(inbox1);
    }

    @Test
    public void searchShouldAllowNamespaceFiltering() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        String specificNamespace = "specificNamespace";
        MailboxPath mailboxPath1 = new MailboxPath(specificNamespace, USER_1, "mailbox");
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.createMailbox(mailboxPath1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            session1);
        mailboxManager.setRights(mailboxPath1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .namespace(specificNamespace)
            .matchesAllMailboxNames()
            .build();

        assertThat(mailboxManager.search(mailboxQuery, session2))
            .extracting(MailboxMetaData::getPath)
            .containsOnly(mailboxPath1);
    }

    @Test
    public void searchForMessageShouldReturnMessagesFromAllMyMailboxesIfNoMailboxesAreSpecified() throws Exception {
        Assume.assumeTrue(mailboxManager
            .getSupportedMessageCapabilities()
            .contains(MailboxManager.MessageCapabilities.UniqueID));

        session = mailboxManager.createSystemSession(USER_1);

        MailboxPath cacahueteFolder = MailboxPath.forUser(USER_1, "CACAHUETE");
        MailboxId cacahueteMailboxId = mailboxManager.createMailbox(cacahueteFolder, session).get();
        MessageManager cacahueteMessageManager = mailboxManager.getMailbox(cacahueteMailboxId, session);
        MessageId cacahueteMessageId = cacahueteMessageManager
            .appendMessage(AppendCommand.from(message), session)
            .getMessageId();

        MailboxPath pirouetteFilder = MailboxPath.forUser(USER_1, "PIROUETTE");
        MailboxId pirouetteMailboxId = mailboxManager.createMailbox(pirouetteFilder, session).get();
        MessageManager pirouetteMessageManager = mailboxManager.getMailbox(pirouetteMailboxId, session);

        MessageId pirouetteMessageId = pirouetteMessageManager
            .appendMessage(AppendCommand.from(message), session)
            .getMessageId();

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .build();


        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .containsOnly(cacahueteMessageId, pirouetteMessageId);
    }

    @Test
    public void searchForMessageShouldReturnMessagesFromMyDelegatedMailboxes() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);
        MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
        MailboxPath delegatedMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
        MailboxId delegatedMailboxId = mailboxManager.createMailbox(delegatedMailboxPath, sessionFromDelegater).get();
        MessageManager delegatedMessageManager = mailboxManager.getMailbox(delegatedMailboxId, sessionFromDelegater);

        MessageId messageId = delegatedMessageManager
            .appendMessage(AppendCommand.from(message), sessionFromDelegater)
            .getMessageId();

        mailboxManager.setRights(delegatedMailboxPath,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_1)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            sessionFromDelegater);

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .containsOnly(messageId);
    }

    @Test
    public void searchForMessageShouldNotReturnMessagesFromMyDelegatedMailboxesICanNotRead() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);
        MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
        MailboxPath delegatedMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
        MailboxId delegatedMailboxId = mailboxManager.createMailbox(delegatedMailboxPath, sessionFromDelegater).get();
        MessageManager delegatedMessageManager = mailboxManager.getMailbox(delegatedMailboxId, sessionFromDelegater);

        delegatedMessageManager.appendMessage(AppendCommand.from(message), sessionFromDelegater);

        mailboxManager.setRights(delegatedMailboxPath,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_1)
                .rights(MailboxACL.Right.Lookup)
                .asAddition()),
            sessionFromDelegater);

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .isEmpty();
    }

    @Test
    public void searchForMessageShouldOnlySearchInMailboxICanRead() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);
        MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USER_2, "OTHER_MAILBOX");
        MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, sessionFromDelegater).get();
        MessageManager otherMailboxManager = mailboxManager.getMailbox(otherMailboxId, sessionFromDelegater);

        otherMailboxManager.appendMessage(AppendCommand.from(message), sessionFromDelegater);

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .isEmpty();
    }

    @Test
    public void searchForMessageShouldIgnoreMailboxThatICanNotRead() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);
        MailboxSession sessionFromDelegater = mailboxManager.createSystemSession(USER_2);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USER_2, "SHARED");
        MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, sessionFromDelegater).get();
        MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, sessionFromDelegater);

        otherMessageManager.appendMessage(AppendCommand.from(message), sessionFromDelegater);

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .inMailboxes(otherMailboxId)
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .isEmpty();
    }

    @Test
    public void searchForMessageShouldCorrectlyExcludeMailbox() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USER_1, "SHARED");
        MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, session).get();
        MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, session);

        otherMessageManager.appendMessage(AppendCommand.from(message), session);

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .notInMailboxes(otherMailboxId)
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .isEmpty();
    }

    @Test
    public void searchForMessageShouldPriorizeExclusionFromInclusion() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);
        MailboxPath otherMailboxPath = MailboxPath.forUser(USER_1, "SHARED");
        MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, session).get();
        MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, session);

        otherMessageManager.appendMessage(AppendCommand.from(message), session);

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .inMailboxes(otherMailboxId)
            .notInMailboxes(otherMailboxId)
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .isEmpty();
    }

    @Test
    public void searchForMessageShouldOnlySearchInGivenMailbox() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));

        session = mailboxManager.createSystemSession(USER_1);

        MailboxPath searchedMailboxPath = MailboxPath.forUser(USER_1, "WANTED");
        MailboxId searchedMailboxId = mailboxManager.createMailbox(searchedMailboxPath, session).get();
        MessageManager searchedMessageManager = mailboxManager.getMailbox(searchedMailboxId, session);

        MailboxPath otherMailboxPath = MailboxPath.forUser(USER_1, "SHARED");
        MailboxId otherMailboxId = mailboxManager.createMailbox(otherMailboxPath, session).get();
        MessageManager otherMessageManager = mailboxManager.getMailbox(otherMailboxId, session);

        otherMessageManager.appendMessage(AppendCommand.from(message), session);

        MessageId messageId = searchedMessageManager
            .appendMessage(AppendCommand.from(message), session)
            .getMessageId();

        MultimailboxesSearchQuery multiMailboxesQuery = MultimailboxesSearchQuery
            .from(new SearchQuery())
            .inMailboxes(searchedMailboxId)
            .build();

        assertThat(mailboxManager.search(multiMailboxesQuery, session, DEFAULT_MAXIMUM_LIMIT))
            .containsExactly(messageId);
    }

    @Test
    public void searchShouldNotReturnNoMoreDelegatedMailboxes() throws MailboxException {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asRemoval()),
            session1);

        MailboxQuery mailboxQuery = MailboxQuery.builder()
            .matchesAllMailboxNames()
            .build();

        assertThat(mailboxManager.search(mailboxQuery, session2))
            .isEmpty();
    }

    @Test
    public void getMailboxCountersShouldReturnDefaultValueWhenNoReadRight() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        mailboxManager.getMailbox(inbox1, session1).appendMessage(AppendCommand.from(message), session1);

        MailboxCounters mailboxCounters = mailboxManager.getMailbox(inbox1, session2)
            .getMailboxCounters(session2);

        assertThat(mailboxCounters)
            .isEqualTo(MailboxCounters.builder()
                .count(0)
                .unseen(0)
                .build());
    }

    @Test
    public void getMailboxCountersShouldReturnStoredValueWhenReadRight() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Lookup, MailboxACL.Right.Read)
                .asAddition()),
            session1);

        mailboxManager.getMailbox(inbox1, session1)
            .appendMessage(AppendCommand.builder()
                .recent()
                .build(message), session1);

        MailboxCounters mailboxCounters = mailboxManager.getMailbox(inbox1, session2)
            .getMailboxCounters(session2);

        assertThat(mailboxCounters)
            .isEqualTo(MailboxCounters.builder()
                .count(1)
                .unseen(1)
                .build());
    }

    @Test
    public void getMetaDataShouldReturnDefaultValueWhenNoReadRight() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.ACL));
        MailboxSession session1 = mailboxManager.createSystemSession(USER_1);
        MailboxSession session2 = mailboxManager.createSystemSession(USER_2);
        MailboxPath inbox1 = MailboxPath.inbox(session1);
        mailboxManager.createMailbox(inbox1, session1);
        mailboxManager.setRights(inbox1,
            MailboxACL.EMPTY.apply(MailboxACL.command()
                .forUser(USER_2)
                .rights(MailboxACL.Right.Lookup)
                .asAddition()),
            session1);

        mailboxManager.getMailbox(inbox1, session1)
            .appendMessage(AppendCommand.builder()
                .recent()
                .build(message), session1);

        boolean resetRecent = false;
        MessageManager.MetaData metaData = mailboxManager.getMailbox(inbox1, session2)
            .getMetaData(resetRecent, session2, MessageManager.MetaData.FetchGroup.UNSEEN_COUNT);

        softly.assertThat(metaData)
            .extracting(MessageManager.MetaData::getHighestModSeq)
            .contains(0L);
        softly.assertThat(metaData)
            .extracting(MessageManager.MetaData::getUidNext)
            .contains(MessageUid.MIN_VALUE);
        softly.assertThat(metaData)
            .extracting(MessageManager.MetaData::getMessageCount)
            .contains(0L);
        softly.assertThat(metaData)
            .extracting(MessageManager.MetaData::getUnseenCount)
            .contains(0L);
        softly.assertThat(metaData)
            .extracting(MessageManager.MetaData::getRecent)
            .contains(ImmutableList.of());
        softly.assertThat(metaData)
            .extracting(MessageManager.MetaData::getPermanentFlags)
            .contains(new Flags());
    }

    @Test
    public void addingMessageShouldFireQuotaUpdateEvent() throws Exception {
        Assume.assumeTrue(mailboxManager.hasCapability(MailboxCapabilities.Quota));
        session = mailboxManager.createSystemSession(USER_1);

        EventCollector listener = new EventCollector();
        mailboxManager.addGlobalListener(listener, session);

        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);
        mailboxManager.getMailbox(inbox, session)
            .appendMessage(MessageManager.AppendCommand.builder()
                .build(message), session);

        assertThat(listener.getEvents())
            .contains(new MailboxListener.QuotaUsageUpdatedEvent(
                session,
                QuotaRoot.quotaRoot("#private&" + USER_1, Optional.empty()),
                Quota.<QuotaCount>builder()
                    .used(QuotaCount.count(1))
                    .computedLimit(QuotaCount.unlimited())
                    .build(),
                Quota.<QuotaSize>builder()
                    .used(QuotaSize.size(85))
                    .computedLimit(QuotaSize.unlimited())
                    .build()));
    }

    @Test
    public void moveMessagesShouldNotThrowWhenMovingAllMessagesOfAnEmptyMailbox() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);

        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        assertThatCode(() -> mailboxManager.moveMessages(MessageRange.all(), inbox, inbox, session))
            .doesNotThrowAnyException();
    }

    @Test
    public void copyMessagesShouldNotThrowWhenMovingAllMessagesOfAnEmptyMailbox() throws Exception {
        session = mailboxManager.createSystemSession(USER_1);

        MailboxPath inbox = MailboxPath.inbox(session);
        mailboxManager.createMailbox(inbox, session);

        assertThatCode(() -> mailboxManager.copyMessages(MessageRange.all(), inbox, inbox, session))
            .doesNotThrowAnyException();
    }
}
