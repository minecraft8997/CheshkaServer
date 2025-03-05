package ru.deewend.cheshka.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CheshkaServer {
    public static final int TICK_RATE_HZ = 20;
    public static final int MAX_SLEEP_TIME_MS = 1000 / TICK_RATE_HZ;
    public static final int SERVER_PORT;
    public static final int BOARD_SIZE;
    public static final int MAX_ONLINE_PLAYER_COUNT;
    public static final int MAX_ONLINE_PLAYER_COUNT_SOFT_KICK;
    public static final int MAX_IDLE_TIME_MILLIS;
    public static final boolean USE_CAPTCHA;
    public static final boolean SHOW_PROPERTIES;
    public static final byte ACTION_ACCEPT = 0;
    public static final byte ACTION_ACCEPT_AND_CLOSE_LATER = 1;
    public static final byte ACTION_CLOSE_NOW = 2;
    public static final String PROPERTY_PREFIX = "cheshka.server.";

    private final Random random = new Random();
    private volatile int onlinePlayerCount;
    private final Set<GameRoom> gameRooms = new HashSet<>();
    private final Set<ClientHandler> authenticatedUsers = new HashSet<>();

    static {
        SHOW_PROPERTIES = Boolean.parseBoolean(Helper.getProperty("showProperties", "false"));

        Log.i("Initializing");
        SERVER_PORT = Integer.parseInt(
                Helper.getProperty("port", "23829")); // Math.abs("Cheshka".hashCode() % 65536)
        BOARD_SIZE = Integer.parseInt(Helper.getProperty("boardSize", "8"));
        MAX_ONLINE_PLAYER_COUNT = Integer.parseInt(Helper.getProperty("maxOnlinePlayerCount", "1500"));
        MAX_ONLINE_PLAYER_COUNT_SOFT_KICK = Integer.parseInt(
                Helper.getProperty("maxOnlinePlayerCountSoftKick", "2000"));
        int maxIdleTimeSeconds = Integer.parseInt(Helper.getProperty("maxIdleTimeSeconds", "1800"));
        String captchaProperty = "useCaptcha";
        USE_CAPTCHA = Boolean.parseBoolean(Helper.getProperty(captchaProperty, "false"));
        if (!USE_CAPTCHA) {
            /*
             * I don't think it's worth logging to the disk.
             */
            System.out.println("---------------------------------------------------------------------------------");
            System.out.println("Captcha challenge is disabled. If you wish to verify newcomers, download");
            System.out.println("https://sourceforge.net/projects/simplecaptcha/files/simplecaptcha-1.2.1.jar,");
            System.out.println("place the jarfile in the server's working directory and restart the server adding");
            System.out.println("-D" + (PROPERTY_PREFIX + captchaProperty) + "=true to the startup command before the -jar flag");
            System.out.println("---------------------------------------------------------------------------------");
        }

        MAX_IDLE_TIME_MILLIS = (int) TimeUnit.SECONDS.toMillis(maxIdleTimeSeconds);
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
            socket.setSoTimeout(MAX_IDLE_TIME_MILLIS);

            byte action;
            synchronized (this) {
                int onlinePlayerCount = ++this.onlinePlayerCount;

                if (onlinePlayerCount <= MAX_ONLINE_PLAYER_COUNT)                action = ACTION_ACCEPT;
                else if (onlinePlayerCount <= MAX_ONLINE_PLAYER_COUNT_SOFT_KICK) action = ACTION_ACCEPT_AND_CLOSE_LATER;
                else                                                             action = ACTION_CLOSE_NOW;
            }

            ClientHandler handler;
            if (action == ACTION_ACCEPT) {
                handler = new ClientHandler(this, socket);
            } else if (action == ACTION_ACCEPT_AND_CLOSE_LATER) {
                handler = new ClientHandler(this, socket, true);
            } else {
                Helper.close(socket);

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

    public void accessGameRooms(Helper.Providable<Set<GameRoom>> providable) {
        provide(gameRooms, providable);
    }

    public void accessAuthenticatedUsers(Helper.Providable<Set<ClientHandler>> providable) {
        provide(authenticatedUsers, providable);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private <T> void provide(T object, Helper.Providable<T> providable) {
        synchronized (object) {
            try {
                providable.provide(object);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
