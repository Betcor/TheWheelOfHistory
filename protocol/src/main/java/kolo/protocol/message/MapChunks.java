package kolo.protocol.message;

import java.util.ArrayList;
import java.util.List;
import kolo.engine.error.Checks;
import kolo.engine.view.MapView;
import kolo.protocol.Protocol;

/** Карта світу як послідовність повідомлень: {@link ServerMessage.MapStart}, потім частини {@link ServerMessage.MapCells}. */
public final class MapChunks {

    private MapChunks() {}

    /** Частини по {@value Protocol#MAP_CHUNK_CELLS} комірок. */
    public static List<ServerMessage> split(MapView map) {
        return split(map, Protocol.MAP_CHUNK_CELLS);
    }

    /**
     * @param chunkCells скільки комірок в одній частині; остання може бути меншою
     * @return початок карти й частини комірок по порядку
     */
    public static List<ServerMessage> split(MapView map, int chunkCells) {
        Checks.inRange("chunk_cells", chunkCells, 1, Integer.MAX_VALUE);
        int cells = map.cells().size();
        List<ServerMessage> messages = new ArrayList<>(1 + (cells + chunkCells - 1) / chunkCells);
        messages.add(new ServerMessage.MapStart(map.seed(), map.width(), map.height(), cells, map.countries()));
        for (int first = 0; first < cells; first += chunkCells) {
            int end = Math.min(cells, first + chunkCells);
            messages.add(new ServerMessage.MapCells(first, map.cells().subList(first, end)));
        }
        return messages;
    }
}
