package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Board;
import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
@SuppressWarnings("unused")
public class OpponentFound extends Packet {
    @Order(no = 1) public String opponentDisplayName;
    @Order(no = 2) public int boardSize;
    @Order(no = 3) public int secondsForTurn;
    @Order(no = 4) public final int noMoveDrawThreshold = Board.NO_MOVE_DRAW_THRESHOLD;
    @Order(no = 5) public int moveNumber;
    @Order(no = 6) public int subMoveNumber;
    @Order(no = 7) public String myPiecePositions;
    @Order(no = 8) public String opponentPiecePositions;
    @Order(no = 9) public boolean whiteColor;
    @Order(no = 10) public boolean myTurnNow;
    @Order(no = 11) public boolean lastChanceActivated;
    @Order(no = 12) public long ageMillis;
    @Order(no = 13) public long reserved;

    @Override
    public int getId() {
        return 0x04;
    }
}
