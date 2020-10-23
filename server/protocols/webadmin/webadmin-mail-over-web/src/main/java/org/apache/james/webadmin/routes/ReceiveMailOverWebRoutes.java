package org.apache.james.webadmin.routes;

import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.server.core.MailImpl;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.request.MailProps;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonExtractor;
import org.eclipse.jetty.http.HttpStatus;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import spark.Service;

@Api(tags = "OverWebMailReceiver")
@Path(ReceiveMailOverWebRoutes.BASE_URL)
@Produces("application/json")
public class ReceiveMailOverWebRoutes implements Routes {

    private final JsonExtractor<MailProps> mailPropsJsonExtractor;

    public static final String BASE_URL = "/receiveMail";

    private MailQueue queue;

    @Override
    public String getBasePath() {
        return BASE_URL;
    }

    @Inject
    public ReceiveMailOverWebRoutes(MailQueueFactory<?> queueFactory) {
        queue = queueFactory.createQueue(MailQueueFactory.SPOOL);
        this.mailPropsJsonExtractor = new JsonExtractor<>(MailProps.class);
    }

    @Override
    public void define(Service service) {
        defineReceiveMailFromWebService(service);
    }

    @POST
    @Path("/receiveMail")
    @ApiOperation(value = "Deleting an user")
    @ApiImplicitParams({
            @ApiImplicitParam(required = true, dataType = "string", name = "username", paramType = "path")
    })
    @ApiResponses(value = {
            @ApiResponse(code = HttpStatus.NO_CONTENT_204, message = "OK. User is removed."),
            @ApiResponse(code = HttpStatus.BAD_REQUEST_400, message = "Invalid input user."),
            @ApiResponse(code = HttpStatus.INTERNAL_SERVER_ERROR_500,
                    message = "Internal server error - Something went bad on the server side.")
    })
    public void defineReceiveMailFromWebService(Service service) {
        service.post(BASE_URL, (request, response) -> {
            try {
                //Parse the json object to org.apache.james.webadmin.request.MailProps and build the MailImpl object
                MailImpl mail = mailPropsJsonExtractor.parse(request.body()).asMailImpl();
                //Send to queue api for mail processing
                queue.enQueue(mail);
                response.body("ENQUEUED");
                response.status(204);
            } catch (Exception e) {
                ErrorResponder.builder()
                        .cause(e)
                        .statusCode(500)
                        .type(ErrorResponder.ErrorType.SERVER_ERROR)
                        .message("The mail will not be sent: " + e.getMessage())
                        .haltError();
            }

            return response.body();
        });
    }

}
