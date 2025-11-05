package com.msde.app;


import java.util.List;
import java.util.Map;

public record PartitionedWindows(List<Map<Long, Double>> windows, long start, long end, List<Map<Long, Integer>> receiverToCounts){}
