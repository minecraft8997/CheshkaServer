package ru.deewend.cheshka.server.packet;

import java.awt.image.BufferedImage;
import java.util.UUID;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.OnlyWhen;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
public class IdentificationResult extends Packet {
    @Order(no = 1) public boolean success;
    @OnlyWhen(field = "success", is = "true")
    @Order(no = 2) public String displayName;
    @OnlyWhen(field = "success", is = "true")
    @Order(no = 3) public UUID clientId;
    @OnlyWhen(field = "success", is = "false")
    @Order(no = 4) public BufferedImage captcha;

    @Override
    public int getId() {
        return 0x01;
    }
}
