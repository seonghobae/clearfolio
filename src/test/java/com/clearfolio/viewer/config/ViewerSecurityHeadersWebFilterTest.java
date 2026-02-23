package com.clearfolio.viewer.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.RequestPath;

import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.concurrent.atomic.AtomicBoolean;

class ViewerSecurityHeadersWebFilterTest {

    @Test
    void addsSecurityHeadersForViewerSurface() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("self");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/viewer/index.html").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            webExchange.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("no-store", headers.getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals("nosniff", headers.getFirst("X-Content-Type-Options"));
        assertEquals("no-referrer", headers.getFirst("Referrer-Policy"));

        String csp = headers.getFirst("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'none'"));
        assertTrue(csp.contains("frame-ancestors 'self'"));
        assertTrue(csp.contains("worker-src 'self' blob:"));
    }

    @Test
    void doesNotSetCspWhenResponseIsRedirect() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("self");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/viewer/" + java.util.UUID.randomUUID()).build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.FOUND);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertEquals("no-store", headers.getFirst(HttpHeaders.CACHE_CONTROL));
        assertNull(headers.getFirst("Content-Security-Policy"));
    }

    @Test
    void addsSecurityHeadersWhenPathIsExactViewer() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("self");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/viewer").build()
        );

        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            webExchange.getResponse().getHeaders().setContentType(MediaType.TEXT_HTML);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        String csp = exchange.getResponse().getHeaders().getFirst("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("frame-ancestors 'self'"));
    }

    @Test
    void normalizesFrameAncestorsForNullAndBlank() {
        ViewerSecurityHeadersWebFilter[] filters = {
                new ViewerSecurityHeadersWebFilter(null),
                new ViewerSecurityHeadersWebFilter("   ")
        };

        for (ViewerSecurityHeadersWebFilter filter : filters) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("/viewer/index.html").build()
            );
            WebFilterChain chain = webExchange -> {
                webExchange.getResponse().setStatusCode(HttpStatus.OK);
                return webExchange.getResponse().setComplete();
            };

            filter.filter(exchange, chain).block();
            String csp = exchange.getResponse().getHeaders().getFirst("Content-Security-Policy");
            assertNotNull(csp);
            assertTrue(csp.contains("frame-ancestors 'self'"));
        }
    }

    @Test
    void usesCustomFrameAncestorsValueWhenConfigured() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("https://example.test");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/viewer/index.html").build()
        );
        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        String csp = exchange.getResponse().getHeaders().getFirst("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("frame-ancestors https://example.test"));
    }

    @Test
    void supportsHeadRequestsForViewerSurface() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("self");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.HEAD, "/viewer/index.html").build()
        );
        WebFilterChain chain = webExchange -> {
            webExchange.getResponse().setStatusCode(HttpStatus.OK);
            return webExchange.getResponse().setComplete();
        };

        filter.filter(exchange, chain).block();

        assertNotNull(exchange.getResponse().getHeaders().getFirst("Content-Security-Policy"));
    }

    @Test
    void treatsBlankOrNullPathAsNonViewerSurface() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("self");
        AtomicBoolean invoked = new AtomicBoolean(false);
        WebFilterChain chain = webExchange -> {
            invoked.set(true);
            return Mono.empty();
        };

        ServerWebExchange blankPath = mock(ServerWebExchange.class);
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        RequestPath path = mock(RequestPath.class);
        when(blankPath.getRequest()).thenReturn(request);
        when(request.getPath()).thenReturn(path);
        when(path.value()).thenReturn("   ");
        when(blankPath.getResponse()).thenReturn(new MockServerHttpResponse());

        filter.filter(blankPath, chain).block();
        assertTrue(invoked.get());

        invoked.set(false);
        ServerWebExchange nullPath = mock(ServerWebExchange.class);
        ServerHttpRequest request2 = mock(ServerHttpRequest.class);
        RequestPath path2 = mock(RequestPath.class);
        when(nullPath.getRequest()).thenReturn(request2);
        when(request2.getPath()).thenReturn(path2);
        when(path2.value()).thenReturn(null);
        when(nullPath.getResponse()).thenReturn(new MockServerHttpResponse());

        filter.filter(nullPath, chain).block();
        assertTrue(invoked.get());
    }

    @Test
    void doesNotAddHeadersForNonViewerPaths() {
        ViewerSecurityHeadersWebFilter filter = new ViewerSecurityHeadersWebFilter("self");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/convert/jobs/123").build()
        );

        WebFilterChain chain = webExchange -> webExchange.getResponse().setComplete();

        filter.filter(exchange, chain).block();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertNull(headers.getFirst("Content-Security-Policy"));
        assertNull(headers.getFirst("X-Content-Type-Options"));
        assertNull(headers.getFirst("Referrer-Policy"));
    }
}
