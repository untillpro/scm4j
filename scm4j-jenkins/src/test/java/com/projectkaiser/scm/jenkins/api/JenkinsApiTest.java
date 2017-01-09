package com.projectkaiser.scm.jenkins.api;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.projectkaiser.scm.jenkins.data.JobDetailed;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;
import com.sun.xml.internal.ws.util.xml.XmlUtil;

public class JenkinsApiTest  {
	
	IJenkinsApi api; 
	
	private static final String TEST_JENKINS_URL = System.getProperty("PK_TEST_JENKINS_URL") == null ? 
			System.getenv("PK_TEST_JENKINS_URL") : System.getProperty("PK_TEST_JENKINS_URL");
	private static final String TEST_JENKINS_USER = System.getProperty("PK_TEST_JENKINS_USER") == null ? 
			System.getenv("PK_TEST_JENKINS_USER") : System.getProperty("PK_TEST_JENKINS_USER");
		private static final String TEST_JENKINS_PASS = System.getProperty("PK_TEST_JENKINS_PASS") == null ? 
				System.getenv("PK_TEST_JENKINS_PASS") : System.getProperty("PK_TEST_JENKINS_PASS");

		private static final String TEST_JOB_XML_FN = "TestJob.xml";

		private static final String TEST_JOB_NAME = "pk_jenkins_test_job";
		
		private String ethalonJobXML = readResource(this.getClass(), TEST_JOB_XML_FN);
	
	@BeforeClass
	public static void setUpClass() {
		assertTrue("Set PK_TEST_JENKINS_URL enviroment variable as url to test Jenkins server to execute tests", 
				TEST_JENKINS_URL != null);
	}
	
	@Before
	public void setUp() {
		api = new JenkinsApi(TEST_JENKINS_URL, TEST_JENKINS_USER, TEST_JENKINS_PASS);
		api.createJob(TEST_JOB_NAME, ethalonJobXML);
	}
	
	@After
	public void tearDown() {
		api.deleteJob(TEST_JOB_NAME);
	}
	
	@Test
	public void testCreateJob() throws Exception {
		assertTrue(api.getJobsList().contains(TEST_JOB_NAME));
		String actualJobXML = api.getJobConfigXml(TEST_JOB_NAME);
		assertTrue(XMLUnit.compareXML(actualJobXML, ethalonJobXML).identical());
	}
	
	@Test
	public void testRunJob() throws ParserConfigurationException, SAXException, IOException {
		Long jobId = api.runJob(TEST_JOB_NAME);
		assertTrue(jobId > 0);
		
	}
	
	
	public void JenkinsAPITest() throws ParserConfigurationException, SAXException, IOException {
		String config = api.getJobConfigXml("test job");
		api.createJob("newe4job", config);
	}
	
	@Test
	public void testChangeJob() throws ParserConfigurationException, SAXException, IOException {
		api.getJobDetailed(jobName)
		api.up
		Document doc = getJobDocument(TEST_JOB_NAME);
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
	
	public static String readResource(Class<?> cls, String resourceName) {
	    StringBuffer res = new StringBuffer();
	    char buf[] = new char[256];
	    try {
	        InputStream is = cls.getResourceAsStream(resourceName);
	        InputStreamReader iReader = new InputStreamReader(is, "utf-8");
	        BufferedReader br = new BufferedReader(iReader);
	        int nRead;
	        while ((nRead = br.read(buf)) > 0) {
	            res.append(buf, 0, nRead);
	        }
	    } catch (IOException e) {
	       throw new RuntimeException(e);
	    }
	    return res.toString();
	}
}
