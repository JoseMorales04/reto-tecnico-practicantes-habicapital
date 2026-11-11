package com.josemorales.billetera;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deja la base de datos vacía antes de cada prueba. */
@Component
public class DatabaseCleaner {
    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void clean() {
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY FALSE").executeUpdate();
        for (Object table : em.createNativeQuery(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_TYPE = 'BASE TABLE'").getResultList()) {
            em.createNativeQuery("TRUNCATE TABLE \"" + table + "\" RESTART IDENTITY").executeUpdate();
        }
        em.createNativeQuery("SET REFERENTIAL_INTEGRITY TRUE").executeUpdate();
    }
}
