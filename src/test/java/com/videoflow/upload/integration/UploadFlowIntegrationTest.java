package com.videoflow.upload.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

class UploadFlowIntegrationTest extends IntegrationTestBase {

    private static final int PART_SIZE = 5_242_880;
    private static final long FILE_SIZE = 5_500_000L;
    private static final String CREATE_BODY = """
            {"fileName":"clip.mp4","contentType":"video/mp4","size":%d}
            """.formatted(FILE_SIZE);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void prepareBucket() {
        ensureBucket();
    }

    @Test
    void happyPathUploadsAllPartsAndCompletes() throws Exception {
        JsonNode created = createUpload(USER_A, CREATE_BODY, HttpStatus.CREATED);
        String uploadId = created.get("uploadId").asText();
        assertEquals(2, created.get("totalParts").asInt());
        assertEquals(PART_SIZE, created.get("partSize").asInt());
        assertEquals("CREATED", created.get("status").asText());

        JsonNode presigned = presignParts(USER_A, uploadId, "{\"partNumbers\":[1,2]}");
        assertEquals(2, presigned.get("parts").size());
        String url1 = firstUploadUrl(presigned, 1);
        String url2 = firstUploadUrl(presigned, 2);

        String etag1 = putPart(url1, new byte[PART_SIZE]);
        String etag2 = putPart(url2, new byte[(int) (FILE_SIZE - PART_SIZE)]);

        ResponseEntity<String> ack = rest.exchange(url("/v1/uploads/" + uploadId + "/parts/1/ack"),
                org.springframework.http.HttpMethod.POST,
                jsonRequest(USER_A, "{\"etag\":\"" + etag1 + "\"," + "\"size\":" + PART_SIZE + "}"),
                String.class);
        assertEquals(200, ack.getStatusCode().value());

        ResponseEntity<String> complete = rest.exchange(url("/v1/uploads/" + uploadId + "/complete"),
                org.springframework.http.HttpMethod.POST,
                jsonRequest(USER_A, completeBody(etag1, etag2)),
                String.class);
        assertEquals(200, complete.getStatusCode().value());
        JsonNode completed = mapper.readTree(complete.getBody());
        assertEquals("COMPLETED", completed.get("status").asText());
        assertEquals(2, completed.get("uploadedParts").asInt());

        JsonNode fetched = getUpload(USER_A, uploadId);
        assertEquals("COMPLETED", fetched.get("status").asText());
        assertEquals(2, fetched.get("uploadedParts").asInt());

        HeadObjectResponse head = storageClient.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET)
                .key("videos/" + uploadId + "/original.mp4")
                .build());
        assertEquals(FILE_SIZE, head.contentLength());
    }

    @Test
    void idempotencyKeyReplayReturnsExistingUpload() {
        String body = """
                {"fileName":"clip.mp4","contentType":"video/mp4","size":1000000}
                """;
        HttpHeadersWithKey request = headersWithKey(USER_A, "replay-1");

        ResponseEntity<String> first = rest.postForEntity(url("/v1/uploads"), jsonWithHeaders(body, request), String.class);
        assertEquals(201, first.getStatusCode().value());
        String uploadId = first.getBody() == null ? null : parse(first.getBody(), "uploadId");

        ResponseEntity<String> second = rest.postForEntity(url("/v1/uploads"), jsonWithHeaders(body, request), String.class);
        assertEquals(200, second.getStatusCode().value());
        assertEquals(uploadId, parse(second.getBody(), "uploadId"));
    }

    @Test
    void outOfRangePartIsRejectedWhenPresigning() {
        JsonNode created = createUpload(USER_A, CREATE_BODY, HttpStatus.CREATED);
        String uploadId = created.get("uploadId").asText();
        ResponseEntity<String> res = rest.exchange(url("/v1/uploads/" + uploadId + "/parts"),
                org.springframework.http.HttpMethod.POST,
                jsonRequest(USER_A, "{\"partNumbers\":[99,1]}"), String.class);
        assertEquals(400, res.getStatusCode().value());
        assertEquals("INVALID_PART", parse(res.getBody(), "code"));
    }

    @Test
    void anotherUserCannotPresignOrComplete() {
        JsonNode created = createUpload(USER_A, CREATE_BODY, HttpStatus.CREATED);
        String uploadId = created.get("uploadId").asText();

        ResponseEntity<String> parts = rest.exchange(url("/v1/uploads/" + uploadId + "/parts"),
                org.springframework.http.HttpMethod.POST,
                jsonRequest(USER_B, "{\"partNumbers\":[1]}"), String.class);
        assertEquals(403, parts.getStatusCode().value());
        assertEquals("UNAUTHORIZED_UPLOAD", parse(parts.getBody(), "code"));

        ResponseEntity<String> complete = rest.exchange(url("/v1/uploads/" + uploadId + "/complete"),
                org.springframework.http.HttpMethod.POST,
                jsonRequest(USER_B, "{\"parts\":[{\"partNumber\":1,\"etag\":\"e1\"}]}"), String.class);
        assertEquals(403, complete.getStatusCode().value());
    }

    @Test
    void cancelledUploadCannotBeCompleted() {
        JsonNode created = createUpload(USER_A, CREATE_BODY, HttpStatus.CREATED);
        String uploadId = created.get("uploadId").asText();

        ResponseEntity<String> cancel = rest.exchange(url("/v1/uploads/" + uploadId),
                org.springframework.http.HttpMethod.DELETE, jsonRequest(USER_A, ""), String.class);
        assertEquals(200, cancel.getStatusCode().value());
        assertEquals("CANCELLED", parse(cancel.getBody(), "status"));

        ResponseEntity<String> complete = rest.exchange(url("/v1/uploads/" + uploadId + "/complete"),
                org.springframework.http.HttpMethod.POST,
                jsonRequest(USER_A, completeBody("e1", "e2")), String.class);
        assertEquals(409, complete.getStatusCode().value());
        assertEquals("INVALID_UPLOAD_STATE", parse(complete.getBody(), "code"));
    }

    @Test
    void rejectsUnsupportedContentTypeAndOversizedFile() {
        ResponseEntity<String> badType = rest.postForEntity(url("/v1/uploads"),
                jsonRequest(USER_A, "{\"fileName\":\"a.txt\",\"contentType\":\"text/plain\",\"size\":100}"),
                String.class);
        assertEquals(400, badType.getStatusCode().value());
        assertEquals("INVALID_FILE_TYPE", parse(badType.getBody(), "code"));

        ResponseEntity<String> tooBig = rest.postForEntity(url("/v1/uploads"),
                jsonRequest(USER_A,
                        "{\"fileName\":\"a.mp4\",\"contentType\":\"video/mp4\",\"size\":6000000000}"),
                String.class);
        assertEquals(413, tooBig.getStatusCode().value());
        assertEquals("FILE_TOO_LARGE", parse(tooBig.getBody(), "code"));
    }

    @Test
    void missingIdentityHeaderReturnsUnauthorized() {
        ResponseEntity<String> res = rest.postForEntity(url("/v1/uploads"),
                jsonRequestNoAuth(CREATE_BODY), String.class);
        assertEquals(401, res.getStatusCode().value());
    }

    private JsonNode createUpload(java.util.UUID user, String body, HttpStatus expected) {
        ResponseEntity<String> res = rest.postForEntity(url("/v1/uploads"), jsonRequest(user, body), String.class);
        assertEquals(expected.value(), res.getStatusCode().value(), "unexpected status for create");
        return parseTree(res.getBody());
    }

    private JsonNode presignParts(java.util.UUID user, String uploadId, String partNumbers) {
        ResponseEntity<String> res = rest.exchange(url("/v1/uploads/" + uploadId + "/parts"),
                org.springframework.http.HttpMethod.POST, jsonRequest(user, partNumbers), String.class);
        assertEquals(200, res.getStatusCode().value());
        return parseTree(res.getBody());
    }

    private JsonNode getUpload(java.util.UUID user, String uploadId) {
        ResponseEntity<String> res = rest.exchange(url("/v1/uploads/" + uploadId),
                org.springframework.http.HttpMethod.GET, jsonRequest(user, ""), String.class);
        assertEquals(200, res.getStatusCode().value());
        return parseTree(res.getBody());
    }

    private String firstUploadUrl(JsonNode presigned, int partNumber) {
        for (JsonNode part : presigned.get("parts")) {
            if (part.get("partNumber").asInt() == partNumber) {
                return part.get("uploadUrl").asText();
            }
        }
        throw new AssertionError("no url for part " + partNumber);
    }

    private String putPart(String uploadUrl, byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uploadUrl))
                .timeout(Duration.ofSeconds(30))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode(), "PUT part failed: " + response.statusCode());
        return response.headers().firstValue("etag").orElseThrow(() -> new AssertionError("no etag"));
    }

    private String completeBody(String etag1, String etag2) {
        return "{\"parts\":[" +
                "{\"partNumber\":1,\"etag\":\"" + etag1 + "\"}," +
                "{\"partNumber\":2,\"etag\":\"" + etag2 + "\"}" +
                "]}";
    }

    private JsonNode parseTree(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception ex) {
            throw new AssertionError("bad json: " + body, ex);
        }
    }

    private String parse(String body, String field) {
        JsonNode node = parseTree(body);
        JsonNode value = node.get(field);
        assertNotNull(value, "missing field " + field + " in " + body);
        return value.asText();
    }

    private HttpHeadersWithKey headersWithKey(java.util.UUID user, String key) {
        return new HttpHeadersWithKey(user, key);
    }

    private HttpEntity<String> jsonWithHeaders(String body, HttpHeadersWithKey headers) {
        org.springframework.http.HttpHeaders h = authHeaders(headers.user);
        h.set("Idempotency-Key", headers.key);
        return new HttpEntity<>(body, h);
    }

    private record HttpHeadersWithKey(java.util.UUID user, String key) {
    }
}