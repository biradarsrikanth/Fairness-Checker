package com.example.fairnesstracker.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Requests Tomcat refuses before Spring sees them (a URI it can't decode, characters a URI can't hold) get the
 * same problem-details body as every other error, not Tomcat's HTML page. Sent over a raw socket, since HTTP
 * clients refuse to send such URIs at all.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TomcatErrorReportTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void answersAnUndecodableUriWithProblemDetails() throws IOException {
        Response response = send("GET /api/v1/teams/%zz HTTP/1.1");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.contentType()).startsWith("application/problem+json");
        JsonNode body = jsonMapper.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(400);
        assertThat(body.get("code").asString()).isEqualTo("MALFORMED_REQUEST");
        assertThat(body.get("title").asString()).isEqualTo("Bad Request");
        assertThat(body.get("type").asString()).isEqualTo("about:blank");
        assertThat(body.get("errors").isArray()).isTrue();
        assertThat(body.get("timestamp").asString()).isNotBlank();
        assertThat(response.body()).doesNotContain("<html").doesNotContain("Apache Tomcat");
    }

    @Test
    void answersCharactersAUriCantHoldWithProblemDetails() throws IOException {
        Response response = send("GET /api/v1/teams?name=a|b{c} HTTP/1.1");
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    void leavesTheApplicationsOwnErrorsAlone() throws IOException {
        Response response = send("GET /api/v1/teams HTTP/1.1");
        assertThat(response.status()).isEqualTo(401);
        assertThat(jsonMapper.readTree(response.body()).get("code").asString()).isEqualTo("UNAUTHENTICATED");
    }

    private record Response(int status, String contentType, String body) {
    }

    private Response send(String requestLine) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            out.write((requestLine + "\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            InputStream in = socket.getInputStream();
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            int split = raw.indexOf("\r\n\r\n");
            String head = raw.substring(0, split);
            String body = raw.substring(split + 4);
            int status = Integer.parseInt(head.split(" ")[1]);
            String contentType = head.lines()
                    .filter(line -> line.toLowerCase().startsWith("content-type:"))
                    .map(line -> line.substring("content-type:".length()).strip())
                    .findFirst().orElse("");
            if (head.toLowerCase().contains("transfer-encoding: chunked")) {
                body = unchunk(body);
            }
            return new Response(status, contentType, body);
        }
    }

    private static String unchunk(String chunked) {
        StringBuilder out = new StringBuilder();
        int at = 0;
        while (at < chunked.length()) {
            int lineEnd = chunked.indexOf("\r\n", at);
            if (lineEnd < 0) {
                break;
            }
            int size = Integer.parseInt(chunked.substring(at, lineEnd).strip(), 16);
            if (size == 0) {
                break;
            }
            out.append(chunked, lineEnd + 2, lineEnd + 2 + size);
            at = lineEnd + 2 + size + 2;
        }
        return out.toString();
    }
}
