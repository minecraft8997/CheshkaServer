package com.deewend.cheshka.server.packet;

import com.deewend.cheshka.server.Packet;
import com.deewend.cheshka.server.annotation.Clientbound;
import com.deewend.cheshka.server.annotation.Order;

@Clientbound
public class DiceRolled extends Packet {
    @Order(no = 1) public byte value;

    @Override
    public int getId() {
        return 0x05;
    }
}
