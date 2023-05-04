package org.apache.james;

import javax.inject.Inject;

import org.apache.james.imapserver.netty.IMAPServerFactory;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.webadmin.Routes;

import com.google.common.base.Preconditions;

import spark.Service;

public class ImapRoutes implements Routes {
    private final IMAPServerFactory imapServerFactory;
    private final ConfigurationProvider configurationProvider;

    @Inject
    public ImapRoutes(IMAPServerFactory imapServerFactory, ConfigurationProvider configurationProvider) {
        this.imapServerFactory = imapServerFactory;
        this.configurationProvider = configurationProvider;
    }

    @Override
    public String getBasePath() {
        return "imap";
    }

    @Override
    public void define(Service service) {
        service.post("imap", (req, res) -> {
            Preconditions.checkArgument(req.queryParams().contains("restart"), "'restart' query parameter shall be specified");

            imapServerFactory.destroy();
            imapServerFactory.configure(configurationProvider.getConfiguration("imapserver"));
            imapServerFactory.init();

            return "";
        });
    }
}
