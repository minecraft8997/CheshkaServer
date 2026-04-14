package com.deewend.cheshka.server.packet;

import java.util.UUID;

import com.deewend.cheshka.server.Packet;
import com.deewend.cheshka.server.annotation.Order;
import com.deewend.cheshka.server.annotation.Serverbound;

@Serverbound
public class ClientIdentification extends Packet {
    @Order(no = 1) public String username;
    @Order(no = 2) public String captcha;
    @Order(no = 3) public UUID clientId;

    @Override
    public int getId() {
        return 0x01;
    }
}
