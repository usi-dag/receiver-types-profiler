package com.msde.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

class XmlParser {
  private File compilerLog;
  private XPath xpath;
  private Document doc;

  public XmlParser(File compilerLog) {
    this.compilerLog = compilerLog;
    try {
      DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      this.doc = db.parse(this.compilerLog);
      doc.normalize();
    } catch (ParserConfigurationException e) {
      System.err.println(e.getMessage());
    } catch (org.xml.sax.SAXException e) {
      System.err.println(e.getMessage());
    } catch (IOException e) {
      System.err.println(e.getMessage());
    }
    this.xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
  }

  public Long getVmStartTime(){
    try{
      String expression = String.format("//hotspot_log");
      NodeList res = (NodeList) this.xpath.compile(expression).evaluate(this.doc, XPathConstants.NODESET);
      String value = res.item(0).getAttributes().getNamedItem("time_ms").getNodeValue();
      return Long.decode(value);
    } catch(Exception e){
      System.err.println(e.getMessage());
    }
    return 0L;
  }

  public List<Long> findCompilationStamps(String methodDescriptor) {
    try {
      // String expression = "//task[@method='Main main ([Ljava/lang/String;)V']";
      String expression = String.format("//task[@method='%s']", methodDescriptor);
      NodeList res = (NodeList) this.xpath.compile(expression).evaluate(this.doc, XPathConstants.NODESET);
      List<Long> toReturn = new ArrayList<>();
      for (int i = 0; i < res.getLength(); i++) {
        String value = res.item(i).getAttributes().getNamedItem("stamp").getNodeValue();
        value = value.replace(".", "");
        toReturn.add(Long.decode(value));

      }
      return toReturn;
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    return new ArrayList<>();
  }

  public List<Long> findDecompilationStamps(String methodDescriptor) {
    try {
      // String expression = "//task[@method='Main main ([Ljava/lang/String;)V']";
      String expression = String.format("//task[@method='%s']", methodDescriptor);
      NodeList res = (NodeList) this.xpath.compile(expression).evaluate(this.doc, XPathConstants.NODESET);
      List<Long> toReturn = new ArrayList<>();
      List<String> compileIds = new ArrayList<>();
      for (int i = 0; i < res.getLength(); i++) {
        String value = res.item(i).getAttributes().getNamedItem("compile_id").getNodeValue();
        compileIds.add(value);
      }
      for(String compileId: compileIds){
        String nonEntrantExpression = String.format("//make_not_entrant[@compile_id='%s']", compileId);
        NodeList nonEntrantNodes = (NodeList) this.xpath.compile(nonEntrantExpression).evaluate(this.doc, XPathConstants.NODESET);
        for(int i=0; i<nonEntrantNodes.getLength(); i++){
          String value = nonEntrantNodes.item(i).getAttributes().getNamedItem("stamp").getNodeValue();
          value = value.replace(".", "");
          toReturn.add(Long.decode(value));
          
        }
      }
      return toReturn;
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    return new ArrayList<>();
  }

}
