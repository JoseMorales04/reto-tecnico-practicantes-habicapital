package com.josemorales.billetera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prueba de punta a punta por HTTP, como lo usaría el frontend. */
@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {

    @Autowired MockMvc mvc;
    @Autowired DatabaseCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner.clean();
    }

    @Test
    void flujoCompletoPorHttp() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Beto\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/accounts/1/deposits").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50000}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":20000,\"description\":\"Pizza\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/accounts/1")).andExpect(jsonPath("$.balance").value(30000));
        mvc.perform(get("/api/accounts/2/movements")).andExpect(jsonPath("$[0].description").value("Pizza"));
        mvc.perform(get("/api/audit")).andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void erroresClaros() throws Exception {
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ana\"}"));
        mvc.perform(post("/api/accounts").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Beto\"}"));

        // sin llave de idempotencia
        mvc.perform(post("/api/accounts/1/deposits").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
                .andExpect(status().isBadRequest());
        // monto negativo
        mvc.perform(post("/api/accounts/1/deposits").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":-100}"))
                .andExpect(status().isBadRequest());
        // saldo insuficiente
        mvc.perform(post("/api/transfers").header("Idempotency-Key", "k2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\":1,\"toAccountId\":2,\"amount\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
        // cuenta que no existe
        mvc.perform(get("/api/accounts/99")).andExpect(status().isNotFound());
    }
}
