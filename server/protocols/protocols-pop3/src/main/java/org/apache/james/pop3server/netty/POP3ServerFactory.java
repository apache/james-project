package org.apache.james.pop3server.netty;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.jboss.netty.util.HashedWheelTimer;

public class POP3ServerFactory extends AbstractServerFactory {

    private ProtocolHandlerLoader loader;
    private FileSystem fileSystem;
    private HashedWheelTimer hashedWheelTimer;

    @Inject
    public void setProtocolHandlerLoader(ProtocolHandlerLoader loader) {
        this.loader = loader;
    }

    @Inject
    public final void setFileSystem(FileSystem filesystem) {
        this.fileSystem = filesystem;
    }

    @Inject
    public void setHashedWheelTimer(HashedWheelTimer hashedWheelTimer) {
        this.hashedWheelTimer = hashedWheelTimer;
    }

    protected POP3Server createServer() {
       return new POP3Server();
    }
    
    @Override
    protected List<AbstractConfigurableAsyncServer> createServers(HierarchicalConfiguration config) throws Exception {

        List<AbstractConfigurableAsyncServer> servers = new ArrayList<>();
        List<HierarchicalConfiguration> configs = config.configurationsAt("pop3server");
        
        for (HierarchicalConfiguration serverConfig: configs) {
            POP3Server server = createServer();
            server.setProtocolHandlerLoader(loader);
            server.setFileSystem(fileSystem);
            server.setHashWheelTimer(hashedWheelTimer);
            server.configure(serverConfig);
            servers.add(server);
        }

        return servers;
        
    }
    
}
