package kolo.protocol.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kolo.engine.error.ProtocolException;
import kolo.engine.error.ValidationException;
import kolo.engine.view.CellView;
import kolo.engine.view.MapView;
import kolo.protocol.ProtocolErrors;

/**
 * Збирає карту з частин на клієнті. Частини мусять іти по порядку, без пропусків і повторів — транспорт (TCP чи
 * {@code LocalChannel}) порядок зберігає, тож інший порядок означає помилку сервера. Одноразовий: одна карта.
 *
 * <p>Помилки — {@link ProtocolException} з місцем {@code map} і проблемою {@code map_not_started}, {@code
 * map_already_started}, {@code unexpected_first} (частина не з тієї комірки), {@code too_many_cells} або {@code
 * map_incomplete}; невалідна зібрана карта — з первинною помилкою.
 */
public final class MapAssembler {

    private static final String LOCATION = "map";

    private ServerMessage.MapStart start;
    private List<CellView> cells;

    /** @throws ProtocolException якщо карта вже почалася */
    public void start(ServerMessage.MapStart message) {
        if (start != null) {
            throw ProtocolErrors.malformed(LOCATION, "map_already_started");
        }
        start = message;
        cells = new ArrayList<>(message.cellCount());
    }

    /**
     * Додає частину.
     *
     * @return зібрана карта, якщо це була остання частина
     * @throws ProtocolException якщо частина не по порядку, зайва чи зібрана карта невалідна
     */
    public Optional<MapView> add(ServerMessage.MapCells message) {
        if (start == null) {
            throw ProtocolErrors.malformed(LOCATION, "map_not_started");
        }
        if (message.first() != cells.size()) {
            throw ProtocolErrors.malformed(LOCATION, "unexpected_first");
        }
        if (message.cells().size() > start.cellCount() - cells.size()) {
            throw ProtocolErrors.malformed(LOCATION, "too_many_cells");
        }
        cells.addAll(message.cells());
        return complete() ? Optional.of(result()) : Optional.empty();
    }

    /** Чи прийшли всі комірки. */
    public boolean complete() {
        return start != null && cells.size() == start.cellCount();
    }

    /** @throws ProtocolException якщо прийшли ще не всі частини або карта невалідна */
    public MapView result() {
        if (!complete()) {
            throw ProtocolErrors.malformed(LOCATION, "map_incomplete");
        }
        try {
            return new MapView(start.seed(), start.width(), start.height(), cells, start.countries());
        } catch (ValidationException e) {
            throw ProtocolErrors.because(LOCATION, e);
        }
    }
}
