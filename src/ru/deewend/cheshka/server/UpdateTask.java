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

                // at this moment no one can connect to this room
                // (since the map is locked), so we are free to
                // perform our job without synchronization
                if (gameRoom.getOpponentPlayerHandler() == null) {
                    if (gameRoom.getWaitingForTheOpponentTicks() >=
                            MAX_HOST_WAITING_TIME_TICKS
                    ) {
                        // IOExceptions are ignored
                        Helper.sendMessageIgnoreErrors(
                                gameRoom.getHostPlayerHandler(),
                                "disconnect:host_timeout"
                        );
                        gameRoom.getHostPlayerHandler().close();
                        entriesToRemove.add(entry.getKey());

                        Log.i("Removing a room (invitationCode=" + gameRoom
                                .getInvitationCode() + ") because we didn't " +
                                "manage to find an opponent");
                    } else {
                        gameRoom.incrementWaitingForTheOpponentTime();
                    }

                    continue;
                }

                if (gameRoom.getHostPlayerHandler().isClosed() ||
                        gameRoom.getOpponentPlayerHandler().isClosed()
                ) {
                    continue; // the game will be terminated by their own threads
                }

                synchronized (gameRoom) {
                    if (gameRoom.getWhoMakesAMove() == gameRoom.getHostPlayerHandler()) {
                        gameRoom.decrementHostPlayerRemainingTime();
                    } else {
                        gameRoom.decrementOpponentPlayerRemainingTime();
                    }

                    int hostRemaining = gameRoom.getHostPlayerRemainingTimeTicks();
                    int opponentRemaining = gameRoom
                            .getOpponentPlayerRemainingTimeTicks();

                    gameRoom.incrementTicksExists();
                    if (gameRoom.getTicksExists() % 100 == 0) {
                        gameRoom.sendAll("time_sync " +
                                hostRemaining + " " + opponentRemaining);
                    }
                    if (hostRemaining <= 0 || opponentRemaining <= 0) {
                        entriesToRemove.add(entry.getKey());
                        Log.i("Someone ran out of time, removing the room... " +
                                "(invitationCode=" + gameRoom.getInvitationCode() + ")");

                        if (hostRemaining <= 0 && opponentRemaining <= 0) {
                            gameRoom.sendAll("disconnect:timed_out_draw");
                            gameRoom.getHostPlayerHandler().close();
                            // opponent's handler will be closed as well

                            continue;
                        }

                        String message;
                        if (hostRemaining <= 0) {
                            message = "disconnect:timed_out_" +
                                    (gameRoom.getHostColor() ? "white" : "black");
                        } else {
                            message = "disconnect:timed_out_" +
                                    (gameRoom.getHostColor() ? "black" : "white");
                        }
                        gameRoom.sendAll(message);
                        gameRoom.getHostPlayerHandler().close();
                    }
                }
            }
            for (String key : entriesToRemove) { // key = invitationCode
                gameRooms.remove(key);
            }

            return true;
        });
    }
}
