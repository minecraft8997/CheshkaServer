package ru.deewend.cheshka.server.packet;

import ru.deewend.cheshka.server.Packet;
import ru.deewend.cheshka.server.annotation.Clientbound;
import ru.deewend.cheshka.server.annotation.OnlyWhen;

@Clientbound
public class MatchmakingStarted extends Packet {
    public boolean hasInvitationCode;
    @OnlyWhen(field = "hasInvitationCode", is = "true")
    public String invitationCode;

    @Override
    public int getId() {
        return 0x03;
    }
}
