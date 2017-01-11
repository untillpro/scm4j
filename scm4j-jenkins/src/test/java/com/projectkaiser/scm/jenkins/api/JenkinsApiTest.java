package com.projectkaiser.scm.jenkins.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

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
import org.xml.sax.SAXException;

import com.projectkaiser.scm.jenkins.data.JobDetailed;
import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

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

		private static final String NEW_JOB_NAME = "pk_jenkins_new__test_job";
		
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
	public void testGetJobDetailed() {
		JobDetailed job = api.getJobDetailed(TEST_JOB_NAME);
		assertTrue(job != null);
		assertTrue(job.getDisplayName().equals(TEST_JOB_NAME));
	}
	
	@Test
	public void testCreateJob() throws Exception {
		assertTrue(api.getJobsList().contains(TEST_JOB_NAME));
		String actualJobXML = api.getJobConfigXml(TEST_JOB_NAME);
		assertTrue(XMLUnit.compareXML(actualJobXML, ethalonJobXML).identical());
	}
	
	@Test
	public void testEnqueueBuild() throws Exception {
		Long jobId = api.enqueueBuild(TEST_JOB_NAME);
		assertTrue(jobId > 0);
	}

	
	public void JenkinsAPITest() throws ParserConfigurationException, SAXException, IOException {
		String config = api.getJobConfigXml("test job");
		api.createJob("newe4job", config);
	}
	
	
	@Test
	public void testChangeJob() throws ParserConfigurationException, SAXException, IOException {
		Document doc = getJobDocument(TEST_JOB_NAME);
		NodeList nodes = doc.getElementsByTagName("disabled");
        nodes.item(0).setTextContent("true");
        updateJob(TEST_JOB_NAME, doc);
        
        doc = getJobDocument(TEST_JOB_NAME);
        nodes = doc.getElementsByTagName("disabled");
        assertEquals(nodes.item(0).getTextContent(), "true");
	}
	
	@Test
	public void testCopyJob() {
		api.copyJob(TEST_JOB_NAME, NEW_JOB_NAME);
		try {
			assertTrue(api.getJobsList().contains(NEW_JOB_NAME));
		} finally {
			api.deleteJob(NEW_JOB_NAME);
		}
	}
	
	@Test
	public void testDeleteJob() {
		api.deleteJob(TEST_JOB_NAME);
		try {
			assertFalse(api.getJobsList().contains(TEST_JOB_NAME));
		} finally { 
			api.createJob(TEST_JOB_NAME, ethalonJobXML);
		}
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
        ByteArrayInputStream input =  new ByteArrayInputStream(xml.getBytes("UTF-8"));
        Document doc = db.parse(input);
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
