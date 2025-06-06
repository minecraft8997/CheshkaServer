package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
public class DiceRolled extends Packet {
    @Order(no = 1) public byte value;

    @Override
    public int getId() {
        return 0x05;
    }
}
