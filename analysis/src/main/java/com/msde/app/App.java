package com.msde.app;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 */
public class App {
    public static ConcurrentMap<Long, Lock> locks = new ConcurrentHashMap<>();


    static{
        for(long i= 0; i<1000; i++){
            locks.computeIfAbsent(i, k -> new ReentrantLock());
        }
        
    }

    record Args(File inputFolder, long delta){}

    private static Args parseArgs(String[] args){
        Args parsedArgs = new Args(new File("/home/ubuntu/receiver-types-profiler/output"), 1000l);
        int i=0;
        while(i<args.length){
            String current = args[i++];
            switch(current){
                case "--input-folder", "-i" -> {
                    File inputFolder = new File(args[i++]);
                    parsedArgs = new Args(inputFolder, parsedArgs.delta);
                }
                case "--delta", "-d" -> {
                    long delta = Long.parseLong(args[i++]);
                    parsedArgs = new Args(parsedArgs.inputFolder, delta);
                }
                default -> {
                    System.out.println("usage: [--help] [--input-folder folder] [--delta time]"); 
                    System.exit(0);
                }
            }

        }
        return parsedArgs;
    }
    
    public static void main(String[] args) {
        Args arguments = parseArgs(args);
        InstrumentationFiles insFiles = getInstrumentationFiles(arguments.inputFolder);
        var idToCallsite = parseCsvMapping(insFiles.callsite);
        var idToClassName = parseCsvMapping(insFiles.className);
        long startTime = getStartTime(insFiles.startTime);

        // threads
        partitionFiles(Arrays.asList(insFiles.partials));

        File resultFolder = new File("result/");
        File[] callsiteFiles = resultFolder.listFiles((el) -> el.getName().startsWith("callsite_"));
        int i = 0;
        // callsiteFiles = Arrays.stream(callsiteFiles).filter(f -> f.length() > 800*1024*1024).toArray(File[]::new);
        for(File cf: callsiteFiles){
            System.out.print(String.format("\33[2K\rworking on file %s %s/%s size %s M" , cf,i, callsiteFiles.length, cf.length()/(1024*1024)));
            Optional<List<Long>> maybeInfo = readBinary(cf);
            // System.out.println("finished reading binary");
            if(maybeInfo.isEmpty()){
                System.err.println("Couldn't read binary file: " + cf.getAbsolutePath());
            }
            List<Long> info = maybeInfo.get();
            File resFile = new File(resultFolder, String.format("result_%s.txt", i++));
            try {
                resFile.createNewFile();
            } catch (IOException e) {
                System.err.println(e.getMessage());
                return;
            }
            var callsiteInfo = reconstructCallsiteInfo(info, idToCallsite, idToClassName);
            // System.out.println("finished reconstructing the callsites");
            // callsiteInfo = callsiteInfo.entrySet().stream().filter(e -> e.getKey().contains("36 Main")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            for (var entry : callsiteInfo.entrySet()) {
                String callsite = entry.getKey();
                var percentageWindows = analyseCallsite(entry.getValue(), arguments.delta);
                var changes = findChanges(percentageWindows);
                var inversions = findInversions(percentageWindows);
                if (changes.isEmpty() && inversions.isEmpty()) {
                    continue;
                }
                StringBuilder res = formatAnalysisResult(callsite, changes, inversions);
                // System.out.println(res);
                try {
                    Files.writeString(resFile.toPath(), res.toString(), StandardOpenOption.APPEND);
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
            // System.out.println("finished writing information to result file");
            
        }

    }

    private static void partitionFiles(List<File> partials) {
        int numberOfPartitions = 4;
        int partitionSize = partials.size()/ numberOfPartitions;
        List<List<File>> partitions = new ArrayList<List<File>>();
        for (int i = 0; i < numberOfPartitions; i++) {
            int beg = i * partitionSize;
            int end = Math.min(beg + partitionSize, partials.size());
            if (i == numberOfPartitions - 1) {
                end = partials.size();
            }
            partitions.add(partials.subList(beg, end));
        }

        List<Thread> threads = new ArrayList<>();
        int i = 0;
        for (List<File> binaryFiles : partitions) {
            int alpaca = i++;
            Thread t = new Thread(() -> {
                int filesNumber = binaryFiles.size();
                int current = 0;
                int id = alpaca;
                for (File binaryFile : binaryFiles) {
                    writeIntermediateFiles(binaryFile);
                    current++;
                    System.out.println(String.format("Progress id: %s - %s/%s", id, current, filesNumber));
                }
            });
            t.start();
            threads.add(t);
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
        
    }

    
    private static void writeIntermediateFiles(File binaryFile) {
        try {
            byte[] bytes = Files.readAllBytes(binaryFile.toPath());
            ByteBuffer.allocate(Long.BYTES).getLong();
            File intermediateFolder = new File("result/");
            intermediateFolder.mkdir();
            
            Map<Long, List<Byte>> fileIdToBytes = new HashMap<>();
            for (int i = 0; i < bytes.length; i += 16) {
                byte[] cs = new byte[8];
                cs[4] = bytes[i];
                cs[5] = bytes[i + 1];
                cs[6] = bytes[i + 2];
                cs[7] = bytes[i + 3];
                long callsiteId = ByteBuffer.wrap(cs).getLong();
                cs[4] = bytes[i + 4];
                cs[5] = bytes[i + 5];
                cs[6] = bytes[i + 6];
                cs[7] = bytes[i + 7];
                long classNameId = ByteBuffer.wrap(cs).getLong();
                long timeDiff = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i + 8, i + 16)).getLong();
                if (callsiteId == 0 && classNameId == 0 && timeDiff == 0) {
                    break;
                }
                long m = callsiteId % 1000;
                List<Byte> toAdd = new ArrayList<>();
                for(byte b: Arrays.copyOfRange(bytes, i, i+16)){
                    toAdd.add(b);
                }
                fileIdToBytes.computeIfAbsent(m, k-> new ArrayList<>()).addAll(toAdd);
            }
            for(Map.Entry<Long, List<Byte>> entry: fileIdToBytes.entrySet()){
                locks.get(entry.getKey()).lock();
                File csFile = new File(intermediateFolder, String.format("callsite_%s.txt", entry.getKey()));
                if(!csFile.exists()){
                    csFile.createNewFile();
                }
                byte[] toWrite = new byte[entry.getValue().size()];
                int i = 0;
                for(Byte b: entry.getValue()){
                    toWrite[i++] = b;
                }
                Files.write(csFile.toPath(), toWrite, StandardOpenOption.APPEND);
                locks.get(entry.getKey()).unlock();
            }
            binaryFile.delete();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    record InstrumentationFiles(File[] partials, File className, File callsite, File startTime) {
    }

    private static InstrumentationFiles getInstrumentationFiles(File inputFolder) {
        File[] partials = inputFolder.listFiles((el) -> el.getName().startsWith("partial"));
        File className = inputFolder.listFiles((el) -> el.getName().startsWith("className"))[0];
        File callsite = inputFolder.listFiles((el) -> el.getName().startsWith("callsite"))[0];
        File startTime = inputFolder.listFiles((el) -> el.getName().startsWith("start_time"))[0];
        return new InstrumentationFiles(partials, className, callsite, startTime);
    }

    private static Map<Long, String> parseCsvMapping(File inputFile) {
        try {
            List<String> lines = Files.readAllLines(inputFile.toPath());
            return lines.stream().map(l -> l.split(","))
                    .collect(Collectors.toMap(el -> Long.valueOf(el[1]), el -> el[0]));

        } catch (IOException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    private static long getStartTime(File startTimeFile) {
        try {
            String startTime = Files.readAllLines(startTimeFile.toPath()).get(0);
            return Long.parseLong(startTime);

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return 0L;
    }

    private static Optional<List<Long>> readBinary(File binaryFile) {
        try {
            byte[] bytes = Files.readAllBytes(binaryFile.toPath());
            ByteBuffer.allocate(Long.BYTES).getLong();
            List<Long> l = new ArrayList<>() {
            };
            for (int i = 0; i < bytes.length; i += 16) {
                byte[] cs = new byte[8];
                cs[4] = bytes[i];
                cs[5] = bytes[i + 1];
                cs[6] = bytes[i + 2];
                cs[7] = bytes[i + 3];
                long callsiteId = ByteBuffer.wrap(cs).getLong();
                cs[4] = bytes[i + 4];
                cs[5] = bytes[i + 5];
                cs[6] = bytes[i + 6];
                cs[7] = bytes[i + 7];
                long classNameId = ByteBuffer.wrap(cs).getLong();
                long timeDiff = ByteBuffer.wrap(Arrays.copyOfRange(bytes, i + 8, i + 16)).getLong();
                if (callsiteId == 0 && classNameId == 0 && timeDiff == 0) {
                    break;
                }
                l.add(callsiteId);
                l.add(classNameId);
                l.add(timeDiff);
            }
            return Optional.of(l);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return Optional.empty();
    }


    public static Map<String, Map<String, List<Long>>> reconstructCallsiteInfo(List<Long> info, Map<Long, String> idToCallsite, Map<Long, String> idToClassName) {
        Map<String, Map<String, List<Long>>> callsiteToInfo = new HashMap<>();
        for (int i = 0; i < info.size(); i += 3) {
            long csid = info.get(i);
            long cnid = info.get(i + 1);
            long timediff = info.get(i + 2);
            String callsite = idToCallsite.get(csid);
            String className = idToClassName.get(cnid);
            callsiteToInfo.computeIfAbsent(callsite, k -> new HashMap<>()).computeIfAbsent(className, k -> new ArrayList<>()).add(timediff);
        }
        return callsiteToInfo;

    }


    private static List<Map<String, Double>> analyseCallsite(Map<String, List<Long>> info, long timeFrame) {
        // returns a list of windows which shows the percentages of the calls on each receiver type in that window
        long windowStart = info.values().stream().flatMap(List::stream).mapToLong(e -> e).min().orElse(0);
        long end = info.values().stream().flatMap(List::stream).mapToLong(e -> e).max().orElse(Long.MAX_VALUE);
        int i =0;
        long nIteration = ((end-windowStart)/timeFrame) + 1;
        List<Map<String, List<Long>>> windows = new ArrayList<>();
        for(int j = 0; j<nIteration; j++){
            windows.add(new HashMap<>());            
        }
        for (Map.Entry<String, List<Long>> el : info.entrySet()) {
            List<List<Long>> partitionedInfo = new ArrayList<>();
            for(int j=0; j<nIteration; j++){
                partitionedInfo.add(new ArrayList<>());
            }
            
            for(Long l: el.getValue()){
                int index = (int) ((l-windowStart)/timeFrame);
                partitionedInfo.get(index).add(l);
            }
            for(int j=0; j<partitionedInfo.size();j++){
                windows.get(j).put(el.getKey(), partitionedInfo.get(j));
            }
        }
        // [{k: len(v) / sum(map(len, e.values())) for k, v in e.items()} for e in windows]
        // ["alpaca": [1,2,3], "coniglio":[4,5,6]] -> ["alpaca": 2, "coniglio": 7.5]
        return windows.stream().map(m -> {
            double tot = m.values().stream().mapToDouble(List::size).sum();
            return m.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().size() / tot))
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        }).toList();
    }

    public record RatioChange(int window, String className, double before, double after, double diff) {
    }

    private static List<RatioChange> findChanges(List<Map<String, Double>> pw) {
        double threshold = 0.1;
        List<RatioChange> changes = new ArrayList<>();
        for (int i = 0; i < pw.size() - 1; i++) {
            var w1 = pw.get(i);
            var w2 = pw.get(i + 1);
            for (String k : w1.keySet()) {
                var v1 = w1.get(k);
                var v2 = w2.get(k);
                double diff = Math.abs(v1 - v2);
                if (diff > threshold) {
                    changes.add(new RatioChange(i, k, v1, v2, diff));
                }
            }
        }
        return changes;
    }

    public record Inversion(int window1, int window2, String className1, String className2) {
    }

    private static List<Inversion> findInversions(List<Map<String, Double>> pw) {
        List<Inversion> inversions = new ArrayList<>();
        for (int i = 0; i < pw.size() - 1; i++) {
            var w1 = pw.get(i);
            var w2 = pw.get(i + 1);

            String[] keys = w1.keySet().toArray(String[]::new);
            for (int j = 0; j < w1.size() - 1; j++) {
                String k1 = keys[j];
                String k2 = keys[j + 1];
                Double valKey1Window1 = w1.get(k1);
                Double valKey2Window1 = w1.get(k2);
                Double valKey1Window2 = w2.get(k1);
                Double valKey2Window2 = w2.get(k2);
                if (valKey1Window1.compareTo(valKey2Window1) != valKey1Window2.compareTo(valKey2Window2)) {
                    inversions.add(new Inversion(i, i + 1, k1, k2));
                }
            }
        }
        return inversions;
    }

    private static StringBuilder formatAnalysisResult(String cs, List<RatioChange> changes, List<Inversion> inversions) {
        StringBuilder result = new StringBuilder(String.format("Callsite: %s\n", cs));
        String indent = "    ";
        if (!changes.isEmpty()) {
            result.append(String.format("%sChanges:\n", indent));
            for (RatioChange change : changes) {
                result.append(String.format("%s%s - window before: %s - window after: %s - diff: %s - before: %s - after: %s\n",
                        indent.repeat(2), change.className, change.window, change.window + 1, change.diff, change.before, change.after));
            }
        }
        if (inversions.isEmpty()) {
            return result;
        }
        result.append(String.format("%sInversions:\n", indent));
        for (Inversion inversion : inversions) {
            result.append(String.format("%s%s - %s - windows: %s - %s\n",
                    indent.repeat(2), inversion.className1, inversion.className2, inversion.window1, inversion.window2));
        }
        return result;

    }

}
