package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.packet.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ForkJoinPool;

import static ru.deewend.cheshka.server.Helper.getClassName;

public class ClientHandler implements Runnable {
    public static final int MAX_PACKET_COUNT_IN_QUEUE = 50;

    private final CheshkaServer cheshkaServer;
    private final Socket socket;
    private DataInputStream inputStream;
    private volatile OutputStream outputStream;
    private final boolean closeBecauseOfOverload;
    private Packet received;
    private String username;
    private final Queue<Packet> packetQueue = new ArrayDeque<>();
    final Queue<Packet> gameRoomPacketQueue = new ArrayDeque<>();
    private boolean host;
    private boolean matchmaking;
    GameRoom gameRoom;

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
            if (username != null) {
                String logoutMessage = username + " disconnected";

                if (t == null) Log.i(logoutMessage);
                else           Log.w(logoutMessage, t);
            }

            cheshkaServer.decrementOnlinePlayerCount();
        }
    }

    public void externalClose() {
        externalSendPacket(null);
    }

    public void externalSendPacket(Packet packet) {
        if (outputStream == null) return;

        //noinspection resource
        ForkJoinPool.commonPool().execute(() -> {
            try {
                sendPacket(packet);
            } catch (IOException ignored) {
            }
        });
    }

    private boolean handleAcceptInvite(String invitationCode) {
        cheshkaServer.accessGameRooms(gameRooms -> {

        });

        return false;
    }

    private boolean handleCreateInvite() {
        return true;
    }

    private boolean handleRandomMatchmaking() {
        throw new UnsupportedOperationException();
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
        if (clientHello.protocolVersion != 1) {
            sendDisconnect("Unsupported protocol version. The server software is probably outdated");

            return;
        }
        sendPacket(new ServerHello());

        receivePacket(ClientIdentification.class);
        ClientIdentification identification = (ClientIdentification) received;
        if (!Helper.validateUsername(identification.username)) {
            sendDisconnect("Bad username. Should be from " +
                    "2 to 16 characters long, should contain only 0-9, a-z, A-Z, dot and underscore symbols");

            return;
        }
        // of course, we'll need to implement a lot of checks here
        if (!Helper.NULL_UUID_OBJ.equals(identification.clientId)) {
            sendDisconnect("Unexpected UUID (?)");

            return;
        }
        IdentificationResult result = new IdentificationResult();
        result.success = true;
        result.displayName = identification.username + " (test)";
        result.clientId = identification.clientId;
        sendPacket(result);

        username = identification.username;
        Log.i(username + " connected");

        HomeData homeData = new HomeData();
        homeData.onlinePlayerCount = cheshkaServer.getOnlinePlayerCount();
        cheshkaServer.accessGameRooms(gameRooms -> homeData.activeGamesCount = gameRooms.size());
        sendPacket(homeData);

        while (true) {
            receivePacket(null);

            if (gameRoom != null && (received instanceof RollDice || received instanceof MakeMove)) {
                queueCurrentPacket();

                continue;
            }
            if (gameRoom == null && !matchmaking && received instanceof InitiateMatchmaking initiateMatchmaking) {
                boolean disconnect = false;
                String code = initiateMatchmaking.invitationCode;
                boolean shouldMarkMatchmaking = switch (initiateMatchmaking.mode) {
                    case InitiateMatchmaking.MODE_ACCEPT_INVITE -> handleAcceptInvite(code);
                    case InitiateMatchmaking.MODE_CREATE_INVITE -> handleCreateInvite();
                    case InitiateMatchmaking.MODE_RANDOM_OPPONENT -> handleRandomMatchmaking();
                    default -> {
                        disconnect = true;
                        sendDisconnect("Unknown InitiateMatchmaking mode");

                        yield false;
                    }
                };
                if (disconnect) break;

                if (shouldMarkMatchmaking) {
                    matchmaking = true;

                    MatchmakingStarted matchmakingStarted = new MatchmakingStarted();
                    //noinspection DataFlowIssue (if shouldMarkMatchmaking is true, then gameRoom != null)
                    matchmakingStarted.hasInvitationCode = gameRoom.hasInvitationCode();
                    matchmakingStarted.invitationCode = gameRoom.getInvitationCode();
                    sendPacket(matchmakingStarted);
                }
            }
            sendDisconnect("Unexpected packet");

            break;
        }
    }

    private void queueCurrentPacket() throws IOException {
        synchronized (this) {
            if (packetQueue.size() >= MAX_PACKET_COUNT_IN_QUEUE) {
                sendDisconnect("Too many packets");

                return;
            }
            packetQueue.add(received);
        }
    }

    private void sendDisconnect(String reason) throws IOException {
        sendPacket(craftDisconnectPacket(reason));
    }

    @SuppressWarnings("SameParameterValue")
    private void sendDisconnectAsync(String reason) {
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
            throw new RuntimeException("Sending a packet", e);
        }

        if (expecting != null && !expecting.isInstance(received)) {
            throw new IOException("expected to receive " +
                    getClassName(expecting) + " packet, got" + getClassName(received));
        }
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    public void close() {
        Helper.close(socket);
    }
}
