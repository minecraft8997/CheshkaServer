package com.deewend.cheshka.server.packet;

import com.deewend.cheshka.server.Packet;
import com.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class RollDice extends Packet {
    @Override
    public int getId() {
        return 0x03;
    }
}
