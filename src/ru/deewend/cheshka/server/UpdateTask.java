package ru.deewend.cheshka.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.deewend.cheshka.server.CheshkaServer.*;

public class UpdateTask implements Runnable {
    private final CheshkaServer server;

    public UpdateTask(CheshkaServer server) {
        this.server = server;
    }

    @Override
    @SuppressWarnings({"BusyWait", "InfiniteLoopStatement", "finally"})
    public void run() {
        try {
            Thread.sleep(MAX_SLEEP_TIME_MS);
            while (true) {
                long started = System.currentTimeMillis();
                tick();
                long delta = System.currentTimeMillis() - started;
                if (delta < MAX_SLEEP_TIME_MS) {
                    Thread.sleep(MAX_SLEEP_TIME_MS - delta);
                } else {
                    Thread.sleep(1);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            Log.s("InterruptedException in UpdateTask", e);
        } finally {
            Log.s("The server ticking thread has stopped. The application will be terminated");

            System.exit(-1);
        }
    }

    private void tick() {
        server.accessGameRooms(gameRooms -> {
            if (gameRooms.isEmpty()) return true;

            List<String> entriesToRemove = new ArrayList<>();

            for (Map.Entry<String, GameRoom> entry : gameRooms.entrySet()) {
                GameRoom gameRoom = entry.getValue();

                if (!gameRoom.tick()) entriesToRemove.add(entry.getKey());
            }
            for (String key : entriesToRemove) { // key = invitationCode
                gameRooms.remove(key);
            }

            return true;
        });
    }
}
