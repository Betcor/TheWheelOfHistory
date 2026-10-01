package kolo.client.screen;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;
import kolo.client.i18n.Texts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Результат мережевого запиту — у потік UI: успіх — обробнику, помилка — текстом для гравця. */
final class UiFutures {

    private static final Logger LOG = LoggerFactory.getLogger(UiFutures.class);

    private UiFutures() {}

    static <T> void onUi(CompletableFuture<T> future, Texts texts, Consumer<T> success, Consumer<String> failure) {
        future.whenComplete((value, error) -> {
            if (error == null) {
                Platform.runLater(() -> success.accept(value));
                return;
            }
            if (!ErrorTexts.expected(error)) {
                // Межа запиту: баг не має зникнути мовчки.
                LOG.error("Неочікувана помилка запиту", error);
            }
            String message = ErrorTexts.of(texts, error);
            Platform.runLater(() -> failure.accept(message));
        });
    }
}
