// Guardar en: src/test/java/com/enterprise/tacticalsim/modules/mirror/controller/MirrorControllerTest.java
package com.enterprise.tacticalsim.modules.mirror.controller;

import com.enterprise.tacticalsim.modules.mirror.config.MirrorProperties;
import com.enterprise.tacticalsim.modules.mirror.service.MirrorRoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba del controlador REST con MockMvc standalone (sin seguridad real ni
 * contexto de Spring): se simula el {@code Authentication} del usuario.
 * Las reglas de roles de SecurityConfig no se ejercitan aqui.
 */
class MirrorControllerTest {

    private static final String CODE_REGEX = "[A-HJKMNP-Z2-9]{6}";

    private final ObjectMapper mapper = new ObjectMapper();
    private MirrorProperties props;
    private MockMvc mvc;
    private UsernamePasswordAuthenticationToken analyst;

    @BeforeEach
    void setUp() {
        props = new MirrorProperties();
        props.setMaxRooms(3);
        MirrorRoomService service = new MirrorRoomService(props, mapper, Clock.systemUTC());
        mvc = MockMvcBuilders.standaloneSetup(new MirrorController(service)).build();
        analyst = new UsernamePasswordAuthenticationToken(
                "analista@equipo.com", null, AuthorityUtils.createAuthorityList("ROLE_ANALYST"));
    }

    @Test
    void crearSalaDevuelveEnvoltorioConCodigoClaveYVigencia() throws Exception {
        mvc.perform(post("/api/v1/mirror/rooms").principal(analyst))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.message").value("Sala de duplicación creada"))
                .andExpect(jsonPath("$.data.roomCode").value(matchesPattern(CODE_REGEX)))
                .andExpect(jsonPath("$.data.emitterKey").value(matchesPattern("[A-Za-z0-9_-]{43}")))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(14400));
    }

    @Test
    void cadaSalaTieneCodigoYClaveDistintos() throws Exception {
        props.setMaxRooms(40);
        Set<String> codes = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            MvcResult result = mvc.perform(post("/api/v1/mirror/rooms").principal(analyst))
                    .andExpect(status().isCreated())
                    .andReturn();
            JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).get("data");
            assertThat(data.get("roomCode").asText()).matches(CODE_REGEX);
            codes.add(data.get("roomCode").asText());
            keys.add(data.get("emitterKey").asText());
        }
        assertThat(codes).hasSize(30);
        assertThat(keys).hasSize(30);
    }

    @Test
    void alExcederElMaximoDeSalasResponde429EnEspanol() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/mirror/rooms").principal(analyst)).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/mirror/rooms").principal(analyst))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("límite de salas")));
    }
}
