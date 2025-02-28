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


/**
 */
public class App {
    public static void main(String[] args) {
        File inputFolder = new File("/home/ubuntu/receiver-types-profiler/output");
        long delta = 1000;
        InstrumentationFiles insFiles = getInstrumentationFiles(inputFolder);
        var idToCallsite = parseCsvMapping(insFiles.callsite);
        var idToClassName = parseCsvMapping(insFiles.className);
        long startTime = getStartTime(insFiles.startTime);
        // Map<String, Map<String, List<Long>>> total = new HashMap<>();
        List<Long> totalInfo = new ArrayList<>();
        for (File partial : insFiles.partials) {
            Optional<List<Long>> maybeInfo = readBinary(partial);
            if (maybeInfo.isEmpty()) {
                System.err.println("Couldn't read binary file: " + partial.getAbsolutePath());
                return;
            }
            List<Long> info = maybeInfo.get();
            totalInfo.addAll(info);

        }
        File resultFolder = new File("result/");
        resultFolder.mkdir();
        File resFile = new File(resultFolder, "result.txt");
        try {
            resFile.createNewFile();
        } catch (IOException e) {
            System.err.println(e.getMessage());
            return;
        }
        var callsiteInfo = reconstructCallsiteInfo(totalInfo, idToCallsite, idToClassName);
        // callsiteInfo = callsiteInfo.entrySet().stream().filter(e -> e.getKey().contains("36 Main")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        for (var entry : callsiteInfo.entrySet()) {
            String callsite = entry.getKey();
            var percentageWindows = analyseCallsite(entry.getValue(), delta);
            var changes = findChanges(percentageWindows);
            var inversions = findInversions(percentageWindows);
            if (changes.isEmpty() && inversions.isEmpty()) {
                continue;
            }
            StringBuilder res = formatAnalysisResult(callsite, changes, inversions);
            System.out.println(res);
            try {
                Files.writeString(resFile.toPath(), res.toString(), StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
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
        long windowStart = info.values().stream().flatMap(List::stream).mapToLong(e -> e).min().orElse(0);
        long end = info.values().stream().flatMap(List::stream).mapToLong(e -> e).max().orElse(Long.MAX_VALUE);
        List<Map<String, List<Long>>> windows = new ArrayList<>();
        while (true) {
            Map<String, List<Long>> current = new HashMap<>();
            for (Map.Entry<String, List<Long>> el : info.entrySet()) {
                long bs = windowStart;
                var res = el.getValue().stream().filter(e -> e >= bs && e < bs + timeFrame).toList();
                current.put(el.getKey(), res);
            }
            windows.add(current);
            windowStart += timeFrame;
            if (windowStart > end) {
                break;
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
