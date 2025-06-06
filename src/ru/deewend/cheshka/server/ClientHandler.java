package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.packet.*;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;

import static ru.deewend.cheshka.server.Helper.getClassName;

public class ClientHandler implements Runnable {
    public static final int MAX_PACKET_COUNT_IN_QUEUE = 24;
    public static final int CAPTCHA_ATTEMPTS = 10;
    public static final int MAX_UNEXPECTED_PACKET_COUNT_IN_A_ROW = 5;
    private static final Set<Class<? extends Packet>> HANDLED_BY_GAME_ROOM =
            Set.of(CancelMatchmaking.class, RollDice.class, MakeMove.class, Resign.class);

    private final CheshkaServer cheshkaServer;
    private final Socket socket;
    private DataInputStream inputStream;
    private volatile OutputStream outputStream;
    private final boolean closeBecauseOfOverload;
    private Packet received;
    private volatile String username;
    private volatile UUID clientId;
    final Queue<Packet> gameRoomPacketQueue = new ArrayDeque<>();
    volatile boolean matchmaking;
    volatile GameRoom gameRoom;

    public ClientHandler(CheshkaServer cheshkaServer, Socket socket) {
        this(cheshkaServer, socket, false);
    }

    public ClientHandler(CheshkaServer cheshkaServer, Socket socket, boolean closeBecauseOfOverload) {
        this.cheshkaServer = cheshkaServer;
        this.socket = socket;
        this.closeBecauseOfOverload = closeBecauseOfOverload;
    }

    @Override
    public void run() {
        Throwable t = null;
        try (Socket socket = this.socket) {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = socket.getOutputStream();

            run0();
        } catch (Throwable th) {
            t = th;
        } finally {
            cheshkaServer.accessAuthenticatedUsers(authenticatedUsers -> authenticatedUsers.remove(this));

            if (username != null) {
                String logoutMessage = username + " disconnected";

                if (t == null) {
                    Log.i(logoutMessage);
                } else {
                    String reason = switch (t) {
                        case @SuppressWarnings("unused") EOFException eof -> "EOF";
                        case @SuppressWarnings("unused") IOException io -> "I/O issue";
                        case @SuppressWarnings("unused") Exception exception -> "exception";
                        default -> "error";
                    };
                    String fullMessage = logoutMessage + " (" + reason + ")";

                    if (t instanceof IOException /* including EOF */) {
                        Log.i(fullMessage);
                    } else {
                        Log.w(fullMessage, t);
                    }
                }
            }

            cheshkaServer.decrementOnlinePlayerCount();
        }
    }

    public void externalClose() {
        externalSendPacket(null);
    }

    public void externalSendPacket(Packet packet) {
        if (outputStream == null) return;

        //noinspection resource (?)
        ForkJoinPool.commonPool().execute(() -> {
            try {
                sendPacket(packet);
            } catch (IOException ignored) {
            }
        });
    }

    private void handleAcceptInvite(String invitationCode) throws IOException {
        if (!Helper.checkInvitationCode(invitationCode)) {
            sendPacket(new OpponentNotFound());

            return;
        }

        cheshkaServer.accessGameRooms(gameRooms -> {
            for (GameRoom room : gameRooms) {
                if (invitationCode.equals(room.getInvitationCode())) {
                    room.connectOpponentPlayer(this);

                    return;
                }
            }

            externalSendPacket(new OpponentNotFound());
        });
    }

    private void handleCreateInvite() {
        gameRoom = new GameRoom(cheshkaServer, this, true);

        cheshkaServer.accessGameRooms(gameRooms -> gameRooms.add(gameRoom));
    }

    private void handleRandomMatchmaking() {
        cheshkaServer.accessGameRooms(gameRooms -> {
            for (GameRoom room : gameRooms) {
                if (!room.hasInvitationCode() && room.isMatchmaking()) {
                    room.connectOpponentPlayer(this);

                    return;
                }
            }
            gameRoom = new GameRoom(cheshkaServer, this, false);

            gameRooms.add(gameRoom);
        });
    }

    private void run0() throws IOException {
        if (closeBecauseOfOverload) {
            sendDisconnect("Unfortunately, the server is currently overloaded. Please try again later");

            return;
        }
        receivePacket(ClientHello.class);
        ClientHello clientHello = (ClientHello) received;
        if (clientHello.magic != Helper.CLIENT_HELLO_MAGIC) {
            sendDisconnect("Unsupported protocol: bad magic value");

            return;
        }
        if (clientHello.protocolVersion != Helper.SERVER_VERSION_CODE) {
            boolean less = (clientHello.protocolVersion < Helper.SERVER_VERSION_CODE);
            String message;
            if (less) message = "Outdated client, please update to 1.1.1";
            else      message = "Outdated server, only 1.1d, 1.1d_2, 1.1 and 1.1.1 clients are supported";
            sendDisconnect(message);

            return;
        }
        sendPacket(new ServerHello());

        receivePacket(ClientIdentification.class);
        ClientIdentification identification = (ClientIdentification) received;
        String username = identification.username;
        if (!Helper.validateUsername(username)) {
            sendDisconnect("Bad username. Should be from " +
                    "2 to 16 characters long, should contain only 0-9, a-z, A-Z, dot and underscore symbols");

            return;
        }
        UUID clientId = identification.clientId;
        boolean noClientId = Helper.NULL_UUID_OBJ.equals(clientId);
        if (CheshkaServer.USE_CAPTCHA && (noClientId || !DB.getInstance().isUserVerified(clientId))) {
            int attempts = 0;
            while (attempts++ < CAPTCHA_ATTEMPTS) {
                Pair<BufferedImage, String> captcha = CaptchaProvider.generateCaptcha();

                IdentificationResult challengeRequired = new IdentificationResult();
                challengeRequired.success = false;
                challengeRequired.captcha = captcha.first();
                sendPacket(challengeRequired);

                String answer = captcha.second();
                //noinspection UnusedAssignment
                captcha = null;
                System.gc(); // give JVM a hint that it can free a pretty costly BufferedImage

                receivePacket(ClientIdentification.class);
                if (answer.equalsIgnoreCase(((ClientIdentification) received).captcha)) break;
            }
            if (attempts >= CAPTCHA_ATTEMPTS) {
                // in case of running out of attempts, should be equal to CAPTCHA_ATTEMPTS + 1
                sendDisconnect("Captcha challenge was not completed");

                return;
            }
            clientId = UUID.randomUUID();
            DB.getInstance().saveVerified(clientId);
        }
        if (!CheshkaServer.USE_CAPTCHA && noClientId) {
            clientId = UUID.randomUUID();
        }
        IdentificationResult result = new IdentificationResult();
        result.success = true;
        result.displayName = username;
        result.clientId = clientId;
        sendPacket(result);

        this.username = username;
        this.clientId = clientId;
        cheshkaServer.accessAuthenticatedUsers(authenticatedUsers -> authenticatedUsers.add(this));

        Log.i(username + " connected (" + socket.getInetAddress().getHostAddress() + ")");

        cheshkaServer.accessGameRooms(gameRooms -> {
            for (GameRoom room : gameRooms) {
                if (room.reconnectPlayer(this)) return;
            }
        });

        externalSendPacket(Helper.craftHomeData(cheshkaServer));

        int unexpectedInARow = 0;
        boolean incremented = false;
        while (true) {
            if (!incremented) unexpectedInARow = 0;
            incremented = false;

            receivePacket(null);
            Class<?> clazz = received.getClass();

            if (gameRoom != null && gameRoom.isObsolete()) {
                gameRoom = null;
                matchmaking = false;

                clearQueue();
            }
            if (gameRoom != null && HANDLED_BY_GAME_ROOM.contains(clazz)) {
                queueCurrentPacket();

                continue;
            }
            if (gameRoom == null && !matchmaking && received instanceof InitiateMatchmaking initiateMatchmaking) {
                boolean disconnect = false;
                String code = initiateMatchmaking.invitationCode;
                switch (initiateMatchmaking.mode) {
                    case InitiateMatchmaking.MODE_ACCEPT_INVITE -> handleAcceptInvite(code);
                    case InitiateMatchmaking.MODE_CREATE_INVITE -> handleCreateInvite();
                    case InitiateMatchmaking.MODE_RANDOM_OPPONENT -> handleRandomMatchmaking();
                    default -> {
                        disconnect = true;
                        sendDisconnect("Unknown InitiateMatchmaking mode");
                    }
                }
                if (disconnect) break;

                matchmaking = (initiateMatchmaking.mode != InitiateMatchmaking.MODE_ACCEPT_INVITE);
                if (matchmaking) {
                    MatchmakingStarted matchmakingStarted = new MatchmakingStarted();
                    if (gameRoom != null) {
                        matchmakingStarted.hasInvitationCode = gameRoom.hasInvitationCode();
                        matchmakingStarted.invitationCode = gameRoom.getInvitationCode();
                    }
                    sendPacket(matchmakingStarted);
                }

                continue;
            }
            if (++unexpectedInARow >= MAX_UNEXPECTED_PACKET_COUNT_IN_A_ROW) {
                sendDisconnect("Too many unexpected packets, the last one was " + clazz.getSimpleName());

                break;
            }

            incremented = true;
        }
    }

    private synchronized void queueCurrentPacket() {
        if (!(received instanceof MakeMove) && Helper.findPacket(this, received.getClass()) != null) return;

        if (gameRoomPacketQueue.size() >= MAX_PACKET_COUNT_IN_QUEUE) {
            disconnectAsync("Too many queued packets");

            return;
        }
        gameRoomPacketQueue.add(received);
    }

    private synchronized void clearQueue() {
        gameRoomPacketQueue.clear();
    }

    private void sendDisconnect(String reason) throws IOException {
        sendPacket(craftDisconnectPacket(reason));
    }

    @SuppressWarnings("SameParameterValue")
    public void disconnectAsync(String reason) {
        externalSendPacket(craftDisconnectPacket(reason));
        externalClose();
    }

    private Disconnect craftDisconnectPacket(String reason) {
        Disconnect disconnect = new Disconnect();
        disconnect.reason = reason;

        return disconnect;
    }

    private void sendPacket(Packet packet) throws IOException {
        if (packet == null) {
            close();

            return;
        }
        Class<?> clazz = packet.getClass();
        if (!clazz.isAnnotationPresent(Clientbound.class)) {
            throw new RuntimeException("Attempted to send " +
                    clazz.getName() + " packet which is not annotated as @Clientbound");
        }

        try {
            packet.send(outputStream);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Sending a packet", e);
        }
    }

    private void receivePacket(Class<? extends Packet> expecting) throws IOException {
        try {
            received = Packet.deserialize(inputStream);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Receiving a packet", e);
        }

        if (expecting != null && !expecting.isInstance(received)) {
            throw new IOException("Expected to receive " +
                    getClassName(expecting) + " packet, got " + getClassName(received));
        }
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    public void close() {
        Helper.close(socket);
    }

    public String getUsername() {
        return username;
    }

    public UUID getClientId() {
        return clientId;
    }
}
