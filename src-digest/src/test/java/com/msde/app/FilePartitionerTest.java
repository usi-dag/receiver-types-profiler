
package com.msde.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class FilePartitionerTest{
  @Test
  public void testPartitionFileList(){
    List<File> files = new ArrayList<>();
    for(int i=0; i<10; i++){
      files.add(new File(String.format("%d", i)));
    }
    List<List<File>> p = FilePartitioner.partitionFileList(files, 4);
    assertEquals(4, p.size());
    assertEquals(2, p.get(0).size());
    int j = 0;
    for(int i = 0; i<p.get(0).size(); i++){
      assertEquals(String.format("%d", j), p.get(0).get(i).getName());
      j++;
    }
    assertEquals(2, p.get(1).size());
    for(int i = 0; i<p.get(1).size(); i++){
      assertEquals(String.format("%d", j), p.get(1).get(i).getName());
      j++;
    }
    assertEquals(2, p.get(2).size());
    for(int i = 0; i<p.get(2).size(); i++){
      assertEquals(String.format("%d", j), p.get(2).get(i).getName());
      j++;
    }
    assertEquals(4, p.get(3).size());
    for(int i = 0; i<p.get(3).size(); i++){
      assertEquals(String.format("%d", j), p.get(3).get(i).getName());
      j++;
    }
  }

  @Test
  public void testWriteIntermediateFiles(){
    File cf = new File("/home/ubuntu/receiver-types-profiler/src-digest/partial_30.txt");
    File pf = new File("result/p.txt");
    try{
      Files.copy(cf.toPath(), pf.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }catch(IOException e){
      
    }
    List<File> files = new ArrayList<>();
    files.add(pf);
    FilePartitioner.partitionFiles(files);
    File res = new File("/home/ubuntu/receiver-types-profiler/src-digest/result");
    assertTrue(res.isDirectory());
    assertEquals(61, res.listFiles().length);
  }
}
