package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;

@Clientbound
public class Disconnect extends Packet {
    public String reason;

    @Override
    public int getId() {
        return 0x08;
    }
}
