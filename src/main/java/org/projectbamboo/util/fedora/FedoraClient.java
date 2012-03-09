package org.projectbamboo.util.fedora;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A simple interface to fedora that supports just the
 * basic functions needed for the FedoraRepository.
 */
public class FedoraClient {

    private static final Logger log = LoggerFactory.getLogger(FedoraClient.class);
    
    /**
     * The date format used to parse and generate dates as represented in
     * fedora.
     */
    private static DateFormat FEDORA_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    static {
        FEDORA_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    /**
     * Converts a fedora date String (like "2010-10-01T19:55:00.808Z") to
     * a java Date object.
     */
    public static Date parseFedoraDate(String fedoraDateStr) throws ParseException {
        return FEDORA_DATE_FORMAT.parse(fedoraDateStr);
    }

    /**
     * Converts a java Date object into a fedora-formatted date String.
     */
    public static String printFedoraDateString(Date date) {
        return FEDORA_DATE_FORMAT.format(date);
    }
    
    /**
     * An underlying HttpClient that handles the REST calls.  This
     * client is initialized at construction time.
     */
    protected HttpClient client;

    /** 
     * A document builder for building Documents from XML.  This
     * variable is not initialized at construction time, but instead
     * serves as a cache for the fist DocumentBuilder instance
     * created by calls that require it.  All access to this member
     * variable should be mediated through getDocumentBuilder().
     */
//    private DocumentBuilder documentBuilder;
    
    /**
     * An XPath implementation for dealing with Documents.  This
     * variable is not initialized at construction time, but instead
     * serves as a cache for the first XPath instance created by
     * calls that require it.  All access to this member variable
     * should be mediated through getXPath().
     */
    private XPath xpath;
    
    /**
     * The base URL for fedora calls. 
     */
    protected String fedoraBaseUrl;
    
    /**
     * Instantiates an unauthenticated FedoraClient.
     * @param fedoraHost the hostname of the fedora server
     * @param fedoraContextName the fedora context name 
     * (likely "fedora")
     * @param port fedora's port
     * @throws URISyntaxException 
     */
    public FedoraClient(String fedoraUrl, boolean readOnly) {
        this(null, null, fedoraUrl, readOnly);
    }
    
    /**
     * Instantiates a potentially authenticated FedoraClient.
     * @param username the username (or null for anonymous access)
     * @param password the password (or null for anonymous access)
     * @param fedoraHost the hostname of the fedora server
     * @param fedoraContextName the fedora context name 
     * (likely "fedora")
     * @param port fedora's port
     * @throws URISyntaxException 
     */
    public FedoraClient(String username, String password, String fedoraUrl, boolean readOnly) {
        fedoraBaseUrl = fedoraUrl;
        
        // Create an HTTP client for future REST calls
	MultiThreadedHttpConnectionManager mgr = new MultiThreadedHttpConnectionManager();
        client = new HttpClient(mgr);
        if (username != null) {
            this.client.getParams().setAuthenticationPreemptive(true);
            Credentials credentials = new UsernamePasswordCredentials(username, password);
            try {
                String host = new URI(fedoraBaseUrl).getHost();
                client.getState().setCredentials(new AuthScope(host, AuthScope.ANY_PORT), credentials);
            } catch (URISyntaxException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
    
    /**
     * Gets or creates a DocumentBuilder.
     */
    protected DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
//        if (this.documentBuilder != null) {
//            return this.documentBuilder;
//        } else {
            // create the document builder
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
//            this.documentBuilder = factory.newDocumentBuilder();
//            return this.documentBuilder;
	    return factory.newDocumentBuilder();
//        }
    }
    
    /**
     * Gets or creates and XPath configured with namespaces appropriate
     * for all other internal methods.
     */
    protected XPath getXPath() {
        if (this.xpath != null) {
            return this.xpath;
        } else {
            this.xpath = XPathFactory.newInstance().newXPath();
            this.xpath.setNamespaceContext(this.createNamespaceContext());
            return this.xpath;
        }
    }
    
    /**
     * Creates a MapNamespace context to be attached to the
     * XPath when it's generated.
     * 
     * Subclasses may override this method, but should ensure that the
     * MapNamespaceContext returned by this class has a superset of the
     * mappings to ensure that other methods will function as expected.
     */
    protected MapNamespaceContext createNamespaceContext() {
        // create the xpath with fedora namespaces built in
        MapNamespaceContext nsc = new MapNamespaceContext();
        nsc.setNamespace("fedora-types", "http://www.fedora.info/definitions/1/0/types/");
        nsc.setNamespace("sparql", "http://www.w3.org/2001/sw/DataAccess/rf1/result");
        nsc.setNamespace("foxml", "info:fedora/fedora-system:def/foxml#");
        nsc.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        nsc.setNamespace("fedora", "info:fedora/fedora-system:def/relations-external#");
        nsc.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
        nsc.setNamespace("fedora-model", "info:fedora/fedora-system:def/model#");
        nsc.setNamespace("oai", "http://www.openarchives.org/OAI/2.0/");
        nsc.setNamespace("oai_dc", "http://www.openarchives.org/OAI/2.0/oai_dc/", "http://www.openarchives.org/OAI/2.0/oai_dc.xsd");
        nsc.setNamespace("dc", "http://purl.org/dc/elements/1.1/"); 
        nsc.setNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        nsc.setNamespace("fedora-management", "http://www.fedora.info/definitions/1/0/management/", "http://www.fedora.info/definitions/1/0/datastreamHistory.xsd");
        return nsc;
    }
    
    /**
     * Gets the MapNamespaceContext associated with the XPath.
     */
    public MapNamespaceContext getMapNamespaceContext() {
        return (MapNamespaceContext) this.getXPath().getNamespaceContext();
    }
    
    /**
     * Gets the fedora server URL being used by this client instance.
     */
    public String getServerUrl() {
        return fedoraBaseUrl;
    }
    
    public FedoraObject getFedoraObject(String pid) throws FedoraException {
        return new FedoraObject(pid, this, false);
    }
    
    public List<FedoraObject> getRelatedObjects(String subjectPid, String predicate, String objectPid) throws FedoraException, IOException {
        String riSearchUrl = null;
        if (subjectPid == null && objectPid == null) {
            throw new IllegalArgumentException("either subject or object must be specified");
        } else if (subjectPid == null) {
            riSearchUrl = this.fedoraBaseUrl + (this.fedoraBaseUrl.endsWith("/") ? "" : "/") + "risearch?type=tuples&lang=itql&format=Sparql&query=select%20%24child%20from%20%3C%23ri%3E%20%0Awhere%20%24child%20%3C" + URLEncoder.encode(predicate, "UTF-8")+ "%3E%20%3Cinfo%3Afedora/" + URLEncoder.encode(objectPid, "UTF-8") + "%3E";
        } else {
            riSearchUrl = this.fedoraBaseUrl + (this.fedoraBaseUrl.endsWith("/") ? "" : "/") + "risearch?type=tuples&lang=itql&format=Sparql&query=select%20%24child%20from%20%3C%23ri%3E%20%0Awhere%20%3Cinfo%3Afedora/" + URLEncoder.encode(subjectPid, "UTF-8") + "%3E%20%3C" + URLEncoder.encode(predicate, "UTF-8")+ "%3E%20%24child";   
        }
        log.debug(riSearchUrl);
        List<FedoraObject> objects = new ArrayList<FedoraObject>();
        try {
            Document doc = getDocumentBuilder().parse(new InputSource( new URL(riSearchUrl).openStream()));
            NodeList children = doc.getDocumentElement().getElementsByTagName("child");
            for (int i = 0; i < children.getLength(); i ++) {
                objects.add(new FedoraObject(((Element) children.item(i)).getAttribute("uri").replace("info:fedora/", ""), this, false));
            }
        } catch (SAXException ex) {
            throw new FedoraException(ex);
        } catch (ParserConfigurationException ex) {
            throw new FedoraException(ex);
        }
        return objects;
    }
    
    /**
     * Creates a new object in the repository (not from an existing FOXML file).  Any
     * of the following parameters may be null.
     * @param pid the pid of the new object (or null to use an auto-generated pid).
     * This method will throw an exception if a pid is specified that already exists
     * in the repository.
     * @param label the label of the object
     * @param namespace the namespace for the created pid
     * @param ownerId the ownerId for the newly created object
     * @param logMessage a log message
     * @return the pid of the newly created object
     */
    public String createObject(String pid, String label, String ownerId, String namespace) throws FedoraException, HttpException, IOException {
        StringBuffer query = new StringBuffer();
        if (label != null) {
            query.append(query.length() == 0 ? "?" : "&");
            query.append("label=" + URLEncoder.encode(truncateLabel(label), "UTF-8"));
        }
        if (namespace != null) {
            query.append(query.length() == 0 ? "?" : "&");
            query.append("namespace=" + URLEncoder.encode(namespace, "UTF-8"));
        }
        if (ownerId != null) {
            query.append(query.length() == 0 ? "?" : "&");
            query.append("ownerId=" + URLEncoder.encode(ownerId, "UTF-8"));
        }
        /*
        if (logMessage != null) {
            query.append(query.length() == 0 ? "?" : "&");
            query.append("logMessage=" + URLEncoder.encode(logMessage, "UTF-8"));
        }
        */
        String url = this.fedoraBaseUrl + "/objects/" + (pid == null ? "new" : pid) + query.toString();
        PostMethod method = new PostMethod(url);
        int statusCode = this.client.executeMethod(method);
        if (statusCode != HttpStatus.SC_CREATED) {
            throw new RuntimeException("REST action \"" + url + "\" failed: " + method.getStatusLine());
        } else {
            pid = method.getResponseBodyAsString(1024);
            log.info(fedoraBaseUrl + ": Created object " + pid);
            return pid;
        }

    }    

    public String getDatastreamDisseminationUrl(String pid, String dsId) {
        return fedoraBaseUrl + "get/" + pid + "/" + dsId;
    }
    
    private static String truncateLabel(String label) {
        if (label == null || label.trim().length() == 0) {
            label = "";
        }
        if (label.length() > 255) {
            return label.substring(0, 250) + "...";
        } else {
            return label;
        }
    }
    
    /**
     * An object to be passed around that represents access 
     * to an object in fedora.  This object serves to both 
     * encapsulate calls to Fedora, and in some cases to cache
     * the results of such calls.  Optimistic locking and
     * transactions *could* be implemented at this level of
     * abstraction.
     */
    public class FedoraObject {
        
        private String pid;
        
        private ObjectProfile objectProfile;
        
        private List<String> cmodels;
        
        private List<String> dsIds;
        
        private boolean readonly;
        
        private FedoraObject(String pid, FedoraClient fc, boolean readonly) throws FedoraException {
            this.pid = pid;
            this.readonly = readonly;
            cmodels = null;
            dsIds = null;
            if (getObjectProfile() == null) {
                throw new FedoraObjectNotFoundException(pid + " was not found in the repository!");
            }
            
        }
        
        public String getPid() {
            return pid;
        }
        
        /**
         * Determines whether this FedoraObject is read-only.
         */
        public boolean isReadOnly() {
            return readonly;
        }
        
        /**
         * Fetches the object profile XML.
         * @param pid the pid of the object to be queried
         * @return an encapsulated version of the object profile
         */
        public ObjectProfile getObjectProfile() throws FedoraException {
            if (objectProfile != null) {
                return objectProfile;
            } else {
                String dateTime = null;
                String url = fedoraBaseUrl + "/objects/" + pid + "?format=xml" + (dateTime != null ? "&asOfDateTime=" + dateTime : "");
                try {
                    GetMethod get = new GetMethod(url);
                    client.executeMethod(get);
                    if (get.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                        return null;
                    } else if (isStatusSuccess(get.getStatusCode())) {
                        Document xml  = getDocumentBuilder().parse(get.getResponseBodyAsStream());
                        objectProfile = new ObjectProfile(xml, getXPath());
                        return objectProfile;
                    } else {
                        throw new FedoraException("REST action \"" + url + "\" failed: " + get.getStatusLine());
                    }
                } catch (HttpException ex) {
                    throw new FedoraException(ex);
                } catch (IOException ex) {
                    throw new FedoraException(ex);
                } catch (SAXException ex) {
                    throw new FedoraException(ex);
                } catch (ParserConfigurationException ex) {
                    throw new FedoraException(ex);
                }
            }
        }
        
        public List<String> listDatastreams() throws FedoraException, IOException {
            if (dsIds != null) {
                return dsIds;
            } else {
                GetMethod get = new GetMethod(fedoraBaseUrl + "/objects/" + pid + "/datastreams?format=xml");
                client.executeMethod(get);
                try {
                    Document dsDoc = getDocumentBuilder().parse(get.getResponseBodyAsStream());
                    NodeList elements = dsDoc.getDocumentElement().getChildNodes();
                    dsIds = new ArrayList<String>(elements.getLength());
                    for (int i = 0; i < elements.getLength(); i ++) {
                        if (elements.item(i) instanceof Element) {
                            Element el = (Element) elements.item(i);
                            if (el.getNodeName().equals("datastream")) {
                                dsIds.add(el.getAttribute("dsid"));
                            }
                        }
                    }
                    return dsIds;
                } catch (SAXException ex) {
                    throw new FedoraException(ex);
                } catch (ParserConfigurationException ex) {
                    throw new FedoraException(ex);
                }
            }
        }
        
        public InputStream getDatastream(String dsName) throws IOException {
            String asOfDateTime = null;
            String url = fedoraBaseUrl + "/objects/" + pid + "/datastreams/" + dsName + "/content" + (asOfDateTime != null ? "?asOfDateTime=" + URLEncoder.encode(asOfDateTime, "UTF-8") : "");
            GetMethod get = new GetMethod(url);
            client.executeMethod(get);
            if (!isStatusSuccess(get.getStatusCode())) {
                throw new RuntimeException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
            return get.getResponseBodyAsStream();
        }
        
        public List<String> getContentModelURIs() throws FedoraException, IOException {
            if (cmodels != null) {
                return cmodels;
            } else {
                cmodels = new ArrayList<String>();
                try {
                    String relsextUrl = fedoraBaseUrl + "/get/" + pid + "/RELS-EXT";
                    Document doc = getDocumentBuilder().parse(new InputSource(new URL(relsextUrl).openStream()));
                    NodeList descriptionNodeList = doc.getDocumentElement().getElementsByTagNameNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "Description");
                    NodeList cmodelNodeList = ((Element) descriptionNodeList.item(0)).getElementsByTagNameNS("info:fedora/fedora-system:def/model#", "hasModel");
                    for (int i = 0; i < cmodelNodeList.getLength(); i ++) {
                        cmodels.add(((Element) cmodelNodeList.item(i)).getAttributeNS("http://www.w3.org/1999/02/22-rdf-syntax-ns#", "resource"));
                    }
                } catch (SAXException ex) {
                    throw new FedoraException(ex);
                } catch (ParserConfigurationException ex) {
                    throw new FedoraException(ex);
                }
                return cmodels;
            }
        }
        
        public DatastreamProfile getDatastreamProfile(String dsId) throws HttpException, IOException, FedoraException, SAXException, ParserConfigurationException {
            Date date = null;
            String url = fedoraBaseUrl + "/objects/" + pid + "/datastreams/" + dsId + "?format=xml" + (date != null ? "&asOfDateTime=" + printFedoraDateString(date) : "");
            GetMethod get = new GetMethod(url);
            client.executeMethod(get);
            if (isStatusSuccess(get.getStatusCode())) {
                Document xml  = getDocumentBuilder().parse(get.getResponseBodyAsStream());
                return new DatastreamProfile(FedoraClient.this, xml.getDocumentElement());
            } else {
                throw new FedoraException("REST action \"" + url + "\" failed: " + get.getStatusLine());
            }
        }

        public void addOrReplaceDatastreamByReference(String dsId, String dsLocation, String controlGroup, String mimetype) throws Exception {
            if (!Pattern.matches("[MXRE]", controlGroup)) {
                throw new IllegalArgumentException("Invalid control group specified!");
            }
            if (mimetype == null) {
                mimetype = "application/octet-stream";
            }
            String url = fedoraBaseUrl + "/objects/" + pid + "/datastreams/" + dsId + "?controlGroup=" + controlGroup + "&mimeType=" + mimetype + "&dsLocation=" + URLEncoder.encode(dsLocation, "UTF-8");
            HttpMethod method = null;
            if (!listDatastreams().contains(dsId)) {
                // ADD datastream
                method = new PostMethod(url);
            } else {
                // MODIFY datastream
                method = new PutMethod(url);
            }
            client.executeMethod(method);
            if (!isStatusSuccess(method.getStatusCode())) {
                throw new FedoraException("Rest action \"" + url + "\" failed: " + method.getStatusLine());
            } else {
                // update the cached list of datastream ids
                if (!dsIds.contains(dsId)) {
                    dsIds.add(dsId);
                }
            }
        }

        public void addOrReplaceDatastream(String dsId, InputStream is, String controlGroup, String mimetype) throws Exception {
            if (mimetype == null) {
                mimetype = "application/octet-stream";
            }
            if (!Pattern.matches("[MXRE]", controlGroup)) {
                throw new IllegalArgumentException("Invalid control group specified!");
            }
            
            // write the stream to a temporary file 
            File tempFile = File.createTempFile("cmisserver-", ".tempfile");
            writeStreamToFile(is, tempFile);
            
            String url = fedoraBaseUrl + "/objects/" + pid + "/datastreams/" + dsId + "?controlGroup=" + controlGroup + "&mimeType=" + mimetype;
            HttpMethod method = null;
            if (!listDatastreams().contains(dsId)) {
                // ADD datastream
                PostMethod post = new PostMethod(url);
                Part[] parts = {
                        new FilePart(tempFile.getName(), tempFile)
                };
                post.setRequestEntity(
                        new MultipartRequestEntity(parts, post.getParams())
                    );
                method = post;
            } else {
                // MODIFY datastream
                PutMethod put = new PutMethod(url);
                Part[] parts = {
                        new FilePart(tempFile.getName(), tempFile)
                };
                put.setRequestEntity(
                        new MultipartRequestEntity(parts, put.getParams())
                    );
                method = put;
            }

            client.executeMethod(method);
            tempFile.delete();
            if (!isStatusSuccess(method.getStatusCode())) {
                log.error("Rest action \"" + url + "\" failed: " + method.getStatusLine() + "(method=" + method.getClass().getSimpleName() + ")" + " " + tempFile.getAbsolutePath());
                throw new FedoraException("Rest action failed!");
            } else {
                // update the cached list of datastream ids
                if (!dsIds.contains(dsId)) {
                    dsIds.add(dsId);
                }
            }
        }

        public void purgeDatastream(String dsId) throws FedoraException, HttpException, IOException {
            DeleteMethod delete = new DeleteMethod(fedoraBaseUrl + "/objects/" + pid + "/datastreams/" + dsId);
            client.executeMethod(delete);
            if (!isStatusSuccess(delete.getStatusCode())) {
                throw new FedoraException("Invalid HTTP Status code: " + delete.getStatusLine());
            } else {
                dsIds.remove(dsId);
            }
        }
        
        public void removeRelationship(String subjectPid, String predicate, String objectPid) throws HttpException, IOException, FedoraException {
            DeleteMethod delete = new DeleteMethod(fedoraBaseUrl + "/objects/" + pid + "/relationships"
                    + "?subject=" + URLEncoder.encode("info:fedora/" + subjectPid, "UTF-8")
                    + "&predicate=" + URLEncoder.encode(predicate, "UTF-8")
                    + "&object=" + URLEncoder.encode("info:fedora/" + objectPid, "UTF-8"));
            client.executeMethod(delete);
            if (!isStatusSuccess(delete.getStatusCode())) {
                throw new FedoraException("Invalid HTTP Status code: " + delete.getStatusLine());
            } else {
                // clear the content model cache
                cmodels = null;
            }
        }
        
        public void addRelationship(String objectPid, String predicate, String subjectPid) throws FedoraException, HttpException, IOException {
            String url = fedoraBaseUrl + "/objects/" + pid + "/relationships/new"
                    + "?subject=" + URLEncoder.encode("info:fedora/" + subjectPid, "UTF-8")
                    + "&predicate=" + URLEncoder.encode(predicate, "UTF-8")
                    + "&object=" + URLEncoder.encode("info:fedora/" + objectPid, "UTF-8")
                    + "&isLiteral=" + URLEncoder.encode(String.valueOf(false), "UTF-8");
            PostMethod post = new PostMethod(url);
            client.executeMethod(post);
            if (!isStatusSuccess(post.getStatusCode())) {
                throw new FedoraException("Invalid HTTP Status code: " + post.getStatusLine() + " for request of:" + url);
            } else {
                // clear the content model cache
                cmodels = null;
            }
        }
        
        public void purge() throws HttpException, IOException, FedoraException {
            DeleteMethod delete = new DeleteMethod(fedoraBaseUrl + "/objects/" + pid);
            client.executeMethod(delete);
            if (!isStatusSuccess(delete.getStatusCode())) {
                throw new FedoraException("Invalid HTTP Status code: " + delete.getStatusLine());
            } else {
                // ensure that no further actions work for this object
                pid = null;
                cmodels = null;
                dsIds = null;
            }
        }

    }
    
    public static final boolean isStatusSuccess(int code) {
        return(code == HttpStatus.SC_OK || code == HttpStatus.SC_ACCEPTED || code == HttpStatus.SC_CREATED || code == HttpStatus.SC_NO_CONTENT);
    }

    
    private static void writeStreamToFile(InputStream is, File file) throws IOException {
        OutputStream output = new FileOutputStream(file);  
        ReadableByteChannel inputChannel = Channels.newChannel(is);  
        WritableByteChannel outputChannel = Channels.newChannel(output);  
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);  
        while (inputChannel.read(buffer) != -1) {  
            buffer.flip();  
            outputChannel.write(buffer);  
            buffer.compact();  
        }  
        buffer.flip();  
        while (buffer.hasRemaining()) {  
            outputChannel.write(buffer);  
        }  
       inputChannel.close();  
       outputChannel.close();
    }

}
