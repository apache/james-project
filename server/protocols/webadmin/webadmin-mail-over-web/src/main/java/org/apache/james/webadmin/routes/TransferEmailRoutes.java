package org.apache.james.webadmin.routes;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import javax.inject.Inject;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.james.webadmin.Routes;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Service;

@Api(tags = "OverWebMailReceiver")
@Path(TransferEmailRoutes.BASE_URL)
@Produces("application/json")
public class TransferEmailRoutes implements Routes {

    public static final String BASE_URL = "/mail-transfer-service";

    private MailQueue queue;

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    @Inject
    public TransferEmailRoutes(MailQueueFactory<?> queueFactory) {
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
    }

    @Override
    public void define(Service service) {
        defineReceiveMailFromWebService(service);
    }

    @POST
    @Path("/mail-transfer-service")
    @ApiOperation(value = "Receiving a message/rfc822 over REST interface")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.CREATED_201, message = ""),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Could not ceate mail from supplied body"),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                    message = "Internal server error - Something went bad on the server side.")
    })
    public void defineReceiveMailFromWebService(Service service) {
        service.post(BASE_URL, (request, response) -> {
            //parse MimeMessage from request body
            MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties()), new ByteArrayInputStream(request.bodyAsBytes()));
            //create MailImpl object from MimeMessage
            MailImpl mail = MailImpl.fromMimeMessage(UUID.randomUUID().toString(), mimeMessage);
            //Send to queue api for mail processing
            queue.enQueue(mail);
            response.body("");
            response.status(201);

            return response.body();
        });
    }

}
