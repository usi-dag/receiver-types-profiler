package com.msde.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

class LongList implements Iterable<Long>{
  private int length = 16*1024*1024;
  private long[] current;
  private int index = 0;
  private List<long[]> l;

  public LongList(){
    this.current = new long[length];
    this.l = new ArrayList<long[]>();
  }

  public LongList(int length){
    this.length = length;
    this.current = new long[length];
    this.l = new ArrayList<long[]>();
  }

  public void add(long v){
    if(index == length){
      this.l.add(this.current);
      this.current = new long[length];
      this.index = 0;
    }
    this.current[index++] = v;
  }

  public int size(){
    return length*l.size() + index;
  }

  public long get(int i){
    // length: 3
    // current: [1 1 1] 
    // l: [1 1 1] [1 1 1]
    // i = 3
    int a = i/length;
    long[] c = current;
    if(a<l.size()){
      c = l.get(a);
    }
    return c[i-length*a];
  }

  public LongStream stream(){
    LongStream s = IntStream.range(0, current.length).filter(e -> e < index).mapToLong(e-> current[e]);
    return LongStream.concat(l.stream().flatMapToLong(Arrays::stream), s);
  }


  public long min(){
    assert l.size() > 0 || index > 0;
    long min = Long.MAX_VALUE;
    for(long[] arr: l){
      for(int i=0; i<arr.length; i++){
        min = arr[i] < min ? arr[i]: min;
      }
    }
    for(int i=0; i<index; i++){
      min = current[i] < min ? current[i]: min;
    }
    return min;
  }

  public long max(){
    assert l.size() > 0 || index > 0;
    long max = Long.MIN_VALUE;
    for(long[] arr: l){
      for(int i=0; i<arr.length; i++){
        max = arr[i] > max ? arr[i]: max;
      }
    }
    for(int i=0; i<index; i++){
      max = current[i] > max ? current[i]: max;
    }
    return max;
  }

  @Override
  public Iterator<Long> iterator() {
    Iterator<Long> it = new Iterator<>() {
      private int currentIndex = 0;

      @Override
      public boolean hasNext() {
          return currentIndex < length*l.size()+index;
      }

          @Override
          public Long next() {
              return get(currentIndex++);
          }

          @Override
          public void remove() {
              throw new UnsupportedOperationException();
          }
      };
    return it;
  }

  
}
