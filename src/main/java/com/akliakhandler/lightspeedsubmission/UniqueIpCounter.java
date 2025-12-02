package com.akliakhandler.lightspeedsubmission;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.IntStream;

public class UniqueIpCounter {
    private static final int BITMAP_SIZE = 1 << 27;
    private static final AtomicIntegerArray bitmap = new AtomicIntegerArray(BITMAP_SIZE);

    public static void main(String[] args) throws Exception {
        String filePath = "src/main/resources/ips.txt";

        LocalDateTime start = LocalDateTime.now();
        System.out.println(countUniqueBitmap(filePath) + " " + Duration.between(start, LocalDateTime.now()).toSeconds());
    }

    public static long countUniqueBitmap(String path) throws Exception {
        int threads = Runtime.getRuntime().availableProcessors();

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        try (
                RandomAccessFile raf = new RandomAccessFile(path, "r");
                FileChannel channel = raf.getChannel()
        ) {

            long fileSize = channel.size();
            long chunkSize = fileSize / threads;

            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                long start = i * chunkSize;
                long end = (i == threads - 1) ? fileSize : (i + 1) * chunkSize;

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        processChunk(channel, start, end);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
        }

        return IntStream.range(0, BITMAP_SIZE)
                .parallel()
                .map(i -> Integer.bitCount(bitmap.get(i)))
                .mapToLong(i -> i)
                .sum();
    }

    private static void processChunk(FileChannel channel, long start, long end) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
        long position = start;
        if (start > 0) {
            buffer.limit(0);

            long tempPos = start;
            ByteBuffer tempBuf = ByteBuffer.allocate(128);
            boolean newlineFound = false;
            while (!newlineFound && tempPos < end) {
                tempBuf.clear();
                channel.read(tempBuf, tempPos);
                tempBuf.flip();
                while (tempBuf.hasRemaining()) {
                    byte b = tempBuf.get();
                    tempPos++;
                    if (b == '\n') {
                        newlineFound = true;
                        break;
                    }
                }
            }
            position = tempPos;
        }

        int currentIp = 0;
        int currentOctet = 0;
        boolean hasPending = false;

        while (position < end || hasPending) {

            buffer.clear();
            int bytesRead = channel.read(buffer, position);
            if (bytesRead == -1) break;

            buffer.flip();
            position += bytesRead;

            while (buffer.hasRemaining()) {
                byte b = buffer.get();

                if (position - buffer.remaining() - 1 >= end) {
                    if (b == '\n') {
                        if (hasPending) {
                            updateBitmap((currentIp << 8) | currentOctet);
                        }
                        return;
                    }
                }

                if (b >= '0' && b <= '9') {
                    currentOctet = currentOctet * 10 + (b - '0');
                    hasPending = true;
                } else if (b == '.') {
                    currentIp = (currentIp << 8) | currentOctet;
                    currentOctet = 0;
                } else if (b == '\n') {
                    if (hasPending) {
                        updateBitmap((currentIp << 8) | currentOctet);
                        currentIp = 0;
                        currentOctet = 0;
                        hasPending = false;
                    }
                }
            }
        }

        if (hasPending) {
            updateBitmap((currentIp << 8) | currentOctet);
        }
    }

    private static void updateBitmap(int ip) {
        int idx = ip >>> 5;
        int mask = 1 << (ip & 31);

        int current = bitmap.get(idx);
        if ((current & mask) == mask) {
            return;
        }
        bitmap.getAndAccumulate(idx, mask, (prev, x) -> prev | x);
    }
}
