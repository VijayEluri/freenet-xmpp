package se.su.it.smack.pubsub.elements;

public class SubscriptionsElement extends PubSubElement {

	public SubscriptionsElement(String node) {
		super(node);
	}
	
	public SubscriptionsElement() {
	}
	
	@Override
	public String getName() {
		return "subscriptions";
	}

	@Override
	public String getBeginXMLAttributes() {
		return "node='" + getNode() + "'";
	}

}
