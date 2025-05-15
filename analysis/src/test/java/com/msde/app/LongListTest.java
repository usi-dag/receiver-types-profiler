package com.msde.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class LongListTest {

  @Test
  public void testAddLessElmentsThanTheLength() {
    LongList list = new LongList(3);
    list.add(1);
    list.add(2);
    assertEquals(1, list.get(0));
    assertEquals(2, list.get(1));
  }

  @Test
  public void testFillInternalArrayOnce() {
    LongList list = new LongList(3);
    for (int i = 0; i < 6; i++) {
      list.add(i);
    }
    for (int i = 0; i < 6; i++) {
      assertEquals(i, list.get(i));
    }
  }

  @Test
  public void testFillInternalArrayMultipleTimes() {
    LongList list = new LongList(3);
    for (int i = 0; i < 11; i++) {
      list.add(i);
    }
    for (int i = 0; i < 11; i++) {
      assertEquals(i, list.get(i));
    }
  }

  @Test
  public void testSize() {
    LongList list = new LongList(3);
    for (int i = 0; i < 6; i++) {
      list.add(i);
    }
    assertEquals(6, list.size());

    LongList list2 = new LongList(3);
    for (int i = 0; i < 2; i++) {
      list2.add(i);
    }
    assertEquals(2, list2.size());

    LongList list3 = new LongList(3);
    for (int i = 0; i < 11; i++) {
      list3.add(i);
    }
    assertEquals(11, list3.size());
  }

  @Test
  public void testMin() {
    LongList list = new LongList(3);
    for (int i = 0; i < 26; i++) {
      list.add(i);
    }
    assertEquals(0, list.min());

    LongList list2 = new LongList(3);
    for (int i = 0; i < 26; i++) {
      list2.add(26 - i);
    }
    assertEquals(1, list2.min());

  }

  @Test
  public void testMax() {
    LongList list = new LongList(3);
    for (int i = 0; i < 26; i++) {
      list.add(i);
    }
    assertEquals(25, list.max());

    LongList list2 = new LongList(3);
    for (int i = 0; i < 26; i++) {
      list2.add(26 - i);
    }
    assertEquals(26, list2.max());
  }

  @Test
  public void testStream(){
    LongList list = new LongList(3);
    for (int i = 0; i < 11; i++) {
      list.add(i);
    }
    long[] l = list.stream().toArray();
    for(int i = 0; i< 11; i++){
      assertEquals(l[i], list.get(i));
    }
  }

  @Test
  public void testIterator(){
    LongList list = new LongList(3);
    for (int i = 0; i < 11; i++) {
      list.add(i);
    }
    int i = 0;
    for(Long e: list){
      assertEquals(e, i++);
    }
  }

}
