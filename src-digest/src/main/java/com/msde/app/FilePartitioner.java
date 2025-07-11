package com.msde.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class FilePartitioner {

    public static ConcurrentMap<Long, Lock> locks = new ConcurrentHashMap<>();

    static {
        for (long i = 0; i < 1000; i++) {
            locks.computeIfAbsent(i, k -> new ReentrantLock());
        }

    }

    public static <T> List<List<T>> partitionFileList(List<T> files, int numberOfPartitions) {
        int partitionSize = files.size() / numberOfPartitions;
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < numberOfPartitions; i++) {
            int beg = i * partitionSize;
            int end = Math.min(beg + partitionSize, files.size());
            if (i == numberOfPartitions - 1) {
                end = files.size();
            }
            partitions.add(files.subList(beg, end));
        }
        return partitions;
    }

    public static void partitionFiles(List<File> partials) {
        /* Ensures that all the information regarding a callsite ends in the same 'callsite_xxx.txt' file,
        */
        int numberOfPartitions = 4;
        List<List<File>> partitions = partitionFileList(partials, numberOfPartitions);
        List<Integer> threadFinished = Arrays.asList(0, 0, 0, 0);
        List<Thread> threads = new ArrayList<>();
        int i = 0;
        for (List<File> binaryFiles : partitions) {
            int id = i++;
            Thread t = new Thread(() -> {
                int current = 0;
                for (File binaryFile : binaryFiles) {
                    writeIntermediateFiles(binaryFile);
                    current++;
                    threadFinished.set(id, current);
                }
            });
            t.start();
            threads.add(t);
        }
        Thread logThread = new Thread(() -> {
            while (threadFinished.stream().mapToInt(el -> el).sum() != partials.size()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    System.err.println(e.getMessage());
                }
                String progress = IntStream.range(0, threadFinished.size()).mapToObj(el -> String.format("%s - %s/%s", el, threadFinished.get(el), partitions.get(el).size())).collect(Collectors.joining(", "));
                System.out.print("\33[2K\rPartition progess: " + progress);
            }
            String progress = IntStream.range(0, threadFinished.size()).mapToObj(el -> String.format("%s - %s/%s", el, threadFinished.get(el), partitions.get(el).size())).collect(Collectors.joining(", "));
            System.out.print("\33[2K\rPartition progess: " + progress);
            System.out.println();
        });
        logThread.start();


        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }

        try {
            logThread.join();
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
        }

    }


    private static void writeIntermediateFiles(File binaryFile) {
        try {
            File intermediateFolder = new File("result/");
            intermediateFolder.mkdir();

            Map<Long, List<Byte>> fileIdToBytes = new HashMap<>();
            int readBytes = 0;
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(binaryFile.toString()))) {
                byte[] bytes = new byte[144 * 1024 * 1024];
                int len;
                outer:
                while ((len = in.read(bytes)) != -1) {
                    for (int i = 0; i < len; i += 24) {
                        long compileId = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i, i+8)).getLong();
                        byte[] cs = new byte[8];
                        cs[4] = bytes[i + 8];
                        cs[5] = bytes[i + 8 + 1];
                        cs[6] = bytes[i + 8 + 2];
                        cs[7] = bytes[i + 8 + 3];
                        long callsiteId = ByteBuffer.wrap(cs).getLong();
                        cs[4] = bytes[i +8 + 4];
                        cs[5] = bytes[i +8 + 5];
                        cs[6] = bytes[i +8 + 6];
                        cs[7] = bytes[i +8 + 7];
                        long classNameId = ByteBuffer.wrap(cs).getLong();
                        long timeDiff = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i + 16, i + 24)).getLong();
                        if (compileId == 0 && callsiteId == 0 && classNameId == 0 && timeDiff == 0) {
                            break outer;
                        }
                        long m = callsiteId % 1000;
                        List<Byte> toAdd = new ArrayList<>();
                        for (byte b : Arrays.copyOfRange(bytes, i, i + 24)) {
                            toAdd.add(b);
                        }
                        fileIdToBytes.computeIfAbsent(m, k -> new ArrayList<>()).addAll(toAdd);
                        readBytes += 24;
                        if (readBytes >= 144 * 1024 * 1024) {
                            dumpBytesToFile(intermediateFolder, fileIdToBytes);
                            fileIdToBytes.clear();
                        }
                    }
                }
            }
            dumpBytesToFile(intermediateFolder, fileIdToBytes);
            binaryFile.delete();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private static void dumpBytesToFile(File intermediateFolder, Map<Long, List<Byte>> fileIdToBytes) throws IOException {
        for (Map.Entry<Long, List<Byte>> entry : fileIdToBytes.entrySet()) {
            locks.get(entry.getKey()).lock();
            File csFile = new File(intermediateFolder, String.format("callsite_%03d.txt", entry.getKey()));
            if (!csFile.exists()) {
                csFile.createNewFile();
            }
            byte[] toWrite = new byte[entry.getValue().size()];
            int j = 0;
            for (Byte b : entry.getValue()) {
                toWrite[j++] = b;
            }
            Files.write(csFile.toPath(), toWrite, StandardOpenOption.APPEND);
            locks.get(entry.getKey()).unlock();
        }
    }

}
