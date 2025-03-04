package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Serverbound;

@Clientbound
@Serverbound
public class Resign extends Packet {
    @Override
    public int getId() {
        return 0x09;
    }
}
