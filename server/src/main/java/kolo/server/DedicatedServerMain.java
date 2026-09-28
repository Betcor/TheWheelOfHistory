package kolo.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Точка входу окремого (headless) сервера. */
public final class DedicatedServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(DedicatedServerMain.class);

    private DedicatedServerMain() {}

    public static void main(String[] args) {
        // Мережа й сесії з'являться на Етапі 4.
        LOG.info("Окремий сервер ще не реалізовано");
    }
}
