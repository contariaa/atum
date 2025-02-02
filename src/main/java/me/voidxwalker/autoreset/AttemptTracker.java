package me.voidxwalker.autoreset;

import me.contaria.speedrunapi.config.SpeedrunConfigAPI;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class AttemptTracker {
    private final Map<Type, Counter> counters = new HashMap<>();
    private final Executor executor = Executors.newSingleThreadExecutor();

    AttemptTracker() throws IOException {
        this.register(Type.RSG);
        this.register(Type.SSG);
        this.register(Type.DEMO);
    }

    public void register(Type type) throws IOException {
        this.counters.put(type, new Counter(type));
    }

    public String getWorldName(Type type) {
        return type.worldName + this.counters.get(type).get();
    }

    public String incrementAndGetWorldName(Type type) {
        Counter counter = this.counters.get(type);
        int count = counter.incrementAndGet();
        this.executor.execute(counter::save);
        return type.worldName + count;
    }

    public static class Counter {
        private final AtomicInteger counter = new AtomicInteger();
        private final File attemptsFile;

        private Counter(Type type) throws IOException {
            this.attemptsFile = SpeedrunConfigAPI.getConfigDir().resolve("atum").resolve(type.fileName).toFile();
            Files.createDirectories(this.attemptsFile.getParentFile().toPath());
            if (!this.attemptsFile.createNewFile()) {
                this.read();
            }
            this.save();
        }

        private int get() {
            return this.counter.get();
        }

        private int incrementAndGet() {
            return this.counter.incrementAndGet();
        }

        private void read() {
            try {
                this.counter.set(Integer.parseInt(new String(Files.readAllBytes(this.attemptsFile.toPath()))));
            } catch (IOException e) {
                Atum.LOGGER.error("Failed to read attempts file: {}", this.attemptsFile.getName(), e);
            } catch (NumberFormatException e) {
                Atum.LOGGER.error("Failed to parse attempts file: {}", this.attemptsFile.getName(), e);
            }
        }

        private void save() {
            try {
                Files.write(this.attemptsFile.toPath(), String.valueOf(this.get()).getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Atum.LOGGER.error("Failed to save attempts file: {}", this.attemptsFile.getName(), e);
            }
        }
    }

    public static class Type {
        public static final Type RSG = new Type("Random Speedrun #", "rsg-attempts.txt");
        public static final Type SSG = new Type("Set Speedrun #", "ssg-attempts.txt");
        public static final Type DEMO = new Type("Demo Speedrun #", "demo-attempts.txt");

        private final String worldName;
        private final String fileName;

        public Type(String worldName, String fileName) {
            this.worldName = worldName;
            this.fileName = fileName;
        }
    }
}
