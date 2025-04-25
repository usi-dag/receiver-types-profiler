package com.msde.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    record Args(File inputFolder, long delta, File compilerLog){}

    private static Args parseArgs(String[] args){
        Args parsedArgs = new Args(new File("/home/ubuntu/receiver-types-profiler/output"), 1000L, new File("/home/ubuntu/receiver-types-profiler/compiler_log.xml"));
        int i=0;
        while(i<args.length){
            String current = args[i++];
            switch(current){
                case "--input-folder", "-i" -> {
                    File inputFolder = new File(args[i++]);
                    parsedArgs = new Args(inputFolder, parsedArgs.delta, parsedArgs.compilerLog);
                }
                case "--delta", "-d" -> {
                    long delta = Long.parseLong(args[i++]);
                    parsedArgs = new Args(parsedArgs.inputFolder, delta, parsedArgs.compilerLog);
                }
                case "--compile-log", "-c" -> {
                    File compilerLog = new File(args[i++]);
                    parsedArgs = new Args(parsedArgs.inputFolder, parsedArgs.delta, compilerLog);
                    
                }
                default -> {
                    System.out.println("usage: [--help] [--input-folder folder] [--delta time] [--compile-log file]"); 
                    System.exit(1);
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
        // InstrumentationFiles insFiles = null;
        // Map<Long, String> idToCallsite = null;
        // Map<Long, String> idToClassName = null;

        // threads
        partitionFiles(Arrays.asList(insFiles.partials));
        System.out.println("After partitioningfiles.");

        File resultFolder = new File("result/");
        File[] callsiteFiles = resultFolder.listFiles((el) -> el.getName().startsWith("callsite_"));
        assert callsiteFiles != null;
        // callsiteFiles = tryPartitioningLargeFiles(Arrays.asList(callsiteFiles));
        System.out.println("After partitioning large files.");
        int i = 0;
        // callsiteFiles = Arrays.stream(callsiteFiles).filter(f -> f.length() > 800*1024*1024).toArray(File[]::new);
        // callsiteFiles = Arrays.stream(callsiteFiles).filter(f -> f.getName().equals("callsite_794.txt")).toArray(File[]::new);
        XmlParser parser = new XmlParser(arguments.compilerLog);
        runAnalysis(callsiteFiles, resultFolder, idToCallsite, idToClassName, arguments, parser, startTime);
    }


    private static void runAnalysis(File[] callsiteFiles, File resultFolder, Map<Long, String> idToCallsite, Map<Long, String> idToClassName, Args arguments, XmlParser parser, Long startTime) {
        Long vmStartTime = parser.getVmStartTime();
        Long startTimeDiff = startTime - vmStartTime;
        int numberOfPartitions = 4;
        List<List<File>> partitions = partitionFileList(List.of(callsiteFiles), numberOfPartitions);
        List<Thread> threads = new ArrayList<>();
        List<Integer> threadFinished = new ArrayList<>();
        for(int i=0; i<numberOfPartitions; i++){
            threadFinished.add(0);
        }
        int id = 0;
        for (List<File> cFiles : partitions) {
            int threadId = id++;
            Thread t = new Thread(() -> {
                int current = 0;
                for (File cf : cFiles) {
                    // System.out.printf("\33[2K\rworking on file %s %s/%s size %s M" , cf, i+1, callsiteFiles.length, cf.length()/(1024*1024));
                    if (threadAnalysis(resultFolder, idToCallsite, idToClassName, arguments, parser, startTime, cf, startTimeDiff))
                        continue;
                    current++;
                    threadFinished.set(threadId, current);
                }

            });
            threads.add(t);
        }
        Thread logThread = new Thread(()->{
            while(threadFinished.stream().mapToInt(el->el).sum() != callsiteFiles.length){
                try{
                    Thread.sleep(400);
                } catch(InterruptedException e){
                    System.err.println(e.getMessage());
                }
                String progress = IntStream.range(0, threadFinished.size()).mapToObj(el -> String.format("%s - %s/%s", el, threadFinished.get(el), partitions.get(el).size())).collect(Collectors.joining(", "));
                System.out.print("\33[2K\rAnalysis progess: "+progress);
            }
            String progress = IntStream.range(0, threadFinished.size()).mapToObj(el -> String.format("%s - %s/%s", el, threadFinished.get(el), partitions.get(el).size())).collect(Collectors.joining(", "));
            System.out.print("\33[2K\rAnalysis progess: "+progress);
            System.out.println();
        });
        threads.add(logThread);
        for (Thread t : threads) {
                t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.err.println(e.getMessage());
            }
        }
    }

    private static boolean threadAnalysis(File resultFolder, Map<Long, String> idToCallsite, Map<Long, String> idToClassName, Args arguments, XmlParser parser, Long startTime, File cf, Long startTimeDiff) {
        Optional<LongList> maybeInfo = readBinary(cf);
        // System.out.println("finished reading binary");
        if (maybeInfo.isEmpty()) {
            System.err.println("Couldn't read binary file: " + cf.getAbsolutePath());
            return true;
        }
        LongList info = maybeInfo.get();
        String callsiteFileNumber = cf.getName().replace("callsite_", "").replace(".txt", "");
        File resFile = new File(resultFolder, String.format("result_%s.txt", callsiteFileNumber));
        if (resFile.exists()) {
            resFile.delete();
        }
        // i++;
        var callsiteInfo = reconstructCallsiteInfo(info, idToCallsite, idToClassName);
        for (var entry : callsiteInfo.entrySet()) {
            String callsite = entry.getKey();
            if (callsite == null) {
                System.err.println("Couldn't reconstruct callsite for file " + cf.getAbsolutePath());
                System.exit(1);
            }
            // System.out.println("Callsite: " + callsite);
            var percentageWindows = analyseCallsite(entry.getValue(), arguments.delta);
            String methodDescriptor = extractMethodDescriptor(callsite);
            // compilations are given in milliseconds from the start time while
            // the instrumentation is kept in microseconds.
            List<Compilation> compilations = parser.findCompilationStamps(methodDescriptor);
            compilations = compilations.stream().map(e -> e.withTime((e.time() - startTimeDiff) * 1000))
                    .sorted(Comparator.comparing(Compilation::time)).toList();
            // compilations = compilations.stream().map(e -> (e-startTimeDiff)*1000).sorted().toList();
            List<Decompilation> decompilations = parser.findDecompilationStamps(methodDescriptor);
            decompilations = decompilations.stream().map(e -> e.withTime((e.time() - startTimeDiff) * 1000))
                    .sorted(Comparator.comparing(Decompilation::time)).toList();
            // List<Map<String, Double>> percentageWindows = null;
            var changes = findChanges(percentageWindows, arguments.delta, startTime, compilations, decompilations);
            var inversions = findInversions(percentageWindows, arguments.delta, startTime, compilations, decompilations);
            if (changes.isEmpty() && inversions.isEmpty()) {
                continue;
            }
            StringBuilder res = formatAnalysisResult(callsite, changes, inversions, percentageWindows.start, arguments.delta);
            // System.out.println(res);
            try {
                resFile.createNewFile();
                Files.writeString(resFile.toPath(), res.toString(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }
        return false;
    }

    private static String extractMethodDescriptor(String callsite){
        String methodDescriptor = callsite.split(" ")[1];
        methodDescriptor = methodDescriptor.replace("(", " (");
        String[] split = methodDescriptor.split(" ");
        String left = split[0];
        String right = split[1];
        left = left.replace(".", " ");
        left = left.replace("/", ".");
        return left + " " + right;
    }

    private static List<List<File>> partitionFileList(List<File> files, int numberOfPartitions){
        int partitionSize = files.size()/numberOfPartitions;
        List<List<File>> partitions = new ArrayList<>();
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

    private static void partitionFiles(List<File> partials) {
        int numberOfPartitions = 4;
        int partitionSize = partials.size()/ numberOfPartitions;
        List<List<File>> partitions = new ArrayList<>();
        for (int i = 0; i < numberOfPartitions; i++) {
            int beg = i * partitionSize;
            int end = Math.min(beg + partitionSize, partials.size());
            if (i == numberOfPartitions - 1) {
                end = partials.size();
            }
            partitions.add(partials.subList(beg, end));
        }
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
        Thread logThread = new Thread(()->{
            while(threadFinished.stream().mapToInt(el->el).sum()!= partials.size()){
                try{
                    Thread.sleep(500);
                } catch(InterruptedException e){
                    System.err.println(e.getMessage());
                }
                String progress = IntStream.range(0, threadFinished.size()).mapToObj(el -> String.format("%s - %s/%s", el, threadFinished.get(el), partitions.get(el).size())).collect(Collectors.joining(", "));
                System.out.print("\33[2K\rPartition progess: "+progress);
            }
            String progress = IntStream.range(0, threadFinished.size()).mapToObj(el -> String.format("%s - %s/%s", el, threadFinished.get(el), partitions.get(el).size())).collect(Collectors.joining(", "));
            System.out.print("\33[2K\rPartition progess: "+progress);
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

        try{
            logThread.join();
        } catch(InterruptedException e){
            System.err.println(e.getMessage());
        }
        
    }

    
    private static void writeIntermediateFiles(File binaryFile) {
        try {
            File intermediateFolder = new File("result/");
            intermediateFolder.mkdir();
            
            Map<Long, List<Byte>> fileIdToBytes = new HashMap<>();
            int readBytes = 0;
            try(BufferedInputStream in = new BufferedInputStream(new FileInputStream(binaryFile.toString()))){
                byte[] bytes = new byte[128*1024*1024];
                int len;
                outer: while((len = in.read(bytes)) != -1){
                    for (int i = 0; i < len; i += 16) {
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
                            break outer;
                        }
                        long m = callsiteId % 1000;
                        List<Byte> toAdd = new ArrayList<>();
                        for(byte b: Arrays.copyOfRange(bytes, i, i+16)){
                            toAdd.add(b);
                        }
                        fileIdToBytes.computeIfAbsent(m, k-> new ArrayList<>()).addAll(toAdd);
                        readBytes += 16;
                        if(readBytes >= 128*1024*1024){
                            for(Map.Entry<Long, List<Byte>> entry: fileIdToBytes.entrySet()){
                                locks.get(entry.getKey()).lock();
                                File csFile = new File(intermediateFolder, String.format("callsite_%s.txt", entry.getKey()));
                                if(!csFile.exists()){
                                    csFile.createNewFile();
                                }
                                byte[] toWrite = new byte[entry.getValue().size()];
                                int j = 0;
                                for(Byte b: entry.getValue()){
                                    toWrite[j++] = b;
                                }
                                Files.write(csFile.toPath(), toWrite, StandardOpenOption.APPEND);
                                locks.get(entry.getKey()).unlock();
                            }
                            fileIdToBytes.clear();
                        }
                    }
                }
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
        File[] classNames = inputFolder.listFiles((el) -> el.getName().startsWith("className"));
        assert classNames != null;
        File className = classNames[0];
        File[] callsites = inputFolder.listFiles((el) -> el.getName().startsWith("callsite"));
        assert callsites != null;
        File callsite = callsites[0];
        File[] startTimes = inputFolder.listFiles((el) -> el.getName().startsWith("start_time"));
        assert startTimes != null;
        File startTime = startTimes[0];
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
            String startTime = Files.readAllLines(startTimeFile.toPath()).get(1);
            return Long.parseLong(startTime);

        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return 0L;
    }

    private static Optional<LongList> readBinary(File binaryFile) {
        try {
            // byte[] bytes = Files.readAllBytes(binaryFile.toPath());
            ByteBuffer.allocate(Long.BYTES).getLong();
            LongList l = new LongList();
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(binaryFile.toPath().toString()))) {
                byte[] bytes = new byte[128*1024*1024];
                int len;
                outer: while ((len = in.read(bytes)) != -1) {
                    for(int i=0; i<len; i+= 16){
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
                            break outer;
                        }
                        l.add(callsiteId);
                        l.add(classNameId);
                        l.add(timeDiff);
                    }
                }
            }
            return Optional.of(l);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
        return Optional.empty();
    }


    public static Map<String, Map<String, LongList>> reconstructCallsiteInfo(LongList info, Map<Long, String> idToCallsite, Map<Long, String> idToClassName) {
        Map<String, Map<String, LongList>> callsiteToInfo = new HashMap<>();
        for (int i = 0; i < info.size(); i += 3) {
            long csid = info.get(i);
            long cnid = info.get(i + 1);
            long timediff = info.get(i + 2);
            String callsite = idToCallsite.get(csid);
            String className = idToClassName.get(cnid);
            callsiteToInfo.computeIfAbsent(callsite, k -> new HashMap<>()).computeIfAbsent(className, k -> new LongList()).add(timediff);
        }
        return callsiteToInfo;

    }

    record PartitionedWindows(List<Map<String, Double>> windows, long start, long end){}

    private static PartitionedWindows analyseCallsite(Map<String, LongList> info, long timeFrame) {
        // returns a list of windows which shows the percentages of the calls on each receiver type in that window
        long windowStart = info.values().stream().flatMapToLong(LongList::stream).min().orElse(0);
        long end = info.values().stream().flatMapToLong(LongList::stream).max().orElse(Long.MAX_VALUE);
        long nIteration = ((end-windowStart)/timeFrame) + 1;
        List<Map<String, List<Long>>> windows = new ArrayList<>();
        for(int j = 0; j<nIteration; j++){
            windows.add(new HashMap<>());            
        }
        for (Map.Entry<String, LongList> el : info.entrySet()) {
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
        var partitionedWindows = windows.stream().map(m -> {
            double tot = m.values().stream().mapToDouble(List::size).sum();
            return m.entrySet().stream().map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), tot == 0? 0: e.getValue().size() / tot))
                    .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        }).toList();
        return new PartitionedWindows(partitionedWindows, windowStart, end);
    }
    
    private static List<PrintInformation> findChanges(PartitionedWindows windows, long window, long startTime,
         List<Compilation> compilationTasks, List<Decompilation> decompilations) {
        
        List<Map<String, Double>> pw = windows.windows;
        double threshold = 0.1;
        List<PrintInformation> changes = new ArrayList<>();
        int compilationIter = 0;
        int decompilationIter = 0;
        int offset = (int) (windows.start/window);
        for (int i = 0; i < pw.size() - 1; i++) {
            var w1 = pw.get(i);
            var w2 = pw.get(i + 1);
            for (String k : w1.keySet()) {
                var v1 = w1.get(k);
                var v2 = w2.get(k);
                double diff = Math.abs(v1 - v2);
                if(compilationIter < compilationTasks.size() && startTime+i*window > startTime+compilationTasks.get(compilationIter).time()){
                    var ct = compilationTasks.get(compilationIter++);
                    changes.add(ct);
                }
                if(decompilationIter< decompilations.size() && startTime+i*window > startTime+decompilations.get(decompilationIter).time()){
                    var dt = decompilations.get(decompilationIter++);
                    changes.add(dt);
                }
                if (diff > threshold) {
                    changes.add(new RatioChange(offset+i, k, v1, v2, diff));
                }
            }
        }
        return changes;
    }

    private static List<PrintInformation> findInversions(PartitionedWindows windows, long window, long startTime,
         List<Compilation> compilationTasks, List<Decompilation> decompilations) {
        List<Map<String, Double>> pw = windows.windows;
        List<PrintInformation> inversions = new ArrayList<>();
        int offset = (int) (windows.start/window);
        int compIter = 0;
        int decIter = 0;
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
                if(compIter< compilationTasks.size() && startTime+i*window > startTime+compilationTasks.get(compIter).time()){
                    Compilation ct = compilationTasks.get(compIter++);
                    inversions.add(ct);
                }
                if(decIter< decompilations.size() && startTime+i*window > startTime+decompilations.get(decIter).time()){
                    var dt = decompilations.get(decIter++);
                    inversions.add(dt);
                }
                if (valKey1Window1.compareTo(valKey2Window1) != valKey1Window2.compareTo(valKey2Window2)) {
                    inversions.add(new Inversion(offset+i, offset+i + 1, k1, k2, valKey1Window1, valKey2Window1,
                            valKey1Window2, valKey2Window2));
                }
            }
        }
        return inversions;
    }

    private static StringBuilder formatAnalysisResult(String cs, List<PrintInformation> changes, List<PrintInformation> inversions, long start, long delta) {
        StringBuilder result = new StringBuilder(String.format("Callsite: %s\n", cs));
        String indent = "    ";
        if (!changes.isEmpty()) {
            result.append(String.format("%sChanges[start time = %s, window size = %s]:\n", indent, start, delta));
            for (PrintInformation change : changes) {
                result.append(String.format("%s%s", indent.repeat(2), change.formatInformation()[0]));
            }
        }
        if (inversions.isEmpty()) {
            return result;
        }
        result.append(String.format("%sInversions[start time = %s, window size = %s]:\n", indent, start, delta));
        for (PrintInformation inversion : inversions) {
            for(String s: inversion.formatInformation()){
               result.append(String.format("%s%s", indent.repeat(2), s));
            }
        }
        return result;

    }

}
