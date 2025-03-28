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

  
  public List<Compilation> findCompilationStamps(String methodDescriptor) {
    try {
      // String expression = "//task[@method='Main main ([Ljava/lang/String;)V']";
      String expression = String.format("//task[@method='%s']", methodDescriptor);
      NodeList res = (NodeList) this.xpath.compile(expression).evaluate(this.doc, XPathConstants.NODESET);
      List<Compilation> toReturn = new ArrayList<>();
      for (int i = 0; i < res.getLength(); i++) {
        String value = res.item(i).getAttributes().getNamedItem("stamp").getNodeValue();
        String id = res.item(i).getAttributes().getNamedItem("compile_id").getNodeValue();
        String kind = null;
        if(res.item(i).getAttributes().getNamedItem("compile_kind") != null){
          kind = res.item(i).getAttributes().getNamedItem("compile_kind").getNodeValue();
        }
        value = value.replace(".", "");
        var a = new Compilation(Long.decode(value), id, kind);
        toReturn.add(a);

      }
      return toReturn;
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    return new ArrayList<>();
  }
    
  public List<Decompilation> findDecompilationStamps(String methodDescriptor) {
    try {
      // String expression = "//task[@method='Main main ([Ljava/lang/String;)V']";
      String expression = String.format("//task[@method='%s']", methodDescriptor);
      NodeList res = (NodeList) this.xpath.compile(expression).evaluate(this.doc, XPathConstants.NODESET);
      List<Decompilation> toReturn = new ArrayList<>();
      List<String> compileIds = new ArrayList<>();
      for (int i = 0; i < res.getLength(); i++) {
        String value = res.item(i).getAttributes().getNamedItem("compile_id").getNodeValue();
        compileIds.add(value);
      }
      for(String compileId: compileIds){
        String nonEntrantExpression = String.format("//make_not_entrant[@compile_id='%s']", compileId);
        NodeList nonEntrantNodes = (NodeList) this.xpath.compile(nonEntrantExpression).evaluate(this.doc, XPathConstants.NODESET);
        String trapExpression = String.format("//uncommon_trap[@compile_id='%s']", compileId);
        NodeList trapNodes = (NodeList) this.xpath.compile(trapExpression).evaluate(this.doc, XPathConstants.NODESET);
        for(int i=0; i<nonEntrantNodes.getLength(); i++){
          var attributes = nonEntrantNodes.item(i).getAttributes();
          String reason = null;
          String action = null;
          if(i<trapNodes.getLength()){
            var trapAttr = trapNodes.item(i).getAttributes();
            reason = trapAttr.getNamedItem("reason") != null?  trapAttr.getNamedItem("reason").getNodeValue() : null;
            action = trapAttr.getNamedItem("action") != null?  trapAttr.getNamedItem("action").getNodeValue() : null;
          }
          String value = attributes.getNamedItem("stamp") != null? attributes.getNamedItem("stamp").getNodeValue(): null;
          String kind = attributes.getNamedItem("compile_kind") != null ? attributes.getNamedItem("compile_kind").getNodeValue(): null;
          value = value.replace(".", "");
          Decompilation dec = new Decompilation(Long.decode(value), compileId, kind, reason, action);
          toReturn.add(dec);          
        }
      }
      return toReturn;
    } catch (Exception e) {
      System.err.println(e.getMessage());
    }
    return new ArrayList<>();
  }

}
