package org.apache.james.xodus;

import org.apache.james.ExtraProperties;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerMain;
import org.apache.james.modules.BlobExportMechanismModule;
import org.apache.james.modules.LegacyEncryptionModule;
import org.apache.james.modules.MailboxModule;
import org.apache.james.modules.MailetProcessingModule;
import org.apache.james.modules.RunArgumentsModule;
import org.apache.james.modules.eventstore.MemoryEventStoreModule;
import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.queue.memory.MemoryMailQueueModule;
import org.apache.james.modules.server.DKIMMailetModule;
import org.apache.james.modules.server.DataRoutesModules;
import org.apache.james.modules.server.InconsistencyQuotasSolvingRoutesModule;
import org.apache.james.modules.server.MailQueueRoutesModule;
import org.apache.james.modules.server.MailRepositoriesRoutesModule;
import org.apache.james.modules.server.MailboxRoutesModule;
import org.apache.james.modules.server.MailboxesBackupRoutesModule;
import org.apache.james.modules.server.ReIndexingModule;
import org.apache.james.modules.server.SieveRoutesModule;
import org.apache.james.modules.server.DefaultProcessorsConfigurationProviderModule;
import org.apache.james.modules.server.NoJwtModule;
import org.apache.james.modules.server.RawPostDequeueDecoratorModule;
import org.apache.james.modules.server.TaskManagerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Module;
import com.google.inject.util.Modules;

public class XodusJamesServerMain implements JamesServerMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(XodusJamesServerMain.class);

    public static final Module WEBADMIN = Modules.combine(
        new WebAdminServerModule(),
        new DataRoutesModules(),
        new InconsistencyQuotasSolvingRoutesModule(),
        new MailboxesBackupRoutesModule(),
        new MailboxRoutesModule(),
        new MailQueueRoutesModule(),
        new MailRepositoriesRoutesModule(),
        new ReIndexingModule(),
        new SieveRoutesModule());

    public static final Module PROTOCOLS = Modules.combine(
        new IMAPServerModule(),
        new LegacyEncryptionModule(),
        new ManageSieveServerModule(),
        new ProtocolHandlerModule(),
        new SMTPServerModule());

    public static final Module XODUS_SERVER_MODULE = Modules.combine(
        new MailetProcessingModule(),
        new org.apache.james.modules.data.MemoryDelegationStoreModule(),
        new XodusBlobModule(),
        new BlobExportMechanismModule(),
        new MailboxModule(),
        new MemoryEventStoreModule(),
        new XodusDataModule(),
        new XodusMailboxModule(),
        new MemoryMailQueueModule(),
        new TaskManagerModule(),
        new NoJwtModule(),
        new RawPostDequeueDecoratorModule(),
        new DefaultProcessorsConfigurationProviderModule());

    public static final Module XODUS_SERVER_AGGREGATE_MODULE = Modules.combine(
        XODUS_SERVER_MODULE,
        PROTOCOLS,
        WEBADMIN,
        new DKIMMailetModule());

    public static void main(String[] args) throws Exception {
        ExtraProperties.initialize();

        XodusJamesConfiguration configuration = XodusJamesConfiguration.builder()
            .useWorkingDirectoryEnvProperty()
            .build();

        LOGGER.info("Starting James with Xodus configuration");
        GuiceJamesServer server = createServer(configuration)
            .overrideWith(new RunArgumentsModule(args));

        JamesServerMain.main(server);
    }

    public static GuiceJamesServer createServer(XodusJamesConfiguration configuration) {
        return GuiceJamesServer.forConfiguration(configuration)
            .combineWith(XODUS_SERVER_AGGREGATE_MODULE);
    }
}
