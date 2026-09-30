package kolo.client.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kolo.engine.view.CellView;
import kolo.engine.view.MapView;

/** Де підписати держави на політичній карті. */
public final class CountryLabels {

    /**
     * Підпис держави.
     *
     * @param country номер держави
     * @param cell провінція, над якою стоїть підпис
     * @param x центр підпису в координатах {@link MapGeometry}
     * @param y центр підпису
     * @param size лінійний розмір держави в одиницях карти — від нього розмір шрифту
     */
    public record Label(int country, int cell, double x, double y, double size) {}

    private CountryLabels() {}

    /**
     * Підпис — над провінцією держави, найближчою до середнього центру її провінцій: для увігнутої держави середина
     * може лежати поза нею, а провінція — завжди своя.
     */
    public static List<Label> of(MapView view, MapGeometry geometry) {
        int countries = view.countries().size();
        double[] sumX = new double[countries];
        double[] sumY = new double[countries];
        int[] counts = new int[countries];
        for (int n = 0; n < view.cells().size(); n++) {
            CellView cell = view.cells().get(n);
            if (cell.country().isPresent()) {
                int c = cell.country().getAsInt();
                sumX[c] += geometry.siteX(n);
                sumY[c] += geometry.siteY(n);
                counts[c]++;
            }
        }
        int[] best = new int[countries];
        double[] bestDistance = new double[countries];
        Arrays.fill(best, -1);
        for (int n = 0; n < view.cells().size(); n++) {
            CellView cell = view.cells().get(n);
            if (cell.country().isEmpty()) {
                continue;
            }
            int c = cell.country().getAsInt();
            double dx = geometry.siteX(n) - sumX[c] / counts[c];
            double dy = geometry.siteY(n) - sumY[c] / counts[c];
            double distance = dx * dx + dy * dy;
            if (best[c] < 0 || distance < bestDistance[c]) {
                best[c] = n;
                bestDistance[c] = distance;
            }
        }
        List<Label> labels = new ArrayList<>();
        for (int c = 0; c < countries; c++) {
            if (best[c] >= 0) {
                double size = Math.sqrt(counts[c]) * view.cellSide();
                labels.add(new Label(c, best[c], geometry.siteX(best[c]), geometry.siteY(best[c]), size));
            }
        }
        return List.copyOf(labels);
    }
}
