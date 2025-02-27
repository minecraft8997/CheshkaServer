package ru.deewend.cheshka.server;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DB {
    public static final boolean DISABLE_AUTOMATIC_DB_CLEANUP =
            Boolean.parseBoolean(Helper.getProperty("disableAutomaticDBCleanup", "false"));
    public static final double MAX_CAPTCHA_VERIFICATION_AGE_DAYS =
            Double.parseDouble(Helper.getProperty("maxCaptchaVerificationAgeDays", "180"));
    public static final double MAX_OUTDATED_DB_ENTRIES_PERCENTAGE =
            Double.parseDouble(Helper.getProperty("maxOutdatedDBEntriesPercentage", "15"));
    public static final double OUTDATED_DB_ENTRIES_SCAN_INTERVAL_DAYS =
            Double.parseDouble(Helper.getProperty("outdatedDBEntriesScanIntervalDays", "7"));

    private static final long MAX_CAPTCHA_VERIFICATION_AGE_MS =
            (long) (TimeUnit.DAYS.toMillis(1L) * MAX_CAPTCHA_VERIFICATION_AGE_DAYS);
    private static final double MAX_OUTDATED_DB_ENTRIES_FRACTION = MAX_OUTDATED_DB_ENTRIES_PERCENTAGE / 100.0D;
    private static final double OUTDATED_DB_ENTRIES_SCAN_INTERVAL_MS =
            (long) (TimeUnit.DAYS.toMillis(1L) * OUTDATED_DB_ENTRIES_SCAN_INTERVAL_DAYS);

    private static final DB INSTANCE = new DB();

    private final File dbFile = new File("verifiedClientIdentifiers.db");
    private final File dbTmpFile = new File("verifiedClientIdentifiers.db.tmp");
    private long lastTimeScanned;

    private DB() {
    }

    public static DB getInstance() {
        return INSTANCE;
    }

    public void tick() {
        if (DISABLE_AUTOMATIC_DB_CLEANUP) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTimeScanned >= OUTDATED_DB_ENTRIES_SCAN_INTERVAL_MS) {
            Thread.ofPlatform().name("DB Scan and Cleanup").start(() -> runScanCleanup(true, 0));

            lastTimeScanned = currentTime;
        }
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private synchronized void runScanCleanup(boolean scan, int expectedEntriesCount) {
        byte[] dummy = (scan ? new byte[7] : null);
        int entries = 0;
        int outdatedEntries = 0;
        boolean readingEntry = false;
        try (
                DataInputStream stream = openDBInputStream();
                /*
                 * The second stream is a thing only when we're performing a cleanup.
                 */
                DataOutputStream tmpStream =
                        new DataOutputStream(!scan ? openBufferedOutputStream(dbTmpFile) : new ByteArrayOutputStream())
        ) {
            while (true) {
                long most = 0L;
                long least;
                if (scan) {
                    stream.readByte(); // uuid, most significant bits, first byte
                    readingEntry = true;
                    stream.readFully(dummy); // uuid, most significant bits, remaining 7 bytes
                } else {
                    most = stream.readLong();
                }
                least = stream.readLong(); // uuid, least significant bits
                long timestamp = stream.readLong();
                readingEntry = false;

                if (System.currentTimeMillis() - timestamp >= MAX_CAPTCHA_VERIFICATION_AGE_MS) {
                    outdatedEntries++;
                } else if (!scan) {
                    tmpStream.writeLong(most);
                    tmpStream.writeLong(least);
                    tmpStream.writeLong(timestamp);
                }
                entries++;
            }
        } catch (IOException e) {
            if (e instanceof NoSuchFileException) return;

            if (!(e instanceof EOFException)) {
                String state = (scan ? "scan" : "cleanup");
                Log.w("Unexpected I/O issue when maintaining DB. State: " + state, e);

                return;
            }
        }
        if (!scan) {
            try {
                if (entries != expectedEntriesCount) {
                    Log.w("Cancelling the cleanup, found " + entries + " entries " +
                            "when the expected count was " + expectedEntriesCount);

                    return;
                }
                if (!dbFile.delete()) {
                    Log.w("Failed to delete the outdated DB file. This might affect the " +
                            "result of the further rename operation against the newly created DB file");
                }
                if (!dbTmpFile.renameTo(dbFile)) {
                    Log.w("Failed to replace the old DB with the cleaned one");

                    return;
                }
                Log.i("Successful cleanup");
            } finally {
                if (!dbTmpFile.delete()) {
                    Log.w("Failed to remove the temporary DB file");
                }
            }

            return;
        }
        if (readingEntry) {
            Log.w("Got an EOF while reading an entry, the DB might be corrupted");
        }
        if (entries == 0) return;

        double f = (double) outdatedEntries / entries;
        if (f < MAX_OUTDATED_DB_ENTRIES_FRACTION) return;

        Log.i("DB cleanup is required");

        runScanCleanup(false, entries);
    }

    private DataInputStream openDBInputStream(OpenOption... additionalOptions) throws IOException {
        return new DataInputStream(openBufferedInputStream(dbFile, additionalOptions));
    }

    private BufferedInputStream openBufferedInputStream(File file, OpenOption... additionalOptions) throws IOException {
        return new BufferedInputStream(Files.newInputStream(file.toPath(), generateOptions(additionalOptions)));
    }

    private DataOutputStream openDBOutputStream(OpenOption... additionalOptions) throws IOException {
        return new DataOutputStream(openBufferedOutputStream(dbFile, additionalOptions));
    }

    private BufferedOutputStream openBufferedOutputStream(
            File file, OpenOption... additionalOptions
    ) throws IOException {
        return new BufferedOutputStream(Files.newOutputStream(file.toPath(), generateOptions(additionalOptions)));
    }

    private OpenOption[] generateOptions(OpenOption... additionalOptions) {
        OpenOption[] options;
        if (additionalOptions.length == 0) {
            /*
             * CREATE seems to do nothing against input streams opened via Files#newInputStream.
             */
            options = new OpenOption[] { StandardOpenOption.CREATE };
        } else {
            options = new OpenOption[additionalOptions.length + 1];
            options[0] = StandardOpenOption.CREATE;

            System.arraycopy(additionalOptions, 0, options, 1, additionalOptions.length);
        }

        return options;
    }

    public boolean isUserVerified(UUID clientId) {
        long most = clientId.getMostSignificantBits();
        long least = clientId.getLeastSignificantBits();

        synchronized (this) {
            try (DataInputStream stream = openDBInputStream()) {
                while (true) {
                    long currentMost = stream.readLong();
                    long currentLeast = stream.readLong();
                    long timestamp = stream.readLong();

                    if (most == currentMost && least == currentLeast) {
                        return System.currentTimeMillis() - timestamp < MAX_CAPTCHA_VERIFICATION_AGE_MS;
                    }
                }
            } catch (IOException e) {
                if (!(e instanceof EOFException || e instanceof NoSuchFileException)) Log.w("Reading DB", e);
            }
        }

        return false;
    }

    public void saveVerified(UUID clientId) {
        long most = clientId.getMostSignificantBits();
        long least = clientId.getLeastSignificantBits();
        long currentTime = System.currentTimeMillis();

        synchronized (this) {
            try (DataOutputStream stream = openDBOutputStream(StandardOpenOption.APPEND)) {
                stream.writeLong(most);
                stream.writeLong(least);
                stream.writeLong(currentTime);
            } catch (IOException e) {
                Log.w("Writing to DB", e);
            }
        }
    }
}
