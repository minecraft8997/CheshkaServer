package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;

@Clientbound
public class OpponentFound extends Packet {
    public String opponentDisplayName;
    public int boardSize;
    public int secondsForTurn;
    public String myPiecePositions;
    public String opponentPiecePositions;
    public boolean whiteColor;
    public boolean myTurnNow;

    @Override
    public int getId() {
        return 0x04;
    }
}
