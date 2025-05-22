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


/**
 */
public class App {
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
                    System.out.println("usage: [--help] [--input-folder folder] [--delta time (microseconds)] [--compile-log file]"); 
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
        // NOTE: startTime is in milliseconds
        final long startTime = getStartTime(insFiles.startTime);
        // InstrumentationFiles insFiles = null;
        // Map<Long, String> idToCallsite = null;
        // Map<Long, String> idToClassName = null;

        // threads
        FilePartitioner.partitionFiles(Arrays.asList(insFiles.partials));
        System.out.println("After partitioningfiles.");

        File resultFolder = new File("result/");
        File[] callsiteFiles = resultFolder.listFiles((el) -> el.getName().startsWith("callsite_"));
        assert callsiteFiles != null;
        // callsiteFiles = tryPartitioningLargeFiles(Arrays.asList(callsiteFiles));
        // System.out.println("After partitioning large files.");
        // int i = 0;
        // callsiteFiles = Arrays.stream(callsiteFiles).filter(f -> f.length() > 800*1024*1024).toArray(File[]::new);
        // callsiteFiles = Arrays.stream(callsiteFiles).filter(f -> f.getName().equals("callsite_114.txt")).toArray(File[]::new);
        XmlParser parser = new XmlParser(arguments.compilerLog);
        runAnalysis(callsiteFiles, resultFolder, idToCallsite, idToClassName, arguments, parser, startTime);
    }


    private static void runAnalysis(File[] callsiteFiles, File resultFolder, Map<Long, String> idToCallsite, Map<Long, String> idToClassName, Args arguments, XmlParser parser, Long startTime) {
        // Milliseconds
        final Long vmStartTime = parser.getVmStartTime();
        final Long startTimeDiff = startTime - vmStartTime;
        int numberOfPartitions = 4;
        List<List<File>> partitions = FilePartitioner.partitionFileList(List.of(callsiteFiles), numberOfPartitions);
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
                    // NOTE: double check this 'if'
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
        File resFile = new File(resultFolder, String.format("result_%03d.txt", Integer.parseInt(callsiteFileNumber)));
        if (resFile.exists()) {
            resFile.delete();
        }
        // i++;
        Map<String, Map<Long, LongList>> callsiteInfo = reconstructCallsiteInfo(info, idToCallsite, idToClassName);
        for (var entry : callsiteInfo.entrySet()) {
            String callsite = entry.getKey();
            if (callsite == null) {
                System.err.println("Couldn't reconstruct callsite for file " + cf.getAbsolutePath());
                System.exit(1);
            }
            // System.out.println("Callsite: " + callsite);
            var percentageWindows = analyseCallsite(entry.getValue(), arguments.delta);
            String methodDescriptor = extractMethodDescriptor(callsite);
            // NOTE: compilations are given in milliseconds from the start time while
            // the instrumentation keeps the times in microseconds.
            List<Compilation> compilations = parser.findCompilationStamps(methodDescriptor);
            // NOTE: After this point compilations and decompilations will be in microseconds.
            compilations = compilations.stream().map(e -> e.withTime((e.time() - startTimeDiff) * 1000))
                    .sorted(Comparator.comparing(Compilation::time)).toList();
            // compilations = compilations.stream().map(e -> (e-startTimeDiff)*1000).sorted().toList();
            List<Decompilation> decompilations = parser.findDecompilationStamps(methodDescriptor);
            decompilations = decompilations.stream().map(e -> e.withTime((e.time() - startTimeDiff) * 1000))
                    .sorted(Comparator.comparing(Decompilation::time)).toList();
            // List<Map<String, Double>> percentageWindows = null;
            var changes = findChanges(percentageWindows, arguments.delta, startTime, compilations, decompilations, idToClassName);
            var inversions = findInversions(percentageWindows, arguments.delta, startTime, compilations, decompilations, idToClassName);
            var windowsInformation = getRawWindowInformation(percentageWindows, arguments.delta, startTime, compilations, decompilations, idToClassName);
            if (changes.isEmpty() && inversions.isEmpty()) {
                continue;
            }
            StringBuilder res = formatAnalysisResult(callsite, changes, inversions, windowsInformation, percentageWindows.start, arguments.delta);
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

    static String extractMethodDescriptor(String callsite){
        String methodDescriptor = callsite.split(" ")[1];
        methodDescriptor = methodDescriptor.replace("(", " (");
        String[] split = methodDescriptor.split(" ");
        String left = split[0];
        String right = split[1];
        left = left.replace(".", " ");
        left = left.replace("/", ".");
        return left + " " + right;
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


    public static Map<String, Map<Long, LongList>> reconstructCallsiteInfo(LongList info, Map<Long, String> idToCallsite, Map<Long, String> idToClassName) {
        /**
        Return a map mapping from a callsite to a map which maps from class names to a list of invokation times for object of that class.
        Ex: '0 java/lang/SomeStuff.someMethod()':
                'ClassA': [0, 1, 2, 3]
                'ClassB': [0, 1, 2, 3]
                ...
        NOTE: it might be better to keep the classids instead of getting the actual classnames to save ram.
        **/
        Map<String, Map<Long, LongList>> callsiteToInfo = new HashMap<>();
        for (int i = 0; i < info.size(); i += 3) {
            long csid = info.get(i);
            long cnid = info.get(i + 1);
            long timediff = info.get(i + 2);
            String callsite = idToCallsite.get(csid);
            String className = idToClassName.get(cnid);
            if(className == null){
                System.out.println(String.format("WARNING: ---------------- cnid is: %d", cnid));
                continue;
            }
            callsiteToInfo.computeIfAbsent(callsite, k -> new HashMap<>()).computeIfAbsent(cnid, k -> new LongList()).add(timediff);
        }
        return callsiteToInfo;

    }

    record PartitionedWindows(List<Map<Long, Double>> windows, long start, long end){}

    // record PartitionedWindow(Map<String, Double> value, int windowIndex){}

    private static PartitionedWindows analyseCallsite(Map<Long, LongList> info, final long timeFrame) {
        // returns a list of windows which shows the percentages of the calls on each receiver type in that window
        // WindowStart and end are in microseconds.
        long windowStart = info.values().stream().flatMapToLong(LongList::stream).min().orElse(0);
        long end = info.values().stream().flatMapToLong(LongList::stream).max().orElse(Long.MAX_VALUE);
        long nIteration = ((end-windowStart)/timeFrame) + 1;
        // List of receiver type to list of time deltas of method invocation.
        List<Map<Long, List<Long>>> windows = new ArrayList<>();
        for(int j = 0; j<nIteration; j++){
            windows.add(new HashMap<>());            
        }
        for (Map.Entry<Long, LongList> el : info.entrySet()) {
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

    private static List<PrintInformation> getRawWindowInformation(PartitionedWindows windows, final long window, final long startTime,
         List<Compilation> compilationTasks, List<Decompilation> decompilations, Map<Long, String> idToName) {
        // Compilation and decompilations are given as offset in microseconds from the startTime
        
        List<Map<Long, Double>> pw = windows.windows;
        List<PrintInformation> windowInformation = new ArrayList<>();
        int compilationIter = 0;
        int decompilationIter = 0;
        final int indexOffset = (int) (windows.start/window);
        // the window start offset is in microseconds, the start time is in millisecond
        final long firstWindowStartTime = startTime + (windows.start/1000);
        for (int i = 0; i < pw.size(); i++) {
            var w = pw.get(i);

            boolean windowIsEmpty = w.values().stream().mapToDouble(e->e).sum() == 0.0;
            if (compilationIter < compilationTasks.size() && 
                    firstWindowStartTime + i * window / 1000 > startTime + compilationTasks.get(compilationIter).time() / 1000) {
                var ct = compilationTasks.get(compilationIter++);
                windowInformation.add(ct);
            }
            if (decompilationIter < decompilations.size()
                    && firstWindowStartTime + i * window / 1000 > startTime + decompilations.get(decompilationIter).time() / 1000) {
                var dt = decompilations.get(decompilationIter++);
                windowInformation.add(dt);
            }
            if(windowIsEmpty){
                continue;
            }
            windowInformation.add(new WindowInformation(indexOffset+i, w, idToName));
        }
        return windowInformation;
    }
    
    private static List<PrintInformation> findChanges(PartitionedWindows windows, final long window, final long startTime,
         List<Compilation> compilationTasks, List<Decompilation> decompilations, Map<Long, String> idToName) {
        // Compilation and decompilations are given as offset in microseconds from the startTime
        
        List<Map<Long, Double>> pw = windows.windows;
        final double threshold = 0.1;
        List<PrintInformation> changes = new ArrayList<>();
        int compilationIter = 0;
        int decompilationIter = 0;
        final int indexOffset = (int) (windows.start/window);
        // the window start offset is in microseconds, the start time is in millisecond
        final long firstWindowStartTime = startTime + (windows.start/1000);
        // [A] [] [A] [] [A] [] [B]
        // [][][A] [] [A] [] [A] [] [B]
        // [A] [] [] [] [A] [] [B]
        Optional<Map<Long, Double>> lastValidWindow = Optional.empty();
        for (int i = 0; i < pw.size() - 1; i++) {
            var w1 = pw.get(i);
            var w2 = pw.get(i + 1);

            boolean w1Empty = w1.values().stream().mapToDouble(e->e).sum() == 0.0;
            boolean w2Empty = w2.values().stream().mapToDouble(e->e).sum() == 0.0;
            if (compilationIter < compilationTasks.size() && 
                    firstWindowStartTime + i * window / 1000 > startTime + compilationTasks.get(compilationIter).time() / 1000) {
                var ct = compilationTasks.get(compilationIter++);
                changes.add(ct);
            }
            if (decompilationIter < decompilations.size()
                    && firstWindowStartTime + i * window / 1000 > startTime + decompilations.get(decompilationIter).time() / 1000) {
                var dt = decompilations.get(decompilationIter++);
                changes.add(dt);
            }
            // for (String k : w1.keySet()) {
            if(w1Empty && w2Empty){
                continue;
            }
            if(w1Empty){
                if(!lastValidWindow.isEmpty()){
                    Optional<Double> d = lastValidWindow.get().entrySet().stream()
                            .map((el) -> Math.abs(el.getValue() - w2.get(el.getKey())))
                            .filter(el -> el > threshold).findAny();
                    if(d.isPresent()){
                        changes.add(new RatioChange(indexOffset+i, lastValidWindow.get(), w2, idToName));
                    }
                }
                lastValidWindow = Optional.of(w2);
                continue;
            }
            if(w2Empty){
                lastValidWindow = Optional.of(w1);
                continue;
            }
            Optional<Double> d = w1.entrySet().stream()
                    .map((el) -> Math.abs(el.getValue() - w2.get(el.getKey())))
                    .filter(el -> el > threshold).findAny();
            if(d.isPresent()){
                changes.add(new RatioChange(indexOffset+i, w1, w2, idToName));
            }
            lastValidWindow = Optional.of(w1);
            // }
        }
        return changes;
    }

    private static List<PrintInformation> findInversions(PartitionedWindows windows, final long window, final long startTime,
         List<Compilation> compilationTasks, List<Decompilation> decompilations, Map<Long, String> idToName) {
        List<Map<Long, Double>> pw = windows.windows;
        List<PrintInformation> inversions = new ArrayList<>();
        final int offset = (int) (windows.start/window);
        int compIter = 0;
        int decIter = 0;
        // the window offset start is in microsecond, the startTime is in milliseconds
        final long firstWindowStartTime = startTime + (windows.start/1000);
        Optional<Map<Long, Double>> lastValidWindow = Optional.empty();
        for (int i = 0; i < pw.size() - 1; i++) {
            var w1 = pw.get(i);
            var w2 = pw.get(i + 1);
            // [A] [] [A] [] [A] [] [B]
            // [][][A] [] [A] [] [A] [] [B]
            // [A] [] [] [] [A] [] [B]
            boolean w1Empty = w1.values().stream().mapToDouble(e->e).sum() == 0.0;
            boolean w2Empty = w2.values().stream().mapToDouble(e->e).sum() == 0.0;
            Long[] keys = w1.keySet().toArray(Long[]::new);
            Long[] keysSorted1 = Arrays.stream(keys).filter(k->w1.get(k)!=0).sorted((k1, k2) -> {
                int s = Double.compare(w1.get(k2), w1.get(k1));
                return s;
            }).toArray(Long[]::new);

            Long[] keysSorted2 = Arrays.stream(keys).filter(k-> w2.get(k)!=0).sorted((k1, k2) -> {
                int s = Double.compare(w2.get(k2), w2.get(k1));
                return s;
            }).toArray(Long[]::new);

            if (compIter < compilationTasks.size() &&
                    firstWindowStartTime + i * window / 1000 > startTime + compilationTasks.get(compIter).time()/1000) {
                Compilation ct = compilationTasks.get(compIter++);
                inversions.add(ct);
            }
            if (decIter < decompilations.size() &&
                    firstWindowStartTime + i * window / 1000 > startTime + decompilations.get(decIter).time()/1000) {
                var dt = decompilations.get(decIter++);
                inversions.add(dt);
            }
            if(w1Empty && w2Empty){
                continue;
            }

            if(w1Empty){
                if(!lastValidWindow.isEmpty()){
                    Map<Long, Double> lv = lastValidWindow.get();
                    Long[] keysSortedLV = Arrays.stream(keys).filter(k-> lv.get(k)!=0).sorted((k1, k2) -> {
                        int s = Double.compare(lv.get(k2), lv.get(k1));
                        return s;
                    }).toArray(Long[]::new);
                    if(!compareRanking(keysSortedLV, keysSorted2)){
                        inversions.add(new Inversion(offset+i, offset+i + 1, lv, w2, idToName));
                    }
                }
                lastValidWindow = Optional.of(w2);
                continue;
            }
            if(w2Empty){
                lastValidWindow = Optional.of(w1);
                continue;
            }
            if(!compareRanking(keysSorted1, keysSorted2)){
                inversions.add(new Inversion(offset+i, offset+i + 1, w1, w2, idToName));
            }
            lastValidWindow = Optional.of(w1);
            
        }
        return inversions;
    }

    static <T> boolean compareRanking(T[] arr1, T[] arr2) {
        T[] a1 = arr1.length < arr2.length ? arr1 : arr2;
        T[] a2 = arr1.length < arr2.length ? arr2 : arr1;
        for(int i = 0; i < a1.length; i++){
            if(!a1[i].equals(a2[i])){
                return false;
            }
        }
        return true;
    }

    private static StringBuilder formatAnalysisResult(String cs, List<PrintInformation> changes, List<PrintInformation> inversions, List<PrintInformation> windowsInformation, long start, long delta) {
        StringBuilder result = new StringBuilder(String.format("Callsite: %s\n", cs));
        String indent = "    ";
        if (!changes.isEmpty()) {
            result.append(String.format("%sChanges[start time = %s, window size = %s]:\n", indent, start, delta));
            for (PrintInformation change : changes) {
                for(String s: change.formatInformation()){
                    result.append(String.format("%s%s", indent.repeat(2), s));
                }
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

        result.append(String.format("%sRawWindowInformation[start time = %s, window size = %s]:\n", indent, start, delta));
        for (PrintInformation wi: windowsInformation) {
            for(String s: wi.formatInformation()){
               result.append(String.format("%s%s", indent.repeat(2), s));
            }
        }
        return result;

    }

}
