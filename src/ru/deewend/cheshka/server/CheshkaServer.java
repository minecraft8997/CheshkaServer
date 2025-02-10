package ru.deewend.cheshka.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class CheshkaServer {
    public static final int TICK_RATE_HZ = 20;
    public static final int MAX_SLEEP_TIME_MS = 1000 / TICK_RATE_HZ;
    public static final int SERVER_PORT;
    public static final int BOARD_SIZE;
    public static final int MAX_ONLINE_PLAYER_COUNT;
    public static final int MAX_ONLINE_PLAYER_COUNT_SOFT_KICK;
    public static final int MAX_ROOM_COUNT;
    public static final int PLAYER_TIME_S;
    public static final int PLAYER_TIME_TICKS;
    public static final int MAX_HOST_WAITING_TIME_S;
    public static final int MAX_HOST_WAITING_TIME_TICKS;
    public static final byte ACTION_ACCEPT = 0;
    public static final byte ACTION_AND_CLOSE_LATER = 1;
    public static final byte ACTION_CLOSE_NOW = 2;
    public static final String PROPERTY_PREFIX = "cheshka.server.";

    private final Random random = new Random();
    private volatile int onlinePlayerCount;
    private final Map<String, GameRoom> gameRooms = new HashMap<>();

    static {
        Log.i("Initializing");
        SERVER_PORT = Integer.parseInt(
                Helper.getProperty("port", "23829")); // Math.abs("Cheshka".hashCode() % 65536)
        BOARD_SIZE = Integer.parseInt(
                Helper.getProperty("boardSize", "23829"));
        MAX_ONLINE_PLAYER_COUNT = Integer.parseInt(
                Helper.getProperty("maxOnlinePlayerCount", "1500"));
        MAX_ONLINE_PLAYER_COUNT_SOFT_KICK = Integer.parseInt(
                Helper.getProperty("maxOnlinePlayerCountSoftKick", "2000"));
        MAX_ROOM_COUNT = Integer.parseInt(
                Helper.getProperty("maxRoomCount", "700"));
        PLAYER_TIME_S = Integer.parseInt(
                Helper.getProperty("playerTimeSeconds", "1800"));
        MAX_HOST_WAITING_TIME_S = Integer.parseInt(
                Helper.getProperty("maxHostWaitingTimeSeconds", "900"));

        PLAYER_TIME_TICKS = PLAYER_TIME_S * TICK_RATE_HZ;
        MAX_HOST_WAITING_TIME_TICKS = MAX_HOST_WAITING_TIME_S * TICK_RATE_HZ;
    }

    public static void main(String[] args) throws Throwable {
        (new CheshkaServer()).run();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void run() throws IOException {
        Helper.newThread("Updater", (new UpdateTask(this)), true);

        ServerSocket listeningSocket = new ServerSocket(SERVER_PORT);
        Log.i("The Cheshka server is listening on port " + SERVER_PORT + " (TCP)");

        while (true) {
            Socket socket = listeningSocket.accept();
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(PLAYER_TIME_S * 1000 + (5 * 60 * 1000)); // adding 5 minutes

            byte action;
            synchronized (this) {
                int onlinePlayerCount = ++this.onlinePlayerCount;
                if (onlinePlayerCount <= MAX_ONLINE_PLAYER_COUNT)
                    action = ACTION_ACCEPT;
                else if (onlinePlayerCount <= MAX_ONLINE_PLAYER_COUNT_SOFT_KICK)
                    action = ACTION_AND_CLOSE_LATER;
                else
                    action = ACTION_CLOSE_NOW;
            }

            ClientHandler handler;
            if (action == ACTION_ACCEPT) {
                handler = new ClientHandler(this, socket);
            } else if (action == ACTION_AND_CLOSE_LATER) {
                handler = new ClientHandler(this, socket, true);
            } else {
                try {
                    socket.close();
                } catch (IOException ignored) {}

                continue;
            }

            Helper.newThread("Client Handler", handler, false);
        }
    }

    public Random getRandom() {
        return random;
    }

    public int getOnlinePlayerCount() {
        return onlinePlayerCount;
    }

    public synchronized void decrementOnlinePlayerCount() {
        onlinePlayerCount--;
    }

    public boolean accessGameRooms(Helper.Providable<Map<String, GameRoom>> providable) {
        synchronized (gameRooms) {
            try {
                return providable.provide(gameRooms);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
