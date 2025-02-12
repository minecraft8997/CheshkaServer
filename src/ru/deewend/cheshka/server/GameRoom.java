package ru.deewend.cheshka.server;

import java.util.Random;

public class GameRoom {
    @SuppressWarnings("FieldCanBeLocal")
    private final CheshkaServer cheshkaServer;
    private final ClientHandler hostPlayer;
    private boolean matchmaking = true;
    private String invitationCode;
    private final boolean hostColor; // true = white, false = black
    private final Board board;
    private volatile ClientHandler opponentPlayer;
    private volatile ClientHandler whoseTurn;

    public GameRoom(CheshkaServer cheshkaServer, ClientHandler hostPlayer, boolean hasInvitationCode) {
        this.cheshkaServer = cheshkaServer;
        this.hostPlayer = hostPlayer;

        Random random = cheshkaServer.getRandom();
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
        this.board = new Board(cheshkaServer.getRandom(), CheshkaServer.BOARD_SIZE);
    }

    public boolean tick() {
        return true;
    }

    public void connectOpponentPlayer(ClientHandler opponentPlayer) {
        if (this.opponentPlayer != null) return;

        if (matchmaking) matchmaking = false;
        if (hasInvitationCode()) invitationCode = null;

        this.opponentPlayer = opponentPlayer;
    }

    public ClientHandler getHostPlayer() {
        return hostPlayer;
    }

    public ClientHandler getOpponentPlayer() {
        return opponentPlayer;
    }

    public ClientHandler getWhoseTurn() {
        return whoseTurn;
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

    public boolean getHostColor() {
        return hostColor;
    }
}
