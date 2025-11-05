package com.msde.app;


import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@FunctionalInterface
public interface ConvergeTime<R> {
    R apply(PartitionedWindows partitionedWindows, Integer t);

    static ConvergeTime<List<Long>> topReceiver(int k){
        return (pw, t) ->{
            Map<Long, Integer> receiverToCount = pw.receiverToCounts().stream()
                    .limit(t)
                    .flatMap(m -> m.entrySet().stream())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            Integer::sum
                    ));

            return receiverToCount.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(k)
                    .map(Map.Entry::getKey)
                    .toList();
        };
    }

     static <T> int callsiteStability(PartitionedWindows pw, BiPredicate<T, T> P, ConvergeTime<T> F){
        // cstab(c, k) = min {t >= t0: forall {t_after > t: topRcv(c, k, t0, t) = topRcv(c, k, t0, t_after) } }
        int stabilityPoint = pw.windows().size()-1;
        for(int i = pw.windows().size(); i > 0; i--){
            if(P.test(F.apply(pw, i), F.apply(pw, i-1))){
                stabilityPoint = i-1;
            }else{
                break;
            }
        }
        return stabilityPoint;
    }


    static int callsiteStabilityTopReceiver(PartitionedWindows pw, BiPredicate<List<Long>, List<Long>> P){
        Map<Long, Integer> receiverToCount = pw.receiverToCounts().stream()
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        Integer::sum
                ));

        int stabilityPoint = pw.windows().size()-1;
        Map<Long, Integer> right = receiverToCount;
        for(int i = pw.windows().size(); i > 0; i--){
            Map<Long, Integer> current = pw.receiverToCounts().get(i-1);
            Map<Long, Integer> left  = right.entrySet().stream()
                    .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()-current.get(entry.getKey())))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            List<Long> rightReceivers =  right.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .toList();
            List<Long> leftReceivers =  left.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .toList();
            if(P.test(leftReceivers, rightReceivers)){
                stabilityPoint = i-1;
                right = left;
            }else{
                break;
            }
        }
        return  stabilityPoint;
    }
}
