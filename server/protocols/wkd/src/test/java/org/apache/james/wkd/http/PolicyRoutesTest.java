package org.apache.james.wkd.http;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import reactor.netty.http.server.HttpServerResponse;

class PolicyRoutesTest {

	@Test
	void test() {
		PolicyRoutes policyRoute = new PolicyRoutes();
		HttpServerResponse response = mock(HttpServerResponse.class);
		when(response.header(CONTENT_TYPE, "text/plain")).thenReturn(response);
		when(response.status(OK)).thenReturn(response);
		when(response.sendString(any())).thenReturn(response);

		policyRoute.get(null, response);
		
		verify(response).header(CONTENT_TYPE, "text/plain");
	}

}
