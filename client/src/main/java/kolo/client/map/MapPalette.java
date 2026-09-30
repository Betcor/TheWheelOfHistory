package kolo.client.map;

import kolo.engine.state.Climate;
import kolo.engine.state.Terrain;
import kolo.engine.view.CellView;

/**
 * Кольори карти у форматі ARGB. Пласкі кольори без текстур (GD §22.1); вода однакова в усіх режимах, щоб обриси
 * материків не «стрибали» при зміні режиму.
 */
public final class MapPalette {

    public static final int SEA = rgb(0x9C, 0xC3, 0xDE);
    public static final int LAKE = rgb(0xB4, 0xD4, 0xEA);
    public static final int UNCLAIMED = rgb(0xE6, 0xE0, 0xD2);
    public static final int RIVER = rgb(0x5B, 0x93, 0xC7);
    public static final int COUNTRY_BORDER = rgb(0x3A, 0x3A, 0x3A);
    public static final int HOVER = rgb(0xFF, 0xFF, 0xFF);
    public static final int SELECTION = rgb(0xF2, 0xB7, 0x05);

    /** Кут повороту відтінку між сусідніми номерами держав — «золотий кут»: сусідні номери помітно різні. */
    private static final double GOLDEN_ANGLE = 137.507_764;

    private MapPalette() {}

    /** Колір заливки комірки в режимі. */
    public static int color(CellView cell, MapMode mode) {
        return switch (cell.kind()) {
            case SEA -> SEA;
            case LAKE -> LAKE;
            case LAND ->
                switch (mode) {
                    case POLITICAL ->
                        cell.country().isPresent() ? country(cell.country().getAsInt()) : UNCLAIMED;
                    case TERRAIN -> terrain(cell.terrain().orElseThrow());
                    case CLIMATE -> climate(cell.climate().orElseThrow());
                    case FERTILITY -> fertility(cell.fertility().orElseThrow());
                };
        };
    }

    /** Колір держави за номером: відтінок крокує золотим кутом, насиченість і яскравість — помірні. */
    public static int country(int number) {
        double hue = (number * GOLDEN_ANGLE) % 360;
        // Три рівні яскравості, щоб держави з близьким відтінком через 2–3 номери все одно розрізнялися.
        double brightness = 0.92 - 0.07 * (number % 3);
        return hsb(hue, 0.42, brightness);
    }

    public static int terrain(Terrain terrain) {
        return switch (terrain) {
            case PLAIN -> rgb(0xB8, 0xD6, 0x8A);
            case HILLS -> rgb(0xC9, 0xC0, 0x7A);
            case MOUNTAINS -> rgb(0x9A, 0x86, 0x74);
            case FOREST -> rgb(0x5E, 0x9A, 0x5A);
            case DESERT -> rgb(0xEB, 0xD6, 0x9A);
            case TUNDRA -> rgb(0xD5, 0xDD, 0xDC);
            case SWAMP -> rgb(0x7F, 0x9C, 0x7C);
        };
    }

    public static int climate(Climate climate) {
        return switch (climate) {
            case POLAR -> rgb(0xF2, 0xF5, 0xF7);
            case BOREAL -> rgb(0x86, 0xB0, 0xA8);
            case TEMPERATE -> rgb(0xA8, 0xCF, 0x84);
            case ARID -> rgb(0xE8, 0xB8, 0x6E);
            case TROPICAL -> rgb(0x4F, 0xA0, 0x5E);
        };
    }

    /** Від безплідної (пісок) до житниці (насичена зелень). */
    public static int fertility(int fertility) {
        int barren = rgb(0xE9, 0xDC, 0xB8);
        int rich = rgb(0x3F, 0x8F, 0x3A);
        return mix(barren, rich, Math.clamp(fertility, 0, 100) / 100.0);
    }

    /** Колір, затемнений до частки {@code factor} яскравості. */
    public static int darker(int argb, double factor) {
        return mix(rgb(0, 0, 0), argb, factor);
    }

    static int mix(int from, int to, double t) {
        int r = (int) Math.round(red(from) + (red(to) - red(from)) * t);
        int g = (int) Math.round(green(from) + (green(to) - green(from)) * t);
        int b = (int) Math.round(blue(from) + (blue(to) - blue(from)) * t);
        return rgb(r, g, b);
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF00_0000 | (r << 16) | (g << 8) | b;
    }

    public static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    public static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    public static int blue(int argb) {
        return argb & 0xFF;
    }

    /** HSB → ARGB: відтінок у градусах, насиченість і яскравість — 0..1. */
    static int hsb(double hue, double saturation, double brightness) {
        double h = (hue % 360 + 360) % 360 / 60;
        int sector = (int) Math.floor(h);
        double f = h - sector;
        double p = brightness * (1 - saturation);
        double q = brightness * (1 - saturation * f);
        double t = brightness * (1 - saturation * (1 - f));
        double r;
        double g;
        double b;
        switch (sector) {
            case 0 -> {
                r = brightness;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = brightness;
                b = p;
            }
            case 2 -> {
                r = p;
                g = brightness;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = brightness;
            }
            case 4 -> {
                r = t;
                g = p;
                b = brightness;
            }
            default -> {
                r = brightness;
                g = p;
                b = q;
            }
        }
        return rgb((int) Math.round(r * 255), (int) Math.round(g * 255), (int) Math.round(b * 255));
    }
}
