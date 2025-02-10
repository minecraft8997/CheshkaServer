package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.packet.Disconnect;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import static ru.deewend.cheshka.server.Helper.getClassName;

public class ClientHandler implements Runnable {
    private final CheshkaServer cheshkaServer;
    private final Socket socket;
    private DataInputStream inputStream;
    private OutputStream outputStream;
    private Packet received;
    private final boolean closeBecauseOfOverload;
    private boolean host;
    private GameRoom gameRoom;

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
            Log.i("A player (%s) disconnected!", socket.toString());
            if (t != null) {
                Log.i("... but it happened due to an error (%s)", socket.toString());
                t.printStackTrace();
            }
            cheshkaServer.decrementOnlinePlayerCount();

            if (gameRoom != null) {
                ClientHandler handler;
                if (host) handler = gameRoom.getOpponentPlayerHandler();
                else      handler = gameRoom.getHostPlayerHandler();

                if (handler != null && !handler.isClosed()) {
                    Helper.sendMessageIgnoreErrors(handler,
                            "disconnect:opponent_disconnected");
                    handler.close();
                }

                cheshkaServer.accessGameRooms(gameRooms -> {
                    gameRooms.remove(gameRoom.getInvitationCode()); return true;
                });
            }
        }
    }

    private void run0() throws Throwable {
        if (closeBecauseOfOverload) {
            Disconnect disconnect = new Disconnect();
            disconnect.reason = "Unfortunately, the server is currently overloaded. Please try again later";
            sendPacket(disconnect);

            return;
        }

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
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}
