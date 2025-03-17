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

package org.apache.james;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.jpa.JPADomainList;
import org.apache.james.domainlist.postgres.PostgresDomainList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.apache.james.droplists.jpa.JPADropList;
import org.apache.james.droplists.postgres.PostgresDropList;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.jpa.JPAMailRepositoryFactory;
import org.apache.james.mailrepository.jpa.JPAMailRepositoryUrlStore;
import org.apache.james.mailrepository.postgres.PostgresMailRepositoryFactory;
import org.apache.james.mailrepository.postgres.PostgresMailRepositoryUrlStore;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.jpa.JPARecipientRewriteTable;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.postgres.PostgresRecipientRewriteTable;
import org.apache.james.server.core.MailImpl;
import org.apache.james.sieve.jpa.JPASieveRepository;
import org.apache.james.sieve.postgres.PostgresSieveRepository;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.jpa.JPAUsersDAO;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.mailet.Attribute;
import org.apache.mailet.Mail;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.rules.TemporaryFolder;

import com.github.fge.lambdas.Throwing;
import com.google.inject.Injector;
import com.google.inject.util.Modules;

class JpaToPgCoreDataMigrationTest {

    @RegisterExtension
    static MariaDBExtension mariaDBExtension = new MariaDBExtension();

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @RegisterExtension
    static TemporaryFolderRegistrableExtension folderRegistrableExtension = new TemporaryFolderRegistrableExtension();

    record User(Username username, String password) {
        static User create(Domain domain) {
            return new User(
                    Username.of(randomUUID() + "@" + domain.asString()),
                    randomUUID().toString()
            );
        }
    }

    record RecipientRewrite(Username username, Mapping mapping) {
        static RecipientRewrite create(Domain domain) {
            return new RecipientRewrite(
                    Username.of(randomUUID() + "@" + domain.asString()),
                    Mapping.forward(randomUUID() + "@email.example")
            );
        }
    }

    record MailInRepository(MailRepositoryUrl url, Mail mail) {
        static MailInRepository create(Domain domain, MailRepositoryUrl url) throws MessagingException {
            return new MailInRepository(
                    url,
                    MailImpl.builder()
                            .name(UUID.randomUUID().toString())
                            .sender(randomUUID() + "@" + domain.asString())
                            .addRecipient(randomUUID() + "@" + domain.asString())
                            .addRecipient(randomUUID() + "@" + domain.asString())
                            .addAttribute(Attribute.convertToAttribute("attr" + randomUUID(), "value" + randomUUID()))
                            .mimeMessage(MimeMessageBuilder
                                    .mimeMessageBuilder()
                                    .addHeader(UUID.randomUUID().toString(), UUID.randomUUID().toString())
                                    .setSubject("test")
                                    .setText("test" + UUID.randomUUID())
                                    .build())
                            .state(Mail.DEFAULT)
                            .lastUpdated(new Date())
                            .build()
            );
        }
    }

    record SieveEntry(Username username, QuotaSizeLimit quota, ScriptName scriptName, ScriptContent scriptContent) {
        static SieveEntry create(Domain domain) {
            return new SieveEntry(
                    Username.of(randomUUID() + "@" + domain.asString()),
                    QuotaSizeLimit.size(RandomUtils.nextLong()),
                    new ScriptName(UUID.randomUUID().toString()),
                    new ScriptContent(UUID.randomUUID().toString())
            );
        }
    }

    private static @NotNull Domain someDomain() {
        return Domain.of(randomUUID() + ".example");
    }

    private JpaToPgCoreDataMigration dataMigration;
    private Injector injector;

    @BeforeEach
    void setUp() throws IOException {
        dataMigration = createDataMigration();
        injector = dataMigration.getInjector();
    }

    @Test
    void should_migrate_jpa_schema_to_pg_schema() throws Exception {
        // Given
        var domain = givenJpaDomain();
        var user = givenJpaUser(domain);
        var recipientRewrite = givenJpaRecipientRewrite(domain);
        var mailRepositoryUrl = givenJpaMailRepositoryUrl();
        var mail = givenJpaMailInRepository(domain, mailRepositoryUrl);
        var dropListEntry = givenJpaDropList();
        var sieve = givenJpaSieveEntry(domain);

        // When
        dataMigration.start();

        // Then
        verifyPgDomain(domain);
        verifyPgUser(user);
        verifyPgRecipientRewrite(recipientRewrite);
        verifyPgRepositoryUrl(mailRepositoryUrl);
        verifyPgMailInRepository(mail);
        verifyPgDropList(dropListEntry);
        verifyPgSieveEntry(sieve);
    }

    @Test
    void should_be_idempotent_when_run_twice() throws Exception {
        // Given
        var domain = givenJpaDomain();
        var user = givenJpaUser(domain);
        var recipientRewrite = givenJpaRecipientRewrite(domain);
        var mailRepositoryUrl = givenJpaMailRepositoryUrl();
        var mail = givenJpaMailInRepository(domain, mailRepositoryUrl);
        var dropListEntry = givenJpaDropList();
        var sieve = givenJpaSieveEntry(domain);

        // When
        dataMigration.start();
        dataMigration.start();

        // Then
        verifyPgDomain(domain);
        verifyPgUser(user);
        verifyPgRecipientRewrite(recipientRewrite);
        verifyPgRepositoryUrl(mailRepositoryUrl);
        verifyPgMailInRepository(mail);
        verifyPgDropList(dropListEntry);
        verifyPgSieveEntry(sieve);
    }

    private Domain givenJpaDomain() throws DomainListException {
        var domain = Domain.of(randomUUID() + ".apache.example");
        var jpaDomainList = injector.getInstance(JPADomainList.class);
        jpaDomainList.addDomain(domain);
        return domain;
    }

    private void verifyPgDomain(Domain domain) throws DomainListException {
        var domainList = injector.getInstance(PostgresDomainList.class);
        assertThat(domainList.containsDomain(domain)).isTrue();
    }

    private User givenJpaUser(Domain domain) throws UsersRepositoryException {
        var user = User.create(domain);
        var usersDAO = injector.getInstance(JPAUsersDAO.class);
        usersDAO.addUser(user.username, user.password);
        return user;
    }

    private void verifyPgUser(User user) {
        var usersDAO = injector.getInstance(PostgresUsersDAO.class);
        var pgUser = usersDAO.getUserByName(user.username).orElseThrow();
        assertThat(pgUser.verifyPassword(user.password)).isTrue();
    }


    private RecipientRewrite givenJpaRecipientRewrite(Domain domain)
            throws RecipientRewriteTableException {
        var recipientRewrite = RecipientRewrite.create(domain);
        var recipientRewriteTable = injector.getInstance(JPARecipientRewriteTable.class);
        recipientRewriteTable.addMapping(
                MappingSource.fromUser(recipientRewrite.username),
                recipientRewrite.mapping
        );
        return recipientRewrite;
    }

    private void verifyPgRecipientRewrite(RecipientRewrite recipientRewrite)
            throws RecipientRewriteTable.ErrorMappingException, RecipientRewriteTableException {
        var rewriteTable = injector.getInstance(PostgresRecipientRewriteTable.class);
        var resolvedMappings = rewriteTable.getResolvedMappings(
                recipientRewrite.username.getLocalPart(),
                recipientRewrite.username.getDomainPart().orElseThrow()
        );
        assertThat(resolvedMappings).contains(recipientRewrite.mapping);
    }

    private MailRepositoryUrl givenJpaMailRepositoryUrl() {
        var url = MailRepositoryUrl.from("blob://var/mail/" + randomUUID());
        var urlStore = injector.getInstance(JPAMailRepositoryUrlStore.class);
        urlStore.add(url);
        return url;
    }

    private void verifyPgRepositoryUrl(MailRepositoryUrl url) {
        var urlStore = injector.getInstance(PostgresMailRepositoryUrlStore.class);
        assertThat(urlStore.contains(url)).isTrue();
    }

    private MailInRepository givenJpaMailInRepository(Domain domain, MailRepositoryUrl url) throws MessagingException {
        MailInRepository mailInRepository = MailInRepository.create(domain, url);
        var mail = mailInRepository.mail;
        var mailRepositoryFactory = injector.getInstance(JPAMailRepositoryFactory.class);
        MailRepository mailRepository = mailRepositoryFactory.create(url);
        mailRepository.store(mail);
        return mailInRepository;
    }

    private void checkMailEquality(Mail actual, Mail expected) {
        assertSoftly(Throwing.consumer(softly -> {
            softly.assertThat(actual.getMessage().getContent()).isEqualTo(expected.getMessage().getContent());
            softly.assertThat(actual.getMessageSize()).isEqualTo(expected.getMessageSize());
            softly.assertThat(actual.getName()).isEqualTo(expected.getName());
            softly.assertThat(actual.getState()).isEqualTo(expected.getState());
            softly.assertThat(actual.attributes()).containsAll(expected.attributes().toList());
            softly.assertThat(actual.getErrorMessage()).isEqualTo(expected.getErrorMessage());
            softly.assertThat(actual.getRemoteHost()).isEqualTo(expected.getRemoteHost());
            softly.assertThat(actual.getRemoteAddr()).isEqualTo(expected.getRemoteAddr());
            // JPA implementation trucates the date to seconds losing precision in the process
            softly.assertThat(actual.getLastUpdated().toInstant()).isEqualTo(expected.getLastUpdated().toInstant().truncatedTo(ChronoUnit.SECONDS));
            softly.assertThat(actual.getPerRecipientSpecificHeaders()).isEqualTo(expected.getPerRecipientSpecificHeaders());
        }));
    }


    private void verifyPgMailInRepository(MailInRepository mailInRepository) throws MessagingException {
        var mailRepositoryFactory = injector.getInstance(PostgresMailRepositoryFactory.class);
        var mailRepository = mailRepositoryFactory.create(mailInRepository.url);

        Mail retrieved = mailRepository.retrieve(MailKey.forMail(mailInRepository.mail));
        assertThat(retrieved).satisfies(actual -> checkMailEquality(actual, mailInRepository.mail));
    }

    private DropListEntry givenJpaDropList() throws AddressException {
        Domain aDomain = someDomain();
        Domain anotherDomain = someDomain();
        DropListEntry entry = DropListEntry.builder()
                .denyDomain(aDomain)
                .denyAddress(User.create(anotherDomain).username.asMailAddress())
                .forAll()
                .build();
        var dropList = injector.getInstance(JPADropList.class);
        dropList.add(entry).block();
        return entry;
    }

    private void verifyPgDropList(DropListEntry entry) {
        var dropList = injector.getInstance(PostgresDropList.class);
        List<DropListEntry> entries =
                dropList.list(OwnerScope.GLOBAL, "").collectList().block();
        assertThat(entries).contains(entry);
    }

    private SieveEntry givenJpaSieveEntry(Domain domain)
            throws StorageException, QuotaExceededException {
        var sieveEntry = SieveEntry.create(domain);
        var sieveRepository = injector.getInstance(JPASieveRepository.class);
        sieveRepository.setQuota(sieveEntry.username, sieveEntry.quota);
        sieveRepository.putScript(
                sieveEntry.username,
                sieveEntry.scriptName,
                sieveEntry.scriptContent
        );
        return sieveEntry;
    }

    private void verifyPgSieveEntry(SieveEntry sieveEntry)
            throws QuotaNotFoundException, ScriptNotFoundException {
        var sieveRepository = injector.getInstance(PostgresSieveRepository.class);
        var quota = sieveRepository.getQuota(sieveEntry.username);
        var scripts = sieveRepository.listScripts(sieveEntry.username);
        var script = sieveRepository.getScript(sieveEntry.username, sieveEntry.scriptName);

        assertThat(quota).isEqualTo(sieveEntry.quota);
        assertThat(scripts).hasSize(1);
        assertThat(script).hasSameContentAs(
                IOUtils.toInputStream(sieveEntry.scriptContent.getValue(), StandardCharsets.UTF_8)
        );
    }

    private @NotNull JpaToPgCoreDataMigration createDataMigration() throws IOException {
        TemporaryFolder tmpDir = folderRegistrableExtension.getTemporaryFolder();
        MigrationConfiguration configuration = MigrationConfiguration.builder()
                .workingDirectory(tmpDir.newFolder())
                .configurationFromClasspath()
                .blobStore(BlobStoreConfiguration.builder()
                        .postgres()
                        .disableCache()
                        .passthrough()
                        .noCryptoConfig())
                .build();

        var blobstoreModule = Modules.combine(JpaToPgCoreDataMigration.chooseBlobStoreModules(configuration));
        var module = Modules.override(
                Modules.combine(
                        JpaToPgCoreDataMigration.MIGRATION_MODULES,
                        blobstoreModule
                )
        ).with(
                new JpaToPgCoreDataMigration.MigrationModule(configuration),
                mariaDBExtension.getModule(),
                postgresExtension.getModule()
        );
        return new JpaToPgCoreDataMigration(module);
    }

}