package ru.deewend.cheshka.server;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.zip.CRC32;

public class Helper {
    public interface Providable<T> {
        boolean provide(T object) throws Exception;
    }

    public static final String DEFAULT_STRING_VALUE = "";
    public static final String NULL_UUID = "00000000-0000-0000-0000-000000000000";
    public static final UUID NULL_UUID_OBJ = UUID.fromString(NULL_UUID);
    public static final int MAX_FIELD_SIZE = 65535;

    /*
     * 11 is an attempt to represent H;
     * 5  is an attempt to represent S;
     * K  is skipped;
     *
     * CHESH[K]A.
     */
    public static final int CLIENT_HELLO_MAGIC = 0xC11E511A;

    /*
     * Expected to be equal to 0xDEE111ED.
     *
     * 111 is an attempt to represent W;
     * N   is skipped.
     *
     * DEEWE[N]D.
     */
    public static final int SERVER_HELLO_MAGIC = 0xDEE111ED;

    public static final int SECRET = 0xA115E11C; // reverted Helper.CLIENT_HELLO_MAGIC

    private Helper() {
    }

    public static String getProperty(String key, String defaultValue) {
        return System.getProperty(CheshkaServer.PROPERTY_PREFIX + key, defaultValue);
    }

    public static void newThread(String name, Runnable task, boolean platformThread) {
        if (platformThread) {
            Thread.ofPlatform().name(name).daemon().start(task);
        } else {
            Thread.ofVirtual().name(name).start(task); // already a daemon thread
        }
    }

    public static int calculateCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);

        return (int) crc32.getValue();
    }

    private static byte[] readByteArray(DataInputStream stream) throws IOException {
        int length = stream.readUnsignedShort();
        int signature = stream.readInt();

        byte[] contents = new byte[length];
        stream.readFully(contents);

        if ((signature ^ SECRET) != calculateCRC32(contents)) {
            throw new IOException("Bad signature");
        }

        return contents;
    }

    public static void writeByteArray(DataOutputStream stream, byte[] contents) throws IOException {
        int length = contents.length;

        if (length > MAX_FIELD_SIZE) {
            throw new RuntimeException("Array is too long");
        }
        stream.writeShort(length);
        int signature = calculateCRC32(contents) ^ SECRET;
        stream.writeInt(signature);

        stream.write(contents);
    }

    public static String readString(DataInputStream stream) throws IOException {
        byte[] contents = readByteArray(stream);

        return new String(contents, StandardCharsets.UTF_8);
    }

    public static void writeString(DataOutputStream stream, String str) throws IOException {
        byte[] contents = str.getBytes(StandardCharsets.UTF_8);

        writeByteArray(stream, contents);
    }

    public static void writeBufferedImage(DataOutputStream stream, BufferedImage image) throws IOException {
        ByteArrayOutputStream stream0 = new ByteArrayOutputStream(MAX_FIELD_SIZE / 2);
        ImageIO.write(image, "png", stream0);

        writeByteArray(stream, stream0.toByteArray());
    }

    public static void sendMessageIgnoreErrors(ClientHandler handler, String message) {
        try {
            handler.sendMessage(message);
        } catch (Throwable ignored) {}
    }

    public static byte[] constructCachedPacket(Providable<DataOutputStream> constructor) {
        try (ByteArrayOutputStream byteStream0 = new ByteArrayOutputStream()) {
            DataOutputStream byteStream = new DataOutputStream(byteStream0);

            constructor.provide(byteStream);

            return byteStream0.toByteArray();
        } catch (Exception e) { // should never happen tho
            throw new RuntimeException(e);
        }
    }

    public static String getClassName(Object object) {
        Class<?> clazz;
        if (object instanceof Class) {
            clazz = (Class<?>) object;
        } else {
            clazz = object.getClass();
        }

        return clazz.getName();
    }

    public static String readFully(String filename, String defaultValue) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }

            return builder.toString();
        } catch (IOException e) {
            if (e instanceof FileNotFoundException) {
                try (OutputStream stream = Files.newOutputStream(Paths.get(filename), StandardOpenOption.CREATE)) {
                    stream.write(defaultValue.getBytes(StandardCharsets.UTF_8));
                } catch (IOException ex) {
                    Log.w("Failed to create " + filename + " and write the default value", ex);
                }
            } else {
                Log.w("I/O issue when reading " + filename, e);
            }

            return defaultValue;
        }
    }
}
