package org.apache.james.modules;

import org.apache.james.modules.protocols.IMAPServerModule;
import org.apache.james.modules.protocols.LMTPServerModule;
import org.apache.james.modules.protocols.ManageSieveServerModule;
import org.apache.james.modules.protocols.POP3ServerModule;
import org.apache.james.modules.protocols.ProtocolHandlerModule;
import org.apache.james.modules.protocols.SMTPServerModule;
import org.apache.james.modules.server.WebAdminServerModule;
import org.apache.james.utils.GuiceServerProbe;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class ProtocolsModuleWithoutJMAP extends AbstractModule {
    @Override
    protected void configure() {
        install(new IMAPServerModule());
        install(new ProtocolHandlerModule());
        install(new POP3ServerModule());
        install(new SMTPServerModule());
        install(new LMTPServerModule());
        install(new ManageSieveServerModule());
        install(new WebAdminServerModule());

        bind(GuiceServerProbe.class).in(Scopes.SINGLETON);

    }

}
