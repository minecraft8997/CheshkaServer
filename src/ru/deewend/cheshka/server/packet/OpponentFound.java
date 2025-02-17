package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Board;
import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
public class OpponentFound extends Packet {
    @Order(no = 1) public String opponentDisplayName;
    @Order(no = 2) public int boardSize;
    @Order(no = 3) public int secondsForTurn;
    @Order(no = 4) public final int noMoveDrawThreshold = Board.NO_MOVE_DRAW_THRESHOLD;
    @Order(no = 5) public String myPiecePositions;
    @Order(no = 6) public String opponentPiecePositions;
    @Order(no = 7) public boolean whiteColor;
    @Order(no = 8) public boolean myTurnNow;

    @Override
    public int getId() {
        return 0x04;
    }
}
