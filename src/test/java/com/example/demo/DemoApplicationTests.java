package com.example.demo;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.test.StepVerifier;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class DemoApplicationTests {

	@Rule
	public WireMockRule wireMockRule = new WireMockRule(9005);

	@Test
	public void contextLoads() {
	}

	private static final String ENDPOINT = "/testEndpoint/";

	@Test
	public void reproducesIssue() {

            stubFor(get(ENDPOINT).willReturn(
                        aResponse()
                        .withBody("") // Response with empty body content
                        .withStatus(500)));

            StepVerifier.create(makeRequest())
                .expectError()
                .verify();

	}

	@Test
	public void doesNotReproduceIssue() {

            stubFor(get(ENDPOINT).willReturn(
                        aResponse()
                        .withBody("c") // Response with at least 1 character of body content
                        .withStatus(500)));

            StepVerifier.create(makeRequest())
                .expectError()
                .verify();

	}

        Flux<String> makeRequest() {
                return WebClient.create().get()
                        .uri("http://localhost:9005" + ENDPOINT)
                        .retrieve()
                        .onStatus(this::isNotSuccessful, this::constructWebClientResponseExceptionMono)
                        .bodyToFlux(String.class);
        }

        private boolean isNotSuccessful(final HttpStatus status) {
            System.out.println("isNotSuccessful");
            return !status.is2xxSuccessful();
        }


        Mono<WebClientResponseException> constructWebClientResponseExceptionMono(final ClientResponse response) {
            System.out.println("constructWebClientResponseExceptionMono");
            final HttpStatus statusCode = response.statusCode();
            final int statusCodeValue = statusCode.value();
            final Charset responseCharset = response.headers().contentType()
                .map(MimeType::getCharset)
                .orElse(StandardCharsets.ISO_8859_1);
            Mono<WebClientResponseException> retMono = response.bodyToMono(byte[].class).map(
                    bodyBytes -> {
                        System.out.println("Actually constructing WebClientResponseException with bodyBytes");
                        return new WebClientResponseException(String.format("Unexpected POST response status code observed from %s: %d, reason phrase: %s.", ENDPOINT, statusCodeValue, statusCode.getReasonPhrase()),
                                statusCodeValue,
                                statusCode.getReasonPhrase(),
                                response.headers().asHttpHeaders(),
                                bodyBytes,
                                responseCharset);
                    });

            return retMono;
        }
}
