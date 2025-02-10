package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;

@Clientbound
public class OpponentNotFound extends Packet {
    @Override
    public int getId() {
        return 0x07;
    }
}
