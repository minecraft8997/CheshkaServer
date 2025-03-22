package ru.deewend.cheshka.server;

import ru.deewend.cheshka.server.annotation.OnlyWhen;
import ru.deewend.cheshka.server.annotation.Serverbound;
import ru.deewend.cheshka.server.packet.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;

public abstract class Packet {
    @SuppressWarnings("rawtypes")
    private static final Class[] serverboundPIDMappings;

    static {
        serverboundPIDMappings = new Class[10];
        serverboundPIDMappings[0x00] = ClientHello.class;
        serverboundPIDMappings[0x01] = ClientIdentification.class;
        serverboundPIDMappings[0x02] = InitiateMatchmaking.class;
        serverboundPIDMappings[0x03] = RollDice.class;
        serverboundPIDMappings[0x04] = CancelMatchmaking.class;
        serverboundPIDMappings[0x06] = MakeMove.class;
        serverboundPIDMappings[0x09] = Resign.class;

        for (Class<?> clazz : serverboundPIDMappings) {
            if (clazz == null) continue;

            if (!clazz.isAnnotationPresent(Serverbound.class)) {
                throw new RuntimeException(clazz.getName() +
                        " packet is expected to be annotated as @Serverbound");
            }
        }
    }

    private static boolean shouldContinue(
            Field field, Packet packet, Class<?> cachedClass
    ) throws NoSuchFieldException, IllegalAccessException {
        if (Modifier.isStatic(field.getModifiers())) return true;

        if (cachedClass == null) {
            cachedClass = packet.getClass();
        }
        OnlyWhen annotation = field.getAnnotation(OnlyWhen.class);
        if (annotation != null) {
            String fieldValue =
                    String.valueOf(cachedClass.getField(annotation.field()).get(packet));

            return !fieldValue.equals(annotation.is());
        }

        return false;
    }

    public static Packet deserialize(DataInputStream stream) throws IOException, ReflectiveOperationException {
        int pid = stream.readUnsignedByte();
        Class<?> clazz;
        if (pid >= serverboundPIDMappings.length || (clazz = serverboundPIDMappings[pid]) == null) {
            throw new IOException("Unknown serverbound packet id: " + pid);
        }
        Packet packet = (Packet) clazz.getDeclaredConstructor().newInstance();

        for (Field field : Helper.fixOrder(clazz.getFields())) {
            if (shouldContinue(field, packet, clazz)) continue;

            Class<?> type = field.getType();
            if (type == byte.class) {
                field.set(packet, stream.readByte());
            } else if (type == boolean.class) {
                field.set(packet, stream.readBoolean());
            } else if (type == int.class) {
                field.set(packet, stream.readInt());
            } else if (type == long.class) {
                field.set(packet, stream.readLong());
            } else if (type == UUID.class) {
                long most = stream.readLong();
                long least = stream.readLong();

                field.set(packet, new UUID(most, least));
            } else if (type == String.class) {
                field.set(packet, Helper.readString(stream));
            } else {
                throw new ReflectiveOperationException("Unsupported packet field type: " + type);
            }
        }

        return packet;
    }

    public abstract int getId();

    public final byte[] serialize() throws IOException, ReflectiveOperationException {
        byte[] result;
        try (ByteArrayOutputStream stream0 = new ByteArrayOutputStream()) {
            // closing ByteArrayOutputStream should not be mandatory though

            DataOutputStream stream = new DataOutputStream(stream0);
            int packetId = getId();
            stream.writeByte(packetId);

            Class<?> clazz = getClass();
            for (Field field : Helper.fixOrder(clazz.getFields())) {
                if (shouldContinue(field, this, clazz)) continue;

                Class<?> type = field.getType();
                if (type == byte.class) {
                    stream.writeByte(field.getByte(this));
                } else if (type == boolean.class) {
                    stream.writeBoolean(field.getBoolean(this));
                } else if (type == int.class) {
                    stream.writeInt(field.getInt(this));
                } else if (type == long.class) {
                    stream.writeLong(field.getLong(this));
                } else if (type == UUID.class) {
                    UUID uuid = (UUID) field.get(this);
                    if (uuid == null) uuid = Helper.NULL_UUID_OBJ;

                    stream.writeLong(uuid.getMostSignificantBits());
                    stream.writeLong(uuid.getLeastSignificantBits());
                } else if (type == String.class) {
                    String v = (String) field.get(this);
                    if (v == null) v = Helper.DEFAULT_STRING_VALUE;

                    Helper.writeString(stream, v);
                } else if (type == BufferedImage.class) {
                    BufferedImage image = (BufferedImage) field.get(this);
                    if (image == null) {
                        throw new NullPointerException("Null BufferedImage in a clientbound packet, id is " + packetId);
                    }

                    Helper.writeBufferedImage(stream, image);
                } else {
                    throw new ReflectiveOperationException("Unsupported packet field type: " + type);
                }
            }

            result = stream0.toByteArray();
        }

        return result;
    }

    public final void send(OutputStream stream) throws IOException, ReflectiveOperationException {
        stream.write(serialize());
        stream.flush();
    }
}
