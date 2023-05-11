package org.apache.james.protocols.lib.webadmin;

import org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer;
import org.apache.james.protocols.lib.netty.AbstractServerFactory;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.Responses;
import org.eclipse.jetty.http.HttpStatus;

import com.google.common.base.Preconditions;

import spark.Service;

public abstract class AbstractServerRoutes
        implements Routes {

    protected AbstractServerFactory serverFactory;

    @Override
    public void define(Service service) {
        service.post(getBasePath(), (request, response) -> {
            Preconditions.checkArgument(request.queryParams().contains("reload-certificate"),
                    "'reload-certificate' query parameter shall be specified");

            if (serverFactory.getServers() == null
                    || serverFactory.getServers().isEmpty()
                    || serverFactory.getServers().stream().noneMatch(AbstractConfigurableAsyncServer::isEnabled)) {
                return ErrorResponder.builder()
                        .statusCode(HttpStatus.BAD_REQUEST_400)
                        .type(ErrorResponder.ErrorType.NOT_FOUND)
                        .message("No servers configured, nothing to reload")
                        .haltError();
            }

            for (AbstractConfigurableAsyncServer server : serverFactory.getServers()) {
                server.reloadSSLCertificate();
            }

            return Responses.returnNoContent(response);
        });
    }
}