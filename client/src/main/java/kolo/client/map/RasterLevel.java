package kolo.client.map;

import java.util.ArrayList;
import java.util.List;

/**
 * Рівень деталізації растру карти: розмір зображення в пікселях і масштаб — пікселів на одиницю карти. Піксель
 * {@code (x, y)} — точка карти {@code ((x + 0.5) / scale, (y + 0.5) / scale)}.
 *
 * @param width ширина растру
 * @param height висота растру
 * @param scale пікселів на одиницю карти
 */
public record RasterLevel(int width, int height, double scale) {

    /**
     * Рівні від грубого до детального: довша сторона кожного — {@code longSides[i]} пікселів, але не детальніше за
     * {@code maxScale} (дрібніший растр карта ніколи не покаже).
     */
    public static List<RasterLevel> of(double mapWidth, double mapHeight, double maxScale, int... longSides) {
        List<RasterLevel> levels = new ArrayList<>();
        double previous = 0;
        for (int side : longSides) {
            double scale = Math.min(side / Math.max(mapWidth, mapHeight), maxScale);
            if (scale <= previous) {
                continue;
            }
            previous = scale;
            // Вгору: растр вкриває всю карту, а піксель рахується рівно з масштабу — як у хіт-тесті й камері.
            int width = Math.max(1, (int) Math.ceil(mapWidth * scale));
            int height = Math.max(1, (int) Math.ceil(mapHeight * scale));
            levels.add(new RasterLevel(width, height, scale));
        }
        return List.copyOf(levels);
    }

    /**
     * Рівень для масштабу камери: найгрубіший, що не менш детальний за екран (зменшувати растр краще, ніж
     * збільшувати), інакше найдетальніший.
     */
    public static int choose(List<RasterLevel> levels, double scale) {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).scale() >= scale) {
                return i;
            }
        }
        return levels.size() - 1;
    }
}
