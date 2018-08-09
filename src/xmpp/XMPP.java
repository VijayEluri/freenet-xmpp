package xmpp;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.XMPPException;

import freenet.clients.http.InfoboxNode;
import freenet.clients.http.PageMaker;
import freenet.clients.http.PageNode;
import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.node.DarknetPeerNode;
import freenet.node.FSParseException;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.Base64;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.SimpleFieldSet;
import freenet.support.api.HTTPRequest;

public class XMPP implements FredPlugin, FredPluginHTTP, FredPluginVersioned, FredPluginThreadless {
    private static final String SELF_URI = "/plugins/xmpp.XMPP";

    private PluginRespirator pr;

    private PageMaker pm;

    private XMPP2 xmpp;

    public void runPlugin(PluginRespirator pr) {
	this.pr = pr;
	pm = pr.getPageMaker();
    }

    public void terminate() {

    }

    public String handleHTTPGet(HTTPRequest arg0) throws PluginHTTPException {
	return makeLoginPage();
    }

    public String handleHTTPPost(HTTPRequest request) throws PluginHTTPException {
	String pass = request.getPartAsString("formPassword", 32);
	if ((pass.length() == 0) || !pass.equals(pr.getNode().clientCore.formPassword))
	    return makeErrorPage("Buh! Invalid form password", "Invalid form password");
	else if (request.isPartSet("user") && request.isPartSet("server") && request.isPartSet("password")) {
	    String user = request.getPartAsString("user", 256);
	    String server = request.getPartAsString("server", 256);
	    String password = request.getPartAsString("password", 256);
	    boolean sasl = request.isPartSet("sasl");
	    boolean tls = request.isPartSet("tls");
	    boolean selfsigned = request.isPartSet("selfsigned");
	    boolean publish = "1".equals(request.getPartAsString("publish", 1));
	    xmpp = new XMPP2(user, server, password, sasl, tls, selfsigned);
	    try {
		xmpp.login();
		String page = publish ? makePublishPage() : makeDeletePage();
		xmpp.disconnect();
		return page;
	    } catch (XMPPException e) {
		Logger.error(this, e.getMessage());
		return makeErrorPage(e.getMessage(), "Failed to login");
	    }
	} else {
	    return makeLoginPage();
	}
    }

    private String makeLoginPage() {
	PageNode pageNode = pm.getPageNode("XMPP login", null);
	HTMLNode infoBox = pm.getInfobox("infobox-information", "Notice", pageNode.content);
	HTMLNode loginBox = pm.getInfobox("infobox-normal", "Login", pageNode.content);

	infoBox.addChild("#", "Only user that are allowed to subscribe on your presence can see your "
		+ "published reference. Usually these are the users on your roster(friend list). However "
		+ "anyone can see that you have a darknet reference published even if they can't see the "
		+ "reference itself. Don't continue if this is a problem.");

	HTMLNode form = pr.addFormChild(loginBox, SELF_URI, "fbForm");
	HTMLNode table = form.addChild("table");
	HTMLNode row1 = table.addChild("tr");
	HTMLNode row2 = table.addChild("tr");
	HTMLNode row3 = table.addChild("tr");
	HTMLNode row4td = table.addChild("tr").addChild("td", "colspan", "2");

	row1.addChild("td").addChild("#", "User name: ");
	row1.addChild("td").addChild("input", new String[] { "type", "name" }, new String[] { "text", "user" });
	row2.addChild("td").addChild("#", "Server: ");
	row2.addChild("td").addChild("input", new String[] { "type", "name" }, new String[] { "text", "server" });
	row3.addChild("td").addChild("#", "Password: ");
	row3.addChild("td").addChild("input", new String[] { "type", "name" }, new String[] { "password", "password" });

	row4td.addChild("#", "Publish reference: ");
	row4td.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "radio",
		"publish", "1", "yes" });
	row4td.addChild("#", "  Delete reference: ");
	row4td.addChild("input", new String[] { "type", "name", "value" }, new String[] { "radio", "publish", "0" });

	table = form.addChild("table");
	row1 = table.addChild("tr");
	row2 = table.addChild("tr");
	row3 = table.addChild("tr");

	row1.addChild("td").addChild("#", "Use SASL: ");
	row1.addChild("td").addChild(makeCheckbox("sasl", true, true));
	row2.addChild("td").addChild("#", "Require TLS: ");
	row2.addChild("td").addChild(makeCheckbox("tls", true, true));
	row3.addChild("td").addChild("#", "Accept self-signed TLS-certificates: ");
	row3.addChild("td").addChild(makeCheckbox("selfsigned", true, false));
	form.addChild("br");
	form.addChild("input", "type", "submit");

	return pageNode.outer.generate();
    }

    private String makePublishPage() {
	PageNode pageNode = pm.getPageNode("XMPP", null);
	HTMLNode statusNode = pm.getInfobox("infobox-normal", "Status", pageNode.content);
	int referencesAdded = 0;
	int referencesFailed = 0;
	boolean pep = false;
	boolean pubSub = false;

	// Set containing identities of references we don't want to add
	Set<String> peers = new HashSet<String>();
	peers.add(getIdentityString());
	for (DarknetPeerNode node : pr.getNode().getDarknetConnections())
	    peers.add(node.getIdentityString());

	// Determine if PEP and PubSub is supported
	try {
	    pubSub = xmpp.hasGoodPubSub();
	    pep = xmpp.hasPEP();
	} catch (XMPPException e) {
	    error(statusNode, "Failed to determine if PubSub and PEP are supported, assuming they are not", e
		    .getMessage());
	}

	// We can only publish and download our own published items if our
	// server supports both PubSub and PEP
	if (pubSub && pep) {
	    // Since publish-options isn't well supported we have to create the
	    // node manually in order to get the items persistent. If the node
	    // already exists we except this to fail
	    try {
		xmpp.createNode("presence");
		log(statusNode, "Created pubsub node");
	    } catch (XMPPException e) {
	    }

	    // There may be multiple references published if this plugin is used
	    // with the same jid at different freenet nodes. All the references
	    // are added
	    Map<String, String> ownReferences;
	    try {
		ownReferences = xmpp.retrieveAllItems(null);
	    } catch (XMPPException e) {
		error(statusNode, "Failed to download own references", e.getMessage());
		ownReferences = Collections.emptyMap();
	    }

	    for (Map.Entry<String, String> entry : ownReferences.entrySet())
		if (!peers.contains(entry.getKey())) {
		    try {
			addNodeRef(entry.getValue(), "");
			peers.add(entry.getKey());
			referencesAdded++;
			log(statusNode, "Added own reference " + getRefName(entry.getValue()));
		    } catch (Exception e) {
			referencesFailed++;
			log(statusNode, "Failed to add own reference with identity " + entry.getKey());
		    }
		}

	    // We only publish our reference if it doesn't already exists
	    if (!ownReferences.containsKey(getIdentityString()))
		try {
		    xmpp.publish(getIdentityString(), getNodeRef());
		    log(statusNode, "Published darknet node reference");
		} catch (XMPPException e) {
		    error(statusNode, "Failed to publish darknet node reference", e.getMessage());
		}
	} else {
	    error(statusNode, "PubSub or PEP not fully supported by the server. You can "
		    + "download your friends references but they can't download yours", null);
	}

	// We can download our friends references even if our server doesn't
	// support PEP or PubSub. For all friends all the references are added
	for (RosterEntry rosterEntry : xmpp.getRoster().getEntries()) {
	    Map<String, String> friendReferences;
	    try {
		friendReferences = xmpp.retrieveAllItems(rosterEntry.getUser());
	    } catch (XMPPException e) {
		Logger.normal(this, "Failed to download references for " + rosterEntry.getUser());
		continue;
	    }

	    for (Map.Entry<String, String> entry : friendReferences.entrySet())
		if (!peers.contains(entry.getKey()))
		    try {
			addNodeRef(entry.getValue(), rosterEntry.getUser());
			peers.add(entry.getKey());
			referencesAdded++;
			log(statusNode, "Added reference " + getRefName(entry.getValue()) + " for "
				+ rosterEntry.getUser());
		    } catch (Exception e) {
			referencesFailed++;
			error(statusNode, "Failed to add reference for " + rosterEntry.getUser(), e.getMessage());
		    }
	}

	if (referencesAdded == 0 && referencesFailed == 0)
	    log(statusNode, "Found no references to add");
	else if (referencesAdded > 0)
	    log(statusNode, "Added a total of " + referencesAdded + " references");
	else
	    // referencesFailed > 0
	    log(statusNode, "Failed to add " + referencesFailed + " references");

	return pageNode.outer.generate();
    }

    private String makeDeletePage() {
	try {
	    if (xmpp.hasPEP() && xmpp.hasPubSub()) {
		xmpp.deleteNode();
		return makeSuccessPage("Successfully deleted the virtual PubSub node", "Success");
	    } else
		return makeSuccessPage("Since the server doesn't support PEP or PubSub there "
			+ "isn't any virtual PubSub node to remove", "Success");
	} catch (XMPPException e) {
	    return makeErrorPage(e.getMessage(), "Failed to delete the PubSub node");
	}
    }

    private String makeErrorPage(String error, String header) {
	PageNode pageNode = pm.getPageNode("XMPP error", null);
	HTMLNode errorBox = pm.getInfobox("infobox-alert", header, pageNode.content);
	errorBox.addChild("#", error);
	return pageNode.outer.generate();
    }

    private String makeSuccessPage(String error, String header) {
	PageNode pageNode = pm.getPageNode("XMPP", null);
	HTMLNode errorBox = pm.getInfobox("infobox-success", header, pageNode.content);
	errorBox.addChild("#", error);
	return pageNode.outer.generate();
    }

    private HTMLNode makeCheckbox(String name, boolean enabled, boolean checked) {
	if (!enabled && !checked)
	    return new HTMLNode("input", new String[] { "type", "name", "disabled" }, new String[] { "checkbox", name,
		    "disabled" });
	else if (!enabled && checked)
	    return new HTMLNode("input", new String[] { "type", "name", "checked", "disabled" }, new String[] {
		    "checkbox", name, "checked", "disabled" });
	else if (enabled && !checked)
	    return new HTMLNode("input", new String[] { "type", "name" }, new String[] { "checkbox", name });
	else
	    // if (enabled && checked)
	    return new HTMLNode("input", new String[] { "type", "name", "checked" }, new String[] { "checkbox", name,
		    "checked" });
    }

    // Writes status both to a HTMLNode and the Logger
    private void log(HTMLNode node, String status) {
	node.addChild("#", status);
	node.addChild("br");
	Logger.normal(this, status);
    }

    // Writes status both to a HTMLNode and the Logger
    private void error(HTMLNode node, String message, String fullMessage) {
	node.addChild("b").addChild("font", "color", "red").addChild("#", message);
	node.addChild("br");
	Logger.error(this, message);
	if (fullMessage != null)
	    Logger.error(this, fullMessage);
    }

    public String getVersion() {
	return "0.1";
    }

    private void addNodeRef(String ref, String jid) throws IOException, FSParseException, PeerParseException,
	    ReferenceSignatureVerificationException {
	SimpleFieldSet fs = new SimpleFieldSet(ref, false, true);
	DarknetPeerNode pn = pr.getNode().createNewDarknetNode(fs);
	pn.setPrivateDarknetCommentNote(jid);
	pr.getNode().addPeerConnection(pn);
	Logger.normal(this, "Adding reference for " + jid);
    }

    private String getNodeRef() {
	return pr.getNode().exportDarknetPublicFieldSet().toString();
    }

    private String getIdentityString() {
	return Base64.encode(pr.getNode().getDarknetIdentity());
    }

    private String getRefName(String ref) {
	try {
	    SimpleFieldSet fs = new SimpleFieldSet(ref, false, true);
	    DarknetPeerNode pn = pr.getNode().createNewDarknetNode(fs);
	    return pn.getName();
	} catch (Exception e) {

	}
	return null;
    }
}