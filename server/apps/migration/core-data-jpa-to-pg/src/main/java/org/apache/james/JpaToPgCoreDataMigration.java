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

import static org.apache.james.modules.blobstore.BlobStoreModulesChooser.chooseBlobStoreDAOModule;
import static org.apache.james.modules.blobstore.BlobStoreModulesChooser.chooseEncryptionModule;
import static org.apache.james.modules.blobstore.BlobStoreModulesChooser.chooseStoragePolicyModule;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.domainlist.jpa.JPADomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.postgres.PostgresDomainList;
import org.apache.james.droplists.jpa.JPADropList;
import org.apache.james.droplists.postgres.PostgresDropList;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.mailrepository.api.MailKey;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.jpa.JPAMailRepositoryFactory;
import org.apache.james.mailrepository.jpa.JPAMailRepositoryUrlStore;
import org.apache.james.mailrepository.postgres.PostgresMailRepositoryContentDAO;
import org.apache.james.mailrepository.postgres.PostgresMailRepositoryUrlStore;
import org.apache.james.modules.ClockModule;
import org.apache.james.modules.blobstore.BlobStoreCacheModulesChooser;
import org.apache.james.modules.blobstore.BlobStoreConfiguration;
import org.apache.james.modules.data.JPAEntityManagerModule;
import org.apache.james.modules.data.PostgresCommonModule;
import org.apache.james.modules.data.PostgresDomainListModule;
import org.apache.james.modules.data.PostgresDropListsModule;
import org.apache.james.modules.data.PostgresQuotaGuiceModule;
import org.apache.james.modules.data.PostgresRecipientRewriteTableModule;
import org.apache.james.modules.data.PostgresUsersRepositoryModule;
import org.apache.james.modules.data.SievePostgresRepositoryModules;
import org.apache.james.modules.server.DNSServiceModule;
import org.apache.james.modules.server.DropWizardMetricsModule;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.jpa.JPARecipientRewriteTable;
import org.apache.james.rrt.postgres.PostgresRecipientRewriteTable;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.server.core.configuration.FileConfigurationProvider;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.sieve.jpa.JPASieveRepository;
import org.apache.james.sieve.jpa.model.JPASieveScript;
import org.apache.james.sieve.postgres.PostgresSieveRepository;
import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.jpa.JPAUsersDAO;
import org.apache.james.user.jpa.model.JPAUser;
import org.apache.james.user.lib.model.Algorithm;
import org.apache.james.user.lib.model.DefaultUser;
import org.apache.james.user.postgres.PostgresUsersDAO;
import org.apache.james.user.postgres.PostgresUsersRepositoryConfiguration;
import org.apache.james.util.LoggingLevel;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitializationOperations;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;

public class JpaToPgCoreDataMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(JpaToPgCoreDataMigration.class);

    private static final Module JPA_MODULES = Modules.combine(
            new JPAEntityManagerModule(),
            new UnboundJPAMigrationModule()
    );
    private static final Module POSTGRESQL_MODULES = Modules.combine(
            new PostgresCommonModule(),
            new PostgresDomainListModule(),
            new PostgresRecipientRewriteTableModule(),
            new PostgresUsersRepositoryModule(),
            new org.apache.james.modules.data.PostgresMailRepositoryModule(),
            new PostgresDropListsModule(),
            new PostgresQuotaGuiceModule(),
            new SievePostgresRepositoryModules()
    );

    static final Module MIGRATION_MODULES = Modules.combine(
            new DNSServiceModule(),
            new DropWizardMetricsModule(),
            new ClockModule(),
            new CoreEntityValidatorsModule(),
            PostgresUsersRepositoryModule.USER_CONFIGURATION_MODULE,
            JPA_MODULES,
            POSTGRESQL_MODULES
    );

    private final Injector injector;

    public static void main(String[] args) {
        MigrationConfiguration configuration = MigrationConfiguration.builder()
                .useWorkingDirectoryEnvProperty()
                .build();
        LOGGER.info("Loading configuration {}", configuration.toString());
        var blobstoreModule  = Modules.combine(chooseBlobStoreModules(configuration));
        var module = Modules.combine(
                new MigrationModule(configuration),
                MIGRATION_MODULES,
                blobstoreModule
        );


        JpaToPgCoreDataMigration migration = new JpaToPgCoreDataMigration(module);
        migration.start();
    }

    static List<Module> chooseBlobStoreModules(MigrationConfiguration configuration) {
        ImmutableList.Builder<Module> builder = ImmutableList.<Module>builder()
                .addAll(chooseModules(configuration.blobStoreConfiguration()))
                .add(new BlobStoreCacheModulesChooser.CacheDisabledModule());


        return builder.build();
    }

    public static List<Module> chooseModules(BlobStoreConfiguration choosingConfiguration) {
        return ImmutableList.<Module>builder()
                .add(chooseEncryptionModule(choosingConfiguration.getCryptoConfig()))
                .add(chooseBlobStoreDAOModule(choosingConfiguration.getImplementation()))
                .addAll(chooseStoragePolicyModule(choosingConfiguration.storageStrategy()))
                .add(binder -> binder.bind(BlobStoreConfiguration.class).toInstance(choosingConfiguration))
                .build();
    }

    static class DomainMigration {
        private static final Logger LOGGER = LoggerFactory.getLogger(DomainMigration.class);

        private final JPADomainList jpaDomainList;
        private final PostgresDomainList pgDomainList;

        @Inject
        DomainMigration(
                JPADomainList jpaDomainList,
                PostgresDomainList pgDomainList
        ) {
            this.jpaDomainList = jpaDomainList;
            this.pgDomainList = pgDomainList;
        }

        void doMigration() {
            LOGGER.info("Start domains migration");
            try {
                jpaDomainList.getDomains().forEach(domain -> {
                            try {
                                pgDomainList.addDomain(domain);
                            } catch (DomainListException e) {
                                if (!e.getMessage().contains("already exists.")) {
                                    LOGGER.warn("Failed to migrate domain {}", domain, e);
                                }
                            }
                        }
                );

            } catch (DomainListException e) {
                throw new RuntimeException("Unable to migrate domains, aborting processing", e);
            }
            LOGGER.info("Domains migration completed");
        }
    }

    static class UsersMigration {
        private static final Logger LOGGER = LoggerFactory.getLogger(UsersMigration.class);
        private final Algorithm algorithm;
        private final JPAUsersDAO jpaUsersDAO;
        private final PostgresUsersDAO postgresUsersDAO;


        @Inject
        UsersMigration(
                JPAUsersDAO jpaUsersDAO,
                PostgresUsersDAO postgresUsersDAO,
                PostgresUsersRepositoryConfiguration postgresUsersRepositoryConfiguration
        ) {
            this.algorithm = postgresUsersRepositoryConfiguration.getPreferredAlgorithm();
            this.jpaUsersDAO = jpaUsersDAO;
            this.postgresUsersDAO = postgresUsersDAO;
        }

        void doMigration() {
            LOGGER.info("Start users migration");
            try {
                jpaUsersDAO.list().forEachRemaining(username -> {
                            try {
                                jpaUsersDAO.getUserByName(username).ifPresent(user -> {
                                    var jpaUser = (JPAUser) user;
                                    var pgUser = new DefaultUser(
                                            username,
                                            jpaUser.getPasswordHash(),
                                            jpaUser.getAlgorithm(),
                                            algorithm
                                    );
                                    try {
                                        postgresUsersDAO.addUser(username, UUID.randomUUID().toString());
                                    } catch (RuntimeException e) {
                                        if (!(e.getCause() instanceof AlreadyExistInUsersRepositoryException)) {
                                            throw e;
                                        }
                                        // Idempotent behavior, if the user already exists, we
                                        // only update it.
                                    }
                                    Throwing.runnable(() ->
                                            postgresUsersDAO.updateUser(pgUser)
                                    ).sneakyThrow().run();

                                });
                            } catch (UsersRepositoryException e) {
                                LOGGER.warn("Failed to migrate user {}", username, e);
                            }
                        }
                );
            } catch (UsersRepositoryException e) {
                throw new RuntimeException("Failed to retrieve users for migration", e);
            }
            LOGGER.info("Users migration complete");
        }
    }

    static class RRTMigration {
        private static final Logger LOGGER = LoggerFactory.getLogger(RRTMigration.class);

        private final JPARecipientRewriteTable jpaRRTRepository;
        private final PostgresRecipientRewriteTable pgRRTRepository;

        @Inject
        RRTMigration(
                JPARecipientRewriteTable jpaRRTRepository,
                PostgresRecipientRewriteTable pgRRTRepository
        ) {
            this.jpaRRTRepository = jpaRRTRepository;
            this.pgRRTRepository = pgRRTRepository;
        }

        void doMigration() {
            LOGGER.info("Start RRT migration");
            try {
                jpaRRTRepository.getAllMappings().forEach((mappingSource, mappings) ->
                        mappings.forEach(mapping ->
                                pgRRTRepository.addMapping(mappingSource, mapping)
                        )
                );
            } catch (RecipientRewriteTableException e) {
                throw new RuntimeException(e);
            }
            LOGGER.info("RRT migration complete");
        }
    }

    static class MailRepositoryMigration {
        private static final Logger LOGGER = LoggerFactory.getLogger(MailRepositoryMigration.class);

        private final JPAMailRepositoryUrlStore jpaMailRepositoryUrlStore;
        private final JPAMailRepositoryFactory jpaMailRepositoryFactory;
        private final PostgresMailRepositoryUrlStore pgMailRepositoryUrlStore;
        private final PostgresMailRepositoryContentDAO postgresMailRepositoryContentDAO;

        @Inject
        MailRepositoryMigration(
                JPAMailRepositoryUrlStore jpaMailRepositoryUrlStore,
                JPAMailRepositoryFactory jpaMailRepositoryFactory,
                PostgresMailRepositoryUrlStore pgMailRepositoryUrlStore,
                PostgresMailRepositoryContentDAO postgresMailRepositoryContentDAO
        ) {
            this.jpaMailRepositoryUrlStore = jpaMailRepositoryUrlStore;
            this.jpaMailRepositoryFactory = jpaMailRepositoryFactory;
            this.pgMailRepositoryUrlStore = pgMailRepositoryUrlStore;
            this.postgresMailRepositoryContentDAO = postgresMailRepositoryContentDAO;
        }

        void doMigration() {
            LOGGER.info("Start mail repository urls migration");
            jpaMailRepositoryUrlStore.listDistinct().forEach(
                    Throwing.consumer((MailRepositoryUrl url) -> {
                        pgMailRepositoryUrlStore.add(url);
                        MailRepository mailRepository = jpaMailRepositoryFactory.create(url);
                        mailRepository.list().forEachRemaining(
                                Throwing.consumer((MailKey mailKey) -> {
                                    Mail jpaMail = mailRepository.retrieve(mailKey);
                                    postgresMailRepositoryContentDAO.store(jpaMail, url);
                                })
                        );
                    }).sneakyThrow()
            );
            LOGGER.info("Mail repository urls migration complete");
        }
    }

    static class DropListMigration {
        private static final Logger LOGGER =
                LoggerFactory.getLogger(MailRepositoryMigration.class);

        private final JPADropList jpaDropList;
        private final PostgresDropList pgDropList;

        @Inject
        DropListMigration(
                JPADropList jpaDropList,
                PostgresDropList pgDropList
        ) {
            this.jpaDropList = jpaDropList;
            this.pgDropList = pgDropList;
        }

        void doMigration() {
            LOGGER.info("Start drop list migration");
            jpaDropList.listAll().flatMap(pgDropList::add).count().block();
            LOGGER.info("Mail drop list migration complete");
        }
    }

    static class SieveMigration {
        private static final Logger LOGGER =
                LoggerFactory.getLogger(MailRepositoryMigration.class);

        private final JPASieveRepository jpaSieveRepository;
        private final PostgresSieveRepository pgSieveRepository;

        @Inject
        SieveMigration(
                JPASieveRepository jpaSieveRepository,
                PostgresSieveRepository pgSieveRepository
        ) {
            this.jpaSieveRepository = jpaSieveRepository;
            this.pgSieveRepository = pgSieveRepository;
        }

        void doMigration() {
            LOGGER.info("Start Sieve quotas migration");
            jpaSieveRepository.listAllSieveQuotas().forEach(quota ->
                    pgSieveRepository.setQuota(quota.getUsername(), quota.toQuotaSize())
            );
            LOGGER.info("Mail Sieve quotas migration complete");
            LOGGER.info("Start Sieve scripts migration");
            jpaSieveRepository.listAllSieveScripts().forEach(
                    Throwing.consumer((JPASieveScript sieveScript) -> pgSieveRepository.putScript(
                            Username.of(sieveScript.getUsername()),
                            new ScriptName(sieveScript.getScriptName()),
                            new ScriptContent(sieveScript.getScriptContent())
                    )).sneakyThrow()
            );
            LOGGER.info("Mail Sieve scripts migration complete");
        }
    }

    static class MigrationModule extends AbstractModule {
        private final Configuration configuration;
        private final FileSystemImpl fileSystem;

        @Inject
        public MigrationModule(Configuration configuration) {
            this.configuration = configuration;
            this.fileSystem = new FileSystemImpl(configuration.directories());
        }

        @Override
        protected void configure() {
            bind(FileSystem.class).toInstance(fileSystem);
            bind(Configuration.class).toInstance(configuration);

            bind(ConfigurationProvider.class).toInstance(new FileConfigurationProvider(fileSystem, configuration));
            bind(DomainMigration.class).in(Scopes.SINGLETON);
            bind(UsersMigration.class).in(Scopes.SINGLETON);
            bind(RRTMigration.class).in(Scopes.SINGLETON);
            bind(MailRepositoryMigration.class).in(Scopes.SINGLETON);
        }

        @Provides
        @jakarta.inject.Singleton
        public Configuration.ConfigurationPath configurationPath() {
            return configuration.configurationPath();
        }

        @Provides
        @jakarta.inject.Singleton
        public PropertiesProvider providePropertiesProvider(FileSystem fileSystem, Configuration.ConfigurationPath configurationPrefix) {
            return new PropertiesProvider(fileSystem, configurationPrefix);
        }

        @Provides
        @Singleton
        DomainListConfiguration domainListConfiguration() {
            return DomainListConfiguration.DEFAULT;
        }
    }

    static class UnboundJPAMigrationModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(JPADomainList.class).in(Scopes.SINGLETON);
            bind(JPARecipientRewriteTable.class).in(Scopes.SINGLETON);
            bind(JPAMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
            bind(JPAUsersDAO.class).in(Scopes.SINGLETON);
        }

        @ProvidesIntoSet
        InitializationOperation configureDomainList(DomainListConfiguration configuration, JPADomainList jpaDomainList) {
            return InitilizationOperationBuilder
                    .forClass(JPADomainList.class)
                    .init(() -> jpaDomainList.configure(configuration));
        }

        @ProvidesIntoSet
        InitializationOperation configureJpaUsers(
                EntityManagerFactory entityManagerFactory,
                ConfigurationProvider configurationProvider,
                JPAUsersDAO usersDAO
        ) {
            return InitilizationOperationBuilder
                    .forClass(JPAUsersDAO.class)
                    .init(() -> {
                        usersDAO.configure(configurationProvider.getConfiguration("usersrepository", LoggingLevel.INFO));
                        usersDAO.setEntityManagerFactory(entityManagerFactory);
                        usersDAO.init();
                    });
        }
    }

    static class CoreEntityValidatorsModule extends AbstractModule {
        @Override
        protected void configure() {
            Multibinder.newSetBinder(binder(), UserEntityValidator.class).addBinding().to(DefaultUserEntityValidator.class);
            Multibinder.newSetBinder(binder(), UserEntityValidator.class).addBinding().to(RecipientRewriteTableUserEntityValidator.class);
        }

        @Provides
        @Singleton
        UserEntityValidator userEntityValidator(Set<UserEntityValidator> validatorSet) {
            return new AggregateUserEntityValidator(validatorSet);
        }
    }

    static class PostgresMailRepositoryModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(PostgresMailRepositoryUrlStore.class).in(Scopes.SINGLETON);
            bind(MailRepositoryUrlStore.class).to(PostgresMailRepositoryUrlStore.class);
            Multibinder.newSetBinder(binder(), PostgresModule.class)
                    .addBinding().toInstance(org.apache.james.mailrepository.postgres.PostgresMailRepositoryModule.MODULE);
        }
    }

    Injector getInjector() {
        return injector;
    }

    public JpaToPgCoreDataMigration(Module module) {
        this.injector = Guice.createInjector(module);
        injector.getInstance(InitializationOperations.class).initModules();
    }

    void start() {
        injector.getInstance(DomainMigration.class).doMigration();
        injector.getInstance(UsersMigration.class).doMigration();
        injector.getInstance(RRTMigration.class).doMigration();
        injector.getInstance(MailRepositoryMigration.class).doMigration();
        injector.getInstance(DropListMigration.class).doMigration();
        injector.getInstance(SieveMigration.class).doMigration();
    }
}

