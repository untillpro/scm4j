package com.projectkaiser.scm.jenkins.api;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.projectkaiser.scm.jenkins.api.IJenkinsApi;
import com.projectkaiser.scm.jenkins.api.JenkinsApi;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

public class JenkinsApiTest  {
	
	IJenkinsApi api = new JenkinsApi("http://localhost:8080", "", "");
	
	
	public void JenkinsAPITest() throws ParserConfigurationException, SAXException, IOException {
		String config = api.getJobConfigXml("test job");
		api.createJob("newe4job", config);
	}
	
	
	private void doRunJob(String jobName) {
		api.runJob(jobName);
	}
	

	private void doChangeJob() throws ParserConfigurationException, SAXException, IOException {
		List<String> list = api.getJobsList();
		String jobName = list.get(0);
		Document doc = getJobDocument(jobName);
		NodeList nodes = doc.getElementsByTagName("disabled");
        nodes.item(0).setNodeValue("true");
        updateJob(jobName, doc);
	}
	
	private void updateJob(String jobName, Document doc) throws IOException {
		OutputFormat format = new OutputFormat (doc); 
        StringWriter stringOut = new StringWriter ();    
        XMLSerializer serial   = new XMLSerializer (stringOut, 
                                                    format);
        serial.serialize(doc);
        api.updateJobConfigXml(jobName, stringOut.toString());
	}

	private void doCreateJob() throws IOException {
		
	}
	
	private Document getJobDocument(String jobName) throws ParserConfigurationException, SAXException, IOException {
		
		String xml = api.getJobConfigXml(jobName);
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        
        StringReader sr = new StringReader(xml);
        InputSource is = new InputSource(sr);
        is.setEncoding("UTF-8");

        Document doc = db.parse(is);
        return doc;
	}
}
