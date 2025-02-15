package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.OnlyWhen;
import ru.deewend.cheshka.server.annotation.Order;

@Clientbound
public class MatchmakingStarted extends Packet {
    @Order(no = 1) public boolean hasInvitationCode;
    @OnlyWhen(field = "hasInvitationCode", is = "true")
    @Order(no = 2) public String invitationCode;

    @Override
    public int getId() {
        return 0x03;
    }
}
