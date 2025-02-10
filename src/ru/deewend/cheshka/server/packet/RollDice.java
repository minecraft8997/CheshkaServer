package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class RollDice extends Packet {
    @Override
    public int getId() {
        return 0x03;
    }
}
