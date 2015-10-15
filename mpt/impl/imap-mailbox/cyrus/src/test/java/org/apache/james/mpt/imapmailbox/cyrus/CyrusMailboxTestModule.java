package org.apache.james.mpt.imapmailbox.cyrus;

import org.apache.james.mpt.api.HostSystem;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.api.UserAdder;
import org.apache.james.mpt.host.ExternalHostSystem;
import org.apache.james.mpt.imapmailbox.GrantRightsOnHost;
import org.apache.james.mpt.imapmailbox.MailboxMessageAppender;
import org.apache.james.mpt.imapmailbox.cyrus.host.CyrusHostSystem;
import org.apache.james.mpt.imapmailbox.cyrus.host.CyrusUserAdder;
import org.apache.james.mpt.imapmailbox.cyrus.host.Docker;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.spotify.docker.client.messages.ContainerCreation;

import org.apache.james.mpt.imapmailbox.cyrus.host.GrantRightsOnCyrusHost;
import org.apache.james.mpt.imapmailbox.cyrus.host.MailboxMessageAppenderOnCyrusHost;

public class CyrusMailboxTestModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(Docker.class).toInstance(new Docker("linagora/cyrus-imap"));
        bind(ContainerCreation.class).toProvider(CyrusHostSystem.class);
        bind(ImapHostSystem.class).to(CyrusHostSystem.class);
        bind(HostSystem.class).to(CyrusHostSystem.class);
        bind(ExternalHostSystem.class).to(CyrusHostSystem.class);
        bind(UserAdder.class).to(CyrusUserAdder.class);
        bind(GrantRightsOnHost.class).to(GrantRightsOnCyrusHost.class);
        bind(MailboxMessageAppender.class).to(MailboxMessageAppenderOnCyrusHost.class);
        bindConstant().annotatedWith(Names.named(HostSystem.NAMESPACE_SUPPORT)).to(true);
    }
}
