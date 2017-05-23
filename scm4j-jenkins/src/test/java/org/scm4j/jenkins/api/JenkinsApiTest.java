package org.scm4j.jenkins.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.scm4j.jenkins.api.IJenkinsApi;
import org.scm4j.jenkins.api.JenkinsApi;
import org.scm4j.jenkins.api.exceptions.EPKJExists;
import org.scm4j.jenkins.api.exceptions.EPKJNotFound;
import org.scm4j.jenkins.data.JobDetailed;
import org.scm4j.jenkins.data.QueueItem;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSParser;
import org.w3c.dom.ls.LSSerializer;

public class JenkinsApiTest  {
	
	IJenkinsApi api; 
	
	private static final String TEST_JENKINS_URL = System.getProperty("SCM4J_TEST_JENKINS_URL") == null ? 
			System.getenv("SCM4J_TEST_JENKINS_URL") : System.getProperty("SCM4J_TEST_JENKINS_URL");
	private static final String TEST_JENKINS_USER = System.getProperty("SCM4J_TEST_JENKINS_USER") == null ? 
			System.getenv("SCM4J_TEST_JENKINS_USER") : System.getProperty("SCM4J_TEST_JENKINS_USER");
	private static final String TEST_JENKINS_PASS = System.getProperty("SCM4J_TEST_JENKINS_PASS") == null ? 
			System.getenv("SCM4J_TEST_JENKINS_PASS") : System.getProperty("SCM4J_TEST_JENKINS_PASS");

	private static final String TEST_JOB_XML_FN = "TestJob.xml";
	private static final String TEST_JOB_NAME = "scm4j_jenkins_test_job";
	private static final String NEW_JOB_NAME = "scm4j_jenkins_new_test_job";
	private static final String TEST_UNEXISTING_JOB_NAME = UUID.randomUUID().toString();
	private String ethalonJobXML = readResource(this.getClass(), TEST_JOB_XML_FN);

	@BeforeClass
	public static void setUpClass() {
		assumeTrue("Set SCM4J_TEST_JENKINS_URL environment variable as url to test Jenkins server to execute tests",
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
		
		try {
			api.getJobDetailed(TEST_UNEXISTING_JOB_NAME);
			fail(EPKJNotFound.class.getName() + " is not thrown");
		} catch (EPKJNotFound e) {
		}
	}
	
	@Test
	public void testCreateJob() throws Exception {
		assertTrue(api.getJobsList().contains(TEST_JOB_NAME));
		String actualJobXML = api.getJobConfigXml(TEST_JOB_NAME);
		assertTrue(XMLUnit.compareXML(actualJobXML, ethalonJobXML).identical());
		
		try {
			api.createJob(TEST_JOB_NAME, ethalonJobXML);
			fail(EPKJExists.class.getName() + " is not thrown");
		} catch (EPKJExists e) {
		}
	}
	
	@Test
	public void testEnqueueBuild() {
		Long buildId = api.enqueueBuild(TEST_JOB_NAME);
		assertTrue(buildId > 0);
		assertNotNull(api.getBuild(buildId));
		
		try {
			api.enqueueBuild(TEST_UNEXISTING_JOB_NAME);
			fail(EPKJNotFound.class.getName() + " is not thrown");
		} catch (EPKJNotFound e) {
		}
	}
	
	@Test 
	public void testGetBuild() {
		Long buildId = api.enqueueBuild(TEST_JOB_NAME);
		QueueItem item = api.getBuild(buildId);
		assertNotNull(item);
		assertNotNull(item.getId());
		assertEquals(item.getUrl(), String.format("queue/item/%d/", item.getId()));		
		
		try {
			 api.getBuild(Long.MIN_VALUE);
			fail(EPKJNotFound.class.getName() + " is not thrown");
		} catch (EPKJNotFound e) {
		}
	}

	@Test
	public void testChangeJob() throws Exception {
		Document doc = getJobDocument(TEST_JOB_NAME);
		NodeList nodes = doc.getElementsByTagName("disabled");
        nodes.item(0).setTextContent("true");
        updateJob(TEST_JOB_NAME, doc);
        
        doc = getJobDocument(TEST_JOB_NAME);
        nodes = doc.getElementsByTagName("disabled");
        assertEquals(nodes.item(0).getTextContent(), "true");
        
        try {
        	updateJob(TEST_UNEXISTING_JOB_NAME, doc);
        	fail(EPKJNotFound.class.getName() + " is not thrown");
        } catch (EPKJNotFound e) {
        }
	}
	
	@Test
	public void testCopyJob() {
		api.copyJob(TEST_JOB_NAME, NEW_JOB_NAME);
		try {
			assertTrue(api.getJobsList().contains(NEW_JOB_NAME));
		} finally {
			api.deleteJob(NEW_JOB_NAME);
		}
		
		try {
			api.copyJob(TEST_JOB_NAME, TEST_JOB_NAME);
			fail(EPKJExists.class.getName() + " is not thrown");
		} catch (EPKJExists e) {
		}
		
		try {
			api.copyJob(TEST_UNEXISTING_JOB_NAME, NEW_JOB_NAME);
			fail(EPKJNotFound.class.getName() + " is not thrown");
		} catch (EPKJNotFound e) {
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
		
		try {
			api.deleteJob(TEST_UNEXISTING_JOB_NAME);
			fail(EPKJNotFound.class.getName() + " is not thrown");
		} catch (EPKJNotFound e) {
		}
	}
	
	private void updateJob(String jobName, Document doc) throws Exception {
		DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();    
        DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("XML 3.0 LS 3.0");
	    LSSerializer serializer = impl.createLSSerializer();
        LSOutput output = impl.createLSOutput();
        output.setEncoding("UTF-8");
        ByteArrayOutputStream stringOut = new ByteArrayOutputStream();
        output.setByteStream(stringOut);
        serializer.write(doc, output);
        api.updateJobConfigXml(jobName, new String(stringOut.toByteArray(), "UTF-8"));
	}

	private Document getJobDocument(String jobName) throws Exception {
		DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();    
        DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("XML 3.0 LS 3.0");
        
        LSParser parser = impl.createLSParser(DOMImplementationLS.MODE_SYNCHRONOUS, null);
        LSInput input = impl.createLSInput();
        
        String xml = api.getJobConfigXml(jobName);
        input.setStringData(xml);

		Document doc = parser.parse(input);
        return doc;
	}
	
	public static String readResource(Class<?> cls, String resourceName) {
	    StringBuilder res = new StringBuilder();
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
