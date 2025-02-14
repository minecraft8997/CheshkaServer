package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.packet.OpponentFound;
import ru.deewend.cheshka.server.packet.OpponentNotFound;

import java.util.Queue;
import java.util.Random;
import java.util.UUID;

public class GameRoom {
    public static final int WAITING_FOR_OPPONENT_TIMEOUT_SECONDS =
            Integer.parseInt(Helper.getProperty("waitingForOpponentTimeoutSeconds", "900"));
    public static final int TURN_WAITING_TIMEOUT_SECONDS =
            Integer.parseInt(Helper.getProperty("turnWaitingTimeout", "10"));

    private static final int WAITING_FOR_OPPONENT_TIMEOUT_TICKS =
            WAITING_FOR_OPPONENT_TIMEOUT_SECONDS * CheshkaServer.TICK_RATE_HZ;

    private final Random random;
    private ClientHandler hostPlayer;
    private boolean matchmaking = true;
    private String invitationCode;
    private final boolean hostColor; // true = white, false = black
    private Board board;
    private volatile ClientHandler opponentPlayer;
    private volatile ClientHandler whoseTurn;
    private int waitingForOpponentTicks;
    private volatile boolean obsolete;

    public GameRoom(CheshkaServer cheshkaServer, ClientHandler hostPlayer, boolean hasInvitationCode) {
        random = cheshkaServer.getRandom();
        this.hostPlayer = hostPlayer;

        if (hasInvitationCode) {
            byte[] b = new byte[2];
            random.nextBytes(b);
            int v1 = Byte.toUnsignedInt(b[0]);
            int v2 = Byte.toUnsignedInt(b[1]);
            String v1s = Integer.toHexString(v1);
            String v2s = Integer.toHexString(v2);

            if (v1s.length() == 1) v1s = "0" + v1s;
            if (v2s.length() == 1) v2s = "0" + v2s;
            invitationCode = (v1s + v2s)
                    .replace("dead", String.valueOf(1000 + random.nextInt(9000)))
                    .replace("666", "545"); // replace inappropriate codes
        } else {
            invitationCode = null;
        }
        this.hostColor = random.nextBoolean();
    }

    public boolean tick() {
        if (matchmaking) {
            if (++waitingForOpponentTicks >= WAITING_FOR_OPPONENT_TIMEOUT_TICKS) {
                hostPlayer.externalSendPacket(new OpponentNotFound());

                return false;
            }
            /*
             * if (hostPlayer.isClosed()) return false; // host has left, the game won't start
             * return true;
             */
            return !hostPlayer.isClosed();
        }
        if (board == null) {
            board = new Board(random, CheshkaServer.BOARD_SIZE);

            sendOpponentFound(hostPlayer);
            sendOpponentFound(opponentPlayer);
            whoseTurn = (hostColor ? hostPlayer : opponentPlayer);
        }
        //noinspection SynchronizeOnNonFinalField
        synchronized (whoseTurn) {
            Queue<Packet> queue = whoseTurn.gameRoomPacketQueue;
            while (!queue.isEmpty()) {
                Packet packet = queue.remove();

                board.rollDice()
            }
        }

        return true;
    }

    public boolean reconnectPlayer(ClientHandler player) {
        if (board == null) return false;

        boolean host;
        UUID playerUUID = player.getClientId();
        if (!(host = playerUUID.equals(getHostPlayerUUID())) && !playerUUID.equals(getOpponentPlayerUUID())) {
            return false;
        }
        ClientHandler toDisconnect = (host ? hostPlayer : opponentPlayer);
        /*
         * Most likely this is just a reconnect and this.opponentPlayer is already disconnected.
         */
        toDisconnect.disconnectAsync("Someone with your credentials has connected to the " +
                "server. If it was not you, try clearing Cheshka application data in your settings");
        if (host) {
            hostPlayer = player;
        } else {
            opponentPlayer = player;
        }
        if (whoseTurn == toDisconnect) whoseTurn = player;

        sendOpponentFound(player);

        return true;
    }

    public void connectOpponentPlayer(ClientHandler opponentPlayer) {
        if (this.opponentPlayer != null) return;

        if (matchmaking) matchmaking = false;
        if (hasInvitationCode()) invitationCode = null;

        this.opponentPlayer = opponentPlayer;
    }

    public void sendOpponentFound(ClientHandler handler) { // board is non-null in both cases when this method is called
        OpponentFound opponentFound = new OpponentFound();
        ClientHandler opponent = (handler == hostPlayer ? opponentPlayer : hostPlayer);
        opponentFound.opponentDisplayName = opponent.getUsername();
        opponentFound.boardSize = CheshkaServer.BOARD_SIZE;
        opponentFound.secondsForTurn = TURN_WAITING_TIMEOUT_SECONDS;
        boolean myColor = ((handler == hostPlayer) == hostColor); // (handler == hostPlayer ? hostColor : !hostColor)
        opponentFound.myPiecePositions = board.serializePosition(myColor);
        opponentFound.opponentPiecePositions = board.serializePosition(!myColor);
        opponentFound.whiteColor = myColor;
        opponentFound.myTurnNow = (handler == whoseTurn);

        handler.externalSendPacket(opponentFound);
    }

    public void cleanup() {
        board = null;
        obsolete = true;
    }

    public UUID getHostPlayerUUID() {
        return hostPlayer.getClientId();
    }

    public UUID getOpponentPlayerUUID() {
        return (opponentPlayer != null ? opponentPlayer.getClientId() : null);
    }

    public boolean isMatchmaking() {
        return matchmaking;
    }

    public boolean hasInvitationCode() {
        return invitationCode != null;
    }

    public String getInvitationCode() {
        return invitationCode;
    }

    public boolean isObsolete() {
        return obsolete;
    }
}
