package com.example.student;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:campus;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.hikari.schema=PUBLIC",
        "spring.datasource.hikari.connection-init-sql=CREATE SCHEMA IF NOT EXISTS PUBLIC",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class InterfaceIntegrationTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        request.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String field(String body, String name) {
        var matcher = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), body);
        return matcher.group(1);
    }
    private long id(String body) {
        var matcher = Pattern.compile("\"id\":(\\d+)").matcher(body);
        assertTrue(matcher.find(), body);
        return Long.parseLong(matcher.group(1));
    }
    private String login(String email, String password) throws Exception {
        var r = send("POST", "/api/v1/auth/login", null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        return field(r.body(), "token");
    }

    @Test void loginPageAndAssetsArePublicWhileApiRequiresAuthentication() throws Exception {
        var page = send("GET", "/", null, null);
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("Кампус"));
        assertEquals(200, send("GET", "/assets/app.js", null, null).statusCode());
        assertEquals(200, send("GET", "/assets/styles.css", null, null).statusCode());
        assertEquals(401, send("GET", "/api/v1/student", null, null).statusCode());
        assertEquals(401, send("GET", "/api/v1/student", "invalid-token", null).statusCode());
        assertEquals(401, send("POST", "/api/v1/auth/login", null,
                "{\"email\":\"teacher@example.com\",\"password\":\"wrong\"}").statusCode());
    }

    @Test void rolesAndStudentLifecycleMatchTheInterface() throws Exception {
        String teacher = login("teacher@example.com", "teacher");
        String admin = login("admin@example.com", "admin");
        String superadmin = login("superadmin@example.com", "superAdmin");
        assertEquals(200, send("GET", "/api/v1/student", teacher, null).statusCode());
        assertEquals(403, send("GET", "/api/v1/users", admin, null).statusCode());
        assertEquals(403, send("GET", "/api/v1/student", superadmin, null).statusCode());
        String student = "{\"name\":\"Anna\",\"surname\":\"Test\",\"email\":\"anna@example.com\",\"phone_number\":\"123\"}";
        assertEquals(403, send("POST", "/api/v1/student/save", teacher, student).statusCode());
        assertEquals(400, send("POST", "/api/v1/student/save", admin,
                "{\"name\":\"\",\"surname\":\"Test\"}").statusCode());
        var created = send("POST", "/api/v1/student/save", admin, student);
        assertEquals(200, created.statusCode(), created.body());
        long id = id(created.body());
        var detail = send("GET", "/api/v1/student/" + id, teacher, null);
        assertEquals(200, detail.statusCode());
        assertTrue(detail.body().contains("anna@example.com"));
        assertEquals(200, send("PUT", "/api/v1/student/" + id, admin,
                "{\"email\":\"changed@example.com\",\"phoneNumber\":\"456\"}").statusCode());
        assertTrue(send("GET", "/api/v1/student/" + id, teacher, null).body().contains("changed@example.com"));
        assertEquals(200, send("DELETE", "/api/v1/student/" + id, admin, null).statusCode());
        assertEquals(404, send("GET", "/api/v1/student/" + id, admin, null).statusCode());
    }

    @Test void userUpdateUsesIdReturnsDataAndKeepsAnUnchangedPassword() throws Exception {
        String token = login("superadmin@example.com", "superAdmin");
        String user = "{\"email\":\"integration@example.com\",\"password\":\"test-pass-123\",\"roles\":[\"TEACHER\"]}";
        var created = send("POST", "/api/v1/users/save", token, user);
        assertEquals(200, created.statusCode(), created.body());
        long id = id(created.body());
        assertEquals(409, send("POST", "/api/v1/users/save", token, user).statusCode());
        var updated = send("PUT", "/api/v1/users/" + id, token,
                "{\"email\":\"updated@example.com\",\"password\":\"\",\"roles\":[\"ADMIN\"]}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(updated.body().contains("updated@example.com"));
        assertEquals(id, id(updated.body()));
        String updatedToken = login("updated@example.com", "test-pass-123");
        assertEquals(200, send("GET", "/api/v1/student", updatedToken, null).statusCode());
        assertEquals(200, send("DELETE", "/api/v1/users/" + id, token, null).statusCode());
    }

    @Test void requestValidationAndMalformedJsonProduceBadRequest() throws Exception {
        String admin = login("admin@example.com", "admin");
        String superadmin = login("superadmin@example.com", "superAdmin");
        assertEquals(400, send("POST", "/api/v1/auth/login", null,
                "{\"email\":\"invalid\",\"password\":\"\"}").statusCode());
        assertEquals(400, send("POST", "/api/v1/student/save", admin, "{broken").statusCode());
        assertEquals(400, send("POST", "/api/v1/users/save", superadmin,
                "{\"email\":\"valid@example.com\",\"password\":\"secret123\",\"roles\":[]}").statusCode());
        assertEquals(400, send("POST", "/api/v1/users/save", superadmin,
                "{\"email\":\"valid@example.com\",\"password\":\"short\",\"roles\":[\"TEACHER\"]}").statusCode());
        assertEquals(400, send("POST", "/api/v1/users/save", superadmin,
                "{\"email\":\"valid@example.com\",\"password\":\"secret123\",\"roles\":[\"UNKNOWN\"]}").statusCode());
    }

    @Test void additionalReadRoutesAndMissingUserAreHandled() throws Exception {
        String teacher = login("teacher@example.com", "teacher");
        String superadmin = login("superadmin@example.com", "superAdmin");
        var users = send("GET", "/api/v1/users", superadmin, null);
        assertEquals(200, users.statusCode());
        assertTrue(users.body().contains("superadmin@example.com"));
        assertFalse(users.body().contains("password"));
        String admin = login("admin@example.com", "admin");
        var created = send("POST", "/api/v1/student/save", admin,
                "{\"name\":\"Alias\",\"surname\":\"Route\"}");
        assertEquals(200,created.statusCode());
        long id = id(created.body());
        var direct = send("GET", "/api/v1/student/" + id, teacher, null);
        var alias = send("GET", "/api/v1/student/info/" + id, teacher, null);
        assertEquals(200,alias.statusCode());assertEquals(direct.body(),alias.body());
        assertEquals(404, send("PUT", "/api/v1/users/99999999", superadmin,
                "{\"email\":\"nobody@example.com\",\"roles\":[\"TEACHER\"]}").statusCode());
        send("DELETE", "/api/v1/student/" + id, admin, null);
    }
}
