package ru.deewend.cheshka.server.packet;

import java.awt.image.BufferedImage;
import java.util.UUID;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.OnlyWhen;

@Clientbound
public class IdentificationResult extends Packet {
    public boolean success;
    @OnlyWhen(field = "success", is = "true")
    public String displayName;
    @OnlyWhen(field = "success", is = "true")
    public UUID clientId;
    @OnlyWhen(field = "success", is = "false")
    public BufferedImage captcha;

    @Override
    public int getId() {
        return 0x01;
    }
}
