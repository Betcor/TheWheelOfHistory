package kolo.content;

import static org.assertj.core.api.Assertions.assertThat;

import kolo.content.loader.ContentLoader;
import kolo.engine.content.ContentPack;
import kolo.engine.content.SubIdeologyId;
import kolo.engine.generation.name.CountryNames;
import kolo.engine.rng.Rng;
import kolo.engine.state.LocalizedName;
import org.junit.jupiter.api.Test;

/** Смок-тест: вбудований контент гри завантажується, як при старті клієнта чи сервера. */
class ContentSmokeTest {

    @Test
    void bundledContentLoads() {
        ContentPack pack = ContentLoader.loadBundled();

        assertThat(pack.ideologies()).isNotEmpty();
        assertThat(pack.doctrines()).isNotEmpty();
        assertThat(pack.resources()).isNotEmpty();
        assertThat(pack.developmentLevels()).isNotEmpty();
        assertThat(pack.nuclearStatuses()).isNotEmpty();
        assertThat(pack.traits()).isNotEmpty();
        assertThat(pack.names().stateForms()).isNotEmpty();
        assertThat(pack.hash()).matches("[0-9a-f]{64}");
        // Повторне завантаження — той самий хеш: клієнт і сервер з однаковими файлами зійдуться.
        assertThat(ContentLoader.loadBundled().hash()).isEqualTo(pack.hash());
    }

    @Test
    void bundledContentNamesACountry() {
        ContentPack pack = ContentLoader.loadBundled();

        LocalizedName name = CountryNames.generate(Rng.of(42), pack, new SubIdeologyId("liberal_democracy"));

        assertThat(name.fullName().nominative()).endsWith(" " + name.shortName().nominative());
    }
}
