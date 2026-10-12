// Guardar en: src/test/java/com/enterprise/tacticalsim/modules/mirror/websocket/MirrorWebSocketIntegrationTest.java
package com.enterprise.tacticalsim.modules.mirror.websocket;

import com.enterprise.tacticalsim.modules.mirror.config.MirrorProperties;
import com.enterprise.tacticalsim.modules.mirror.config.MirrorWebSocketConfig;
import com.enterprise.tacticalsim.modules.mirror.controller.MirrorController;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de integracion del relevo de duplicacion de pantalla: servidor real
 * en puerto aleatorio y clientes WebSocket reales (StandardWebSocketClient).
 *
 * Es AUTOCONTENIDA: la configuracion anidada importa solo las clases del
 * modulo mirror y excluye base de datos, JPA y seguridad, asi que no depende
 * del resto del proyecto. El reloj es controlable para probar la expiracion
 * y la tasa de frames sin esperar tiempo real.
 */
@SpringBootTest(
        classes = MirrorWebSocketIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "mirror.room-ttl-minutes=240",
                "mirror.max-viewers-per-room=2",
                "mirror.max-frame-bytes=1024",
                "mirror.max-frames-per-second=5",
                "mirror.max-rooms=40",
                // Heartbeat y barrido casi desactivados: las pruebas los invocan a mano.
                "mirror.heartbeat-millis=3600000",
                "mirror.cleanup-millis=3600000",
                "cors.allowed-origins=http://localhost:3000"
        })
class MirrorWebSocketIntegrationTest {

    @Configuration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration",
            "org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
            "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
    })
    @Import({
            MirrorWebSocketConfig.class,
            MirrorProperties.class,
            MirrorRoomService.class,
            MirrorWebSocketHandler.class,
            MirrorHandshakeInterceptor.class,
            MirrorController.class
    })
    static class TestApp {
        @Bean
        MutableClock mirrorTestClock() {
            return new MutableClock();
        }
    }

    /** Reloj que se mueve solo cuando la prueba lo ordena. */
    static final class MutableClock extends Clock {
        static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
        private volatile Instant now = START;

        void reset() {
            now = START;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Cliente WebSocket de prueba que acumula lo que recibe. */
    static final class TestClient extends TextWebSocketHandler {
        final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);
        volatile CloseStatus closeStatus;
        volatile WebSocketSession session;

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) {
            inbox.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closeStatus = status;
            closed.countDown();
        }

        String next() throws InterruptedException {
            return inbox.poll(3, TimeUnit.SECONDS);
        }

        JsonNode nextJson() throws Exception {
            String text = next();
            assertThat(text).as("se esperaba un mensaje del servidor").isNotNull();
            return MAPPER.readTree(text);
        }

        void expectSilence() throws InterruptedException {
            assertThat(inbox.poll(400, TimeUnit.MILLISECONDS)).as("no se esperaba ningun mensaje").isNull();
        }

        void drain() throws InterruptedException {
            while (inbox.poll(250, TimeUnit.MILLISECONDS) != null) {
                // descarta lo pendiente
            }
        }

        void send(String text) throws IOException {
            session.sendMessage(new TextMessage(text));
        }

        boolean awaitClosed() throws InterruptedException {
            return closed.await(3, TimeUnit.SECONDS);
        }

        void closeQuietly() {
            try {
                if (session != null && session.isOpen()) {
                    session.close();
                }
            } catch (IOException ignored) {
                // nada que hacer
            }
        }
    }

    private record Room(String code, String key) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MirrorRoomService roomService;

    @Autowired
    private MutableClock clock;

    private final List<TestClient> opened = new ArrayList<>();

    @BeforeEach
    void reiniciarReloj() {
        clock.reset();
        for (TestClient client : opened) {
            client.closeQuietly();
        }
        opened.clear();
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Room createRoom() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/mirror/rooms", null, JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode data = response.getBody().get("data");
        return new Room(data.get("roomCode").asText(), data.get("emitterKey").asText());
    }

    private String url(String code, String key) {
        return "/ws/mirror" + (code == null ? "" : "?room=" + code) + (key == null ? "" : "&key=" + key);
    }

    private TestClient connect(String code, String key, String origin) throws Exception {
        TestClient client = new TestClient();
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        client.session = new StandardWebSocketClient()
                .execute(client, headers, URI.create("ws://localhost:" + port + url(code, key)))
                .get(5, TimeUnit.SECONDS);
        opened.add(client);
        return client;
    }

    private TestClient emitter(Room room) throws Exception {
        return connect(room.code(), room.key(), null);
    }

    private TestClient viewer(Room room) throws Exception {
        return connect(room.code(), null, null);
    }

    /** Hace un handshake "a mano" y devuelve el codigo HTTP (101 si se acepta). */
    private int handshakeStatus(String pathAndQuery, String origin) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(5000);
            String request = "GET " + pathAndQuery + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + (origin != null ? "Origin: " + origin + "\r\n" : "")
                    + "\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String statusLine = reader.readLine();
            assertThat(statusLine).startsWith("HTTP/1.1 ");
            return Integer.parseInt(statusLine.split(" ")[1]);
        }
    }

    private void expectType(TestClient client, String type) throws Exception {
        assertThat(client.nextJson().get("type").asText()).isEqualTo(type);
    }

    /** Lee mensajes hasta ver {"type":"viewers","count":N}. */
    private void awaitViewerCount(TestClient client, int count) throws Exception {
        for (int i = 0; i < 10; i++) {
            JsonNode json = client.nextJson();
            if ("viewers".equals(json.get("type").asText()) && json.get("count").asInt() == count) {
                return;
            }
        }
        throw new AssertionError("No llegó el aviso de " + count + " visores");
    }

    // ------------------------------------------------------------------
    // Pruebas
    // ------------------------------------------------------------------

    @Test
    void crearSalaPorRestDevuelveCodigoClaveYVigencia() {
        ResponseEntity<JsonNode> response = rest.postForEntity("/api/v1/mirror/rooms", null, JsonNode.class);
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode body = response.getBody();
        assertThat(body.get("message").asText()).isNotBlank();
        JsonNode data = body.get("data");
        assertThat(data.get("roomCode").asText()).matches("[A-HJKMNP-Z2-9]{6}");
        assertThat(data.get("emitterKey").asText()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(data.get("expiresInSeconds").asLong()).isEqualTo(14400L);
    }

    @Test
    void emisorYDosVisoresRecibenFramesIdenticos() throws Exception {
        Room room = createRoom();

        TestClient emitter = emitter(room);
        JsonNode emitterHello = emitter.nextJson();
        assertThat(emitterHello.get("type").asText()).isEqualTo("hello");
        assertThat(emitterHello.get("role").asText()).isEqualTo("emitter");
        assertThat(emitterHello.get("roomCode").asText()).isEqualTo(room.code());
        assertThat(emitterHello.get("viewers").asInt()).isZero();

        TestClient viewer1 = viewer(room);
        JsonNode hello1 = viewer1.nextJson();
        assertThat(hello1.get("type").asText()).isEqualTo("hello");
        assertThat(hello1.get("role").asText()).isEqualTo("viewer");
        assertThat(hello1.get("viewers").asInt()).isEqualTo(1);
        JsonNode status1 = viewer1.nextJson();
        assertThat(status1.get("type").asText()).isEqualTo("emitter");
        assertThat(status1.get("online").asBoolean()).isTrue();
        awaitViewerCount(emitter, 1);

        TestClient viewer2 = viewer(room);
        assertThat(viewer2.nextJson().get("viewers").asInt()).isEqualTo(2);
        expectType(viewer2, "emitter");
        awaitViewerCount(emitter, 2);
        awaitViewerCount(viewer1, 2);

        // Espacios y caracteres no ASCII a proposito: debe llegar el MISMO texto.
        String frame = "{\"type\":\"frame\",  \"strokes\":[{\"x\":1.5,\"y\":-2}], \"nombre\":\"pizarra ñ\" }";
        emitter.send(frame);
        assertThat(viewer1.next()).isEqualTo(frame);
        assertThat(viewer2.next()).isEqualTo(frame);
        emitter.expectSilence();
    }

    @Test
    void elCodigoDeSalaNoDistingueMayusculas() throws Exception {
        Room room = createRoom();
        TestClient viewer = connect(room.code().toLowerCase(), null, null);
        JsonNode hello = viewer.nextJson();
        assertThat(hello.get("roomCode").asText()).isEqualTo(room.code());
    }

    @Test
    void visorTardioRecibeElUltimoFrame() throws Exception {
        Room room = createRoom();
        TestClient emitter = emitter(room);
        TestClient early = viewer(room);
        early.drain();
        emitter.drain();

        String first = "{\"type\":\"frame\",\"n\":1}";
        String last = "{\"type\":\"frame\",\"n\":2}";
        emitter.send(first);
        emitter.send(last);
        assertThat(early.next()).isEqualTo(first);
        assertThat(early.next()).isEqualTo(last);

        TestClient late = viewer(room);
        expectType(late, "hello");
        expectType(late, "emitter");
        assertThat(late.next()).isEqualTo(last);
        late.expectSilence();
    }

    @Test
    void visorEsSoloLecturaYElSpamLoCierra() throws Exception {
        Room room = createRoom();
        TestClient emitter = emitter(room);
        TestClient viewer1 = viewer(room);
        TestClient viewer2 = viewer(room);
        emitter.drain();
        viewer1.drain();
        viewer2.drain();

        // Un frame enviado por un visor no llega a nadie.
        viewer1.send("{\"type\":\"frame\",\"hack\":true}");
        viewer2.expectSilence();
        emitter.expectSilence();

        // El pong se acepta y no cierra la conexion.
        viewer1.send("{\"type\":\"pong\"}");
        String frame = "{\"type\":\"frame\",\"n\":1}";
        emitter.send(frame);
        assertThat(viewer1.next()).isEqualTo(frame);
        assertThat(viewer2.next()).isEqualTo(frame);

        // Spam repetido: se cierra con POLICY_VIOLATION.
        for (int i = 0; i < 30 && viewer1.session.isOpen(); i++) {
            try {
                viewer1.send("basura " + i);
            } catch (IOException | IllegalStateException ex) {
                break; // el servidor ya lo cerro
            }
        }
        assertThat(viewer1.awaitClosed()).isTrue();
        assertThat(viewer1.closeStatus.getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());

        // El otro visor sigue recibiendo.
        String frame2 = "{\"type\":\"frame\",\"n\":2}";
        emitter.send(frame2);
        // Al irse viewer1 el servidor avisa el nuevo conteo de visores: se ignora y se espera el frame.
        String recibido = viewer2.next();
        while (recibido != null && recibido.contains("\"type\":\"viewers\"")) {
            recibido = viewer2.next();
        }
        assertThat(recibido).isEqualTo(frame2);
    }

    @Test
    void claveInvalidaSalaInexistenteYSinCodigoSeRechazan() throws Exception {
        Room room = createRoom();

        assertThat(handshakeStatus(url(room.code(), "clave-incorrecta"), null)).isEqualTo(403);
        assertThat(handshakeStatus(url(room.code(), ""), null)).isEqualTo(403);
        assertThat(handshakeStatus(url("ZZZZZZ", null), null)).isEqualTo(404);
        assertThat(handshakeStatus(url("ZZZZZZ", "cualquiera"), null)).isEqualTo(404);
        assertThat(handshakeStatus(url(null, null), null)).isEqualTo(404);

        // El cliente WebSocket tambien falla limpiamente (sin excepciones sin capturar).
        assertThatThrownBy(() -> connect(room.code(), "clave-incorrecta", null))
                .isInstanceOf(ExecutionException.class);
        assertThatThrownBy(() -> connect("ZZZZZZ", null, null))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void respetaElLimiteDeVisoresYLiberaElCupo() throws Exception {
        Room room = createRoom(); // limite de la prueba: 2 visores
        TestClient emitter = emitter(room);
        TestClient viewer1 = viewer(room);
        TestClient viewer2 = viewer(room);
        awaitViewerCount(emitter, 2);

        assertThat(handshakeStatus(url(room.code(), null), null)).isEqualTo(429);

        viewer1.closeQuietly();
        awaitViewerCount(emitter, 1);

        TestClient viewer3 = viewer(room);
        assertThat(viewer3.nextJson().get("viewers").asInt()).isEqualTo(2);
        assertThat(viewer2.session.isOpen()).isTrue();
    }

    @Test
    void frameDemasiadoGrandeSeDescartaSinCerrarLaConexion() throws Exception {
        Room room = createRoom(); // maximo de la prueba: 1024 bytes
        TestClient emitter = emitter(room);
        TestClient viewer = viewer(room);
        emitter.drain();
        viewer.drain();

        String head = "{\"type\":\"frame\",\"pad\":\"";
        String tail = "\"}";
        String tooBig = head + "a".repeat(2000) + tail;
        String exact = head + "a".repeat(1024 - head.length() - tail.length()) + tail;
        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(1024);

        emitter.send(tooBig);
        viewer.expectSilence();
        assertThat(emitter.session.isOpen()).isTrue();

        emitter.send(exact);
        assertThat(viewer.next()).isEqualTo(exact);
    }

    @Test
    void mensajesInvalidosDelEmisorNoSeReenvian() throws Exception {
        Room room = createRoom();
        TestClient emitter = emitter(room);
        TestClient viewer = viewer(room);
        emitter.drain();
        viewer.drain();

        emitter.send("esto no es json");
        emitter.send("[1,2,3]");
        emitter.send("{\"type\":\"otro\"}");
        emitter.send("{\"tipo\":\"frame\"}");
        emitter.send("{\"type\":5}");
        emitter.send("{\"type\":\"frame\"} basura");
        emitter.send("{\"type\":\"pong\"}");
        viewer.expectSilence();

        String frame = "{\"type\":\"frame\",\"ok\":true}";
        emitter.send(frame);
        assertThat(viewer.next()).isEqualTo(frame);
    }

    @Test
    void laTasaExcedidaSeDescartaEnSilencio() throws Exception {
        Room room = createRoom(); // limite de la prueba: 5 frames por segundo
        TestClient emitter = emitter(room);
        TestClient viewer = viewer(room);
        emitter.drain();
        viewer.drain();

        // Reloj congelado: todos caen en la misma ventana de un segundo.
        for (int i = 1; i <= 8; i++) {
            emitter.send("{\"type\":\"frame\",\"n\":" + i + "}");
        }
        for (int i = 1; i <= 5; i++) {
            assertThat(viewer.next()).isEqualTo("{\"type\":\"frame\",\"n\":" + i + "}");
        }
        viewer.expectSilence();
        assertThat(emitter.session.isOpen()).isTrue();

        // Pasa el segundo: vuelve a aceptar.
        clock.advance(Duration.ofMillis(1100));
        emitter.send("{\"type\":\"frame\",\"n\":9}");
        assertThat(viewer.next()).isEqualTo("{\"type\":\"frame\",\"n\":9}");
    }

    @Test
    void unNuevoEmisorReemplazaAlAnteriorYElVisorNoVeDesconexion() throws Exception {
        Room room = createRoom();
        TestClient first = emitter(room);
        TestClient viewer = viewer(room);
        first.drain();
        viewer.drain();

        TestClient second = emitter(room);
        assertThat(first.awaitClosed()).isTrue();
        assertThat(first.closeStatus.getCode()).isEqualTo(4001);
        assertThat(first.closeStatus.getReason()).containsIgnoringCase("reemplazado");

        assertThat(second.nextJson().get("viewers").asInt()).isEqualTo(1);
        JsonNode online = viewer.nextJson();
        assertThat(online.get("type").asText()).isEqualTo("emitter");
        assertThat(online.get("online").asBoolean()).isTrue();
        viewer.expectSilence(); // nunca llego un online:false

        String frame = "{\"type\":\"frame\",\"desde\":\"segundo\"}";
        second.send(frame);
        assertThat(viewer.next()).isEqualTo(frame);

        // Cuando el emisor vigente se va, el visor se entera.
        second.closeQuietly();
        JsonNode offline = viewer.nextJson();
        assertThat(offline.get("type").asText()).isEqualTo("emitter");
        assertThat(offline.get("online").asBoolean()).isFalse();
    }

    @Test
    void laSalaVencidaSeCierraAvisaYSeRechaza() throws Exception {
        Room room = createRoom();
        TestClient emitter = emitter(room);
        TestClient viewer = viewer(room);
        emitter.drain();
        viewer.drain();

        clock.advance(Duration.ofMinutes(241));
        roomService.cleanupExpiredRooms();

        for (TestClient client : List.of(emitter, viewer)) {
            JsonNode closed = client.nextJson();
            assertThat(closed.get("type").asText()).isEqualTo("closed");
            assertThat(closed.get("reason").asText()).isNotBlank();
            assertThat(client.awaitClosed()).isTrue();
            assertThat(client.closeStatus.getCode()).isEqualTo(CloseStatus.NORMAL.getCode());
        }
        assertThat(roomService.findActiveRoom(room.code())).isEmpty();
        assertThat(handshakeStatus(url(room.code(), null), null)).isEqualTo(404);
        assertThat(handshakeStatus(url(room.code(), room.key()), null)).isEqualTo(404);
    }

    @Test
    void laSalaVencidaSeRechazaAunSinBarridoPorLimpiezaPerezosa() throws Exception {
        Room room = createRoom();
        clock.advance(Duration.ofMinutes(241));
        assertThat(handshakeStatus(url(room.code(), null), null)).isEqualTo(404);
        assertThat(roomService.findActiveRoom(room.code())).isEmpty();
    }

    @Test
    void elHeartbeatLlegaATodosYElPongSeIgnora() throws Exception {
        Room room = createRoom();
        TestClient emitter = emitter(room);
        TestClient viewer = viewer(room);
        emitter.drain();
        viewer.drain();

        roomService.sendHeartbeat();
        assertThat(emitter.next()).isEqualTo("{\"type\":\"ping\"}");
        assertThat(viewer.next()).isEqualTo("{\"type\":\"ping\"}");

        emitter.send("{\"type\":\"pong\"}");
        viewer.send("{\"type\":\"pong\"}");
        viewer.expectSilence();
        emitter.expectSilence();
        assertThat(viewer.session.isOpen()).isTrue();
    }

    @Test
    void respetaLosOrigenesPermitidos() throws Exception {
        Room room = createRoom();
        assertThat(handshakeStatus(url(room.code(), null), "http://evil.example")).isEqualTo(403);

        TestClient allowed = connect(room.code(), null, "http://localhost:3000");
        assertThat(allowed.nextJson().get("type").asText()).isEqualTo("hello");
        // Sin cabecera Origin (clientes de prueba) tambien se permite: lo hacen todas las demas pruebas.
    }
}
