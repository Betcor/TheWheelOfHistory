package kolo.engine.content;

import java.util.Locale;

/**
 * Складник сили держави при генерації (GD §4.11): колесо ланцюжка (GD §4.1), чия якість входить у проміжну силу.
 *
 * <p>Enum, а не контент: складники — це колеса, які рушій крутить у фіксованому порядку; контент задає лише їхні
 * ваги. Розвиненість — один складник із середньою якістю за галузями, як у стріках (GD §4.10). Нейтральних коліс
 * (материк, лад, релігія, ресурси, назва, люди) тут немає; передісторії теж — після неї вже нема чого зсувати.
 */
public enum PowerComponent {
    AREA,
    POPULATION,
    DEVELOPMENT,
    GDP,
    HDI,
    ARMY_SIZE,
    ARMY_TRAINING,
    NUCLEAR;

    /** Ключ у контенті, напр. {@code army_size}. */
    public String key() {
        // Locale.ROOT: інакше в турецькій локалі «I» перетворюється на «ı».
        return name().toLowerCase(Locale.ROOT);
    }
}
