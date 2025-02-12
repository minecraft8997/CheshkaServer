package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
@Clientbound
public class MakeMove extends Packet {
    public int moveNumber;
    public int subMoveNumber;
    public int piecePosition;
    public byte moveType;
    public boolean automatic;

    @Override
    public int getId() {
        return 0x06;
    }
}
