package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
public class Disconnect extends Packet {
    @Order(no = 1) public String reason;

    @Override
    public int getId() {
        return 0x08;
    }
}
