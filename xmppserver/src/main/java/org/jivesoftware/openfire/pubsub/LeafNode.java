/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.pubsub;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pep.PEPService;
import org.jivesoftware.openfire.pubsub.models.AccessModel;
import org.jivesoftware.openfire.pubsub.models.PublisherModel;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.*;

import static org.jivesoftware.openfire.muc.spi.IQOwnerHandler.parseFirstValueAsBoolean;

/**
 * A type of node that contains published items only. It is NOT a container for
 * other nodes.
 *
 * @author Matt Tucker
 */
public class LeafNode extends Node {

    private Logger Log;

    /**
     * Flag that indicates whether to persist items to storage. Note that when the
     * variable is false then the last published item is the only items being saved
     * to the backend storage.
     */
    private boolean persistPublishedItems;
    /**
     * Maximum number of published items to persist. Note that all nodes are going to persist
     * their published items. The only difference is the number of the last published items
     * to be persisted. Even nodes that are configured to not use persistent items are going
     * to save the last published item.
     */
    private int maxPublishedItems;
    /**
     * The maximum payload size in bytes.
     */
    private int maxPayloadSize;
    /**
     * Flag that indicates whether to send items to new subscribers.
     */
    private boolean sendItemSubscribe;
    /**
     * The last item published to this node.  In a cluster this may have occurred on a different cluster node.
     */
    private transient PublishedItem lastPublished;

    // TODO Add checking of max payload size. Return <not-acceptable> plus a application specific error condition of <payload-too-big/>.

    public LeafNode( PubSubService.UniqueIdentifier serviceId, CollectionNode parentNode, String nodeID, JID creator, boolean subscriptionEnabled, boolean deliverPayloads, boolean notifyConfigChanges, boolean notifyDelete, boolean notifyRetract, boolean presenceBasedDelivery, AccessModel accessModel, PublisherModel publisherModel, String language, ItemReplyPolicy replyPolicy, boolean persistPublishedItems, int maxPublishedItems, int maxPayloadSize, boolean sendItemSubscribe)
    {
        super(serviceId, parentNode, nodeID, creator, subscriptionEnabled, deliverPayloads, notifyConfigChanges, notifyDelete, notifyRetract, presenceBasedDelivery, accessModel, publisherModel, language, replyPolicy);
        Log = LoggerFactory.getLogger(getClass().getName() + "[" + serviceId + "#" + nodeID +"]");
        this.persistPublishedItems = persistPublishedItems;
        this.maxPublishedItems = maxPublishedItems;
        this.maxPayloadSize = maxPayloadSize;
        this.sendItemSubscribe = sendItemSubscribe;
    }

    public LeafNode(PubSubService.UniqueIdentifier serviceId, CollectionNode parentNode, String nodeID, JID creator, DefaultNodeConfiguration defaultConfiguration) {
        super(serviceId, parentNode, nodeID, creator, defaultConfiguration);
        Log = LoggerFactory.getLogger(getClass().getName() + "[" + serviceId + "#" + nodeID +"]");
        this.persistPublishedItems = defaultConfiguration.isPersistPublishedItems();
        this.maxPublishedItems = defaultConfiguration.getMaxPublishedItems();
        this.maxPayloadSize = defaultConfiguration.getMaxPayloadSize();
        this.sendItemSubscribe = defaultConfiguration.isSendItemSubscribe();
    }

    public LeafNode() { // to be used only for serialization;
        super();
        Log = LoggerFactory.getLogger(getClass().getName()); // _should_ be replaced in readExternal()
    }

    @Override
    protected void configure(FormField field) throws NotAcceptableException {
        if ("pubsub#persist_items".equals(field.getVariable())) {
            persistPublishedItems = parseFirstValueAsBoolean( field, true );
            Log.trace("Configuring {}: {}", field.getVariable(), persistPublishedItems);
        }
        else if ("pubsub#max_payload_size".equals(field.getVariable())) {
            maxPayloadSize = field.getFirstValue() != null ? Integer.parseInt( field.getFirstValue() ) : 5120;
            Log.trace("Configuring {}: {}", field.getVariable(), maxPayloadSize);
        }
        else if ("pubsub#send_item_subscribe".equals(field.getVariable())) {
            sendItemSubscribe = parseFirstValueAsBoolean( field, true );
            Log.trace("Configuring {}: {}", field.getVariable(), sendItemSubscribe);
        }
    }

    @Override
    void postConfigure(DataForm completedForm) {
        if (!persistPublishedItems) {
            Log.trace("Configuring to always save the last published item when not configured to use persistent items.");
            maxPublishedItems = 1;
        }
        else {
            FormField field = completedForm.getField("pubsub#max_items");
            if (field != null) {
                maxPublishedItems = field.getFirstValue() != null ? Integer.parseInt( field.getFirstValue() ) : 50;
                Log.trace("Configuring {}: {}", field.getVariable(), maxPublishedItems);
            }
        }
    }

    @Override
    protected void addFormFields(DataForm form, Locale preferredLocale, boolean isEditing) {
        super.addFormFields(form, preferredLocale, isEditing);

        FormField typeField = form.getField("pubsub#node_type");
        typeField.addValue("leaf");
        
        FormField formField = form.addField();
        formField.setVariable("pubsub#send_item_subscribe");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(
                    LocaleUtils.getLocalizedString("pubsub.form.conf.send_item_subscribe", preferredLocale));
        }
        formField.addValue(sendItemSubscribe);

        formField = form.addField();
        formField.setVariable("pubsub#persist_items");
        if (isEditing) {
            formField.setType(FormField.Type.boolean_type);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.persist_items", preferredLocale));
        }
        formField.addValue(persistPublishedItems);

        formField = form.addField();
        formField.setVariable("pubsub#max_items");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.max_items", preferredLocale));
        }
        formField.addValue(maxPublishedItems);

        formField = form.addField();
        formField.setVariable("pubsub#max_payload_size");
        if (isEditing) {
            formField.setType(FormField.Type.text_single);
            formField.setLabel(LocaleUtils.getLocalizedString("pubsub.form.conf.max_payload_size", preferredLocale));
        }
        formField.addValue(maxPayloadSize);

    }

    @Override
    protected void deletingNode() {
    }

    public synchronized void setLastPublishedItem(PublishedItem item)
    {
        if ((lastPublished == null) || (item != null) && item.getCreationDate().after(lastPublished.getCreationDate())) {
            Log.trace("Set last published item to: {}", item.getID());
            lastPublished = item;
        }
    }

    public int getMaxPayloadSize() {
        return maxPayloadSize;
    }

    public boolean isPersistPublishedItems() {
        return persistPublishedItems;
    }

    public int getMaxPublishedItems() {
        return maxPublishedItems;
    }

    /**
     * Returns true if an item element is required to be included when publishing an
     * item to this node. When an item is included then the item will have an item ID
     * that will be included when sending items to node subscribers.<p>
     *
     * Leaf nodes that are transient and do not deliver payloads with event notifications
     * do not require an item element. If a user tries to publish an item to a node
     * that does not require items then an error will be returned.
     *
     * @return true if an item element is required to be included when publishing an
     *         item to this node.
     */
    public boolean isItemRequired() {
        return isPersistPublishedItems() || isPayloadDelivered();
    }

    /**
     * Publishes the list of items to the node. Event notifications will be sent to subscribers
     * for the new published event, as well as all connected resources of the owner, in case the node
     * is a node in a PEP service.<p>
     *
     * The published event may or may not include an item. When the
     * node is not persistent and does not require payloads then an item is not going to be created
     * or included in the event notification.<p>
     *
     * When an affiliate has many subscriptions to the node, the affiliate will get a
     * notification for each set of items that affected the same list of subscriptions.<p>
     *
     * When an item is included in the published event then a new {@link PublishedItem} is
     * going to be created and added to the list of published item. Each published item will
     * have a unique ID in the node scope. The new published item will be added to the end
     * of the published list to keep the chronological order. When the max number of published
     * items is exceeded then the oldest published items will be removed.<p>
     *
     * For performance reasons the newly added published items and the deleted items (if any)
     * are saved to the database using a background thread. Sending event notifications to
     * node subscribers may also use another thread to ensure good performance.<p>
     *
     * @param publisher the full JID of the user that sent the new published event.
     * @param itemElements list of dom4j elements that contain info about the published items.
     */
    public void publishItems(JID publisher, List<Element> itemElements) {
        Log.trace("Publisher '{}' is publishing {} item(s)", publisher, itemElements.size());
        List<PublishedItem> newPublishedItems = new ArrayList<>();
        if (isItemRequired()) {
            String itemID;
            Element payload;
            PublishedItem newItem;
            for (Element item : itemElements) {
                itemID = item.attributeValue("id");
                List<Element> entries = item.elements();
                payload = entries.isEmpty() ? null : entries.get(0);
                
                // Make sure that the published item has a unique ID if NOT assigned by publisher
                if (itemID == null) {
                    itemID = UUID.randomUUID().toString();
                }

                // Create a new published item
                newItem = new PublishedItem(this, publisher, itemID, new Date(CacheFactory.getClusterTime()));
                newItem.setPayload(payload);
                Log.trace("Created new PublishedItem instance with itemID {}", itemID);

                // Add the new item to the list of published items
                newPublishedItems.add(newItem);
                setLastPublishedItem(newItem);
                // Add the new published item to the queue of items to add to the database. The
                // queue is going to be processed by another thread
                if (isPersistPublishedItems()) {
                    Log.trace("Adding PublishedItem in persistence provider as node is configured to persist published items");
                    XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().savePublishedItem(newItem);
                }
            }
        }

        // Build event notification packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        // Broadcast event notification to subscribers and parent node subscribers
        Set<NodeAffiliate> affiliatesToNotify = getAffiliatesToNotify();
        Log.trace("Built event notification stanza to broadcast notification to {} affiliate(s)", affiliatesToNotify.size());

        // TODO Use another thread for this (if # of subscribers is > X)????
        for (NodeAffiliate affiliate : affiliatesToNotify) {
            affiliate.sendPublishedNotifications(message, event, this, newPublishedItems);
        }

        // Invoke event listeners.
        PubSubEventDispatcher.dispatchItemsPublished(this.getUniqueIdentifier(), newPublishedItems);
    }

    /**
     * Retrieves the collection of affiliates that should be sent notifications upon changes to this node.
     *
     * @return A list of node affiliates. Possibly empty.
     */
    public Set<NodeAffiliate> getAffiliatesToNotify() {
        Log.trace("Getting affiliates to notify...");

        Set<NodeAffiliate> affiliatesToNotify = new HashSet<>(affiliates);
        Log.trace("... found {} direct affiliate(s)", affiliates.size());

        // Get affiliates that are subscribed to a parent in the hierarchy of parent nodes
        for (CollectionNode parentNode : getParents()) {
            Set<NodeAffiliate> parentAffiliates = new HashSet<>();
            for (NodeSubscription subscription : parentNode.getSubscriptions()) {
                // OF-2365: Prevent sending notifications to subscribers that are not allowed to access this node.
                if (parentNode.getAccessModel().canAccessItems(this, subscription.getOwner(), subscription.getJID() )
                    && accessModel.canAccessItems(this, subscription.getOwner(), subscription.getJID()))
                {
                    parentAffiliates.add(subscription.getAffiliate());
                }
            }
            Log.trace("... found {} applicable affiliate(s) in parent node {}", parentAffiliates.size(), parentNode.getNodeID());
            affiliatesToNotify.addAll(parentAffiliates);
        }

        // XEP-0136 specifies that all connected resources of the owner of the PEP service should also get a notification (pending filtering)
        // To ensure that happens, the affiliate that represents the owner of the PEP server is added here, if it's not already present.
        if (getService() instanceof PEPService && affiliatesToNotify.stream().noneMatch( a -> a.getAffiliation().equals(NodeAffiliate.Affiliation.owner))) {
            final NodeAffiliate owner = getService().getRootCollectionNode().getAffiliate( getService().getAddress() );
            Log.trace("... added owner of the PEP service");
            affiliatesToNotify.add(owner);
        }
        Log.trace("In total {} unique affiliate(s) were identified.", affiliatesToNotify.size());
        return affiliatesToNotify;
    }

    /**
     * Deletes the list of published items from the node. Event notifications may be sent to
     * subscribers for the deleted items, as well as all connected resources of the service owner,
     * if the service is a PEP service.<p>
     *
     * When an affiliate has many subscriptions to the node, the affiliate will get a notification
     * for each set of items that affected the same list of subscriptions.<p>
     *
     * @param toDelete list of items that were deleted from the node.
     */
    public void deleteItems(List<PublishedItem> toDelete) {
        Log.trace("Deleting {} item(s)", toDelete.size());
        // Remove deleted items from the database
        for (PublishedItem item : toDelete) {
            Log.trace("Removing PublishedItem from persistence provider");
            XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().removePublishedItem(item);
            if (lastPublished != null && lastPublished.getID().equals(item.getID())) {
                Log.trace("Removed item was previously the last published item. Setting last published item to null.");
                lastPublished = null;
            }
        }
        if (isNotifiedOfRetract()) {
            // Build packet to broadcast to subscribers
            Message message = new Message();
            Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
            // Send notification that items have been deleted to subscribers and parent node subscribers
            Set<NodeAffiliate> affiliatesToNotify = getAffiliatesToNotify();
            Log.trace("Built event notification stanza to broadcast notification to {} affiliate(s)", affiliatesToNotify.size());

            // TODO Use another thread for this (if # of subscribers is > X)????
            for (NodeAffiliate affiliate : affiliatesToNotify) {
                affiliate.sendDeletionNotifications(message, event, this, toDelete);
            }

            // XEP-0136 specifies that all connected resources of the owner of the PEP service should also get a notification.
            if ( getService() instanceof PEPService )
            {
                final PEPService service = (PEPService) getService();
                Element items = event.addElement("items");
                items.addAttribute("node", getUniqueIdentifier().getNodeId());
                for (PublishedItem publishedItem : toDelete) {
                    // Add retract information to the event notification
                    Element item = items.addElement("retract");
                    if (isItemRequired()) {
                        item.addAttribute("id", publishedItem.getID());
                    }

                    // Send the notification
                    final Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(service.getAddress().getNode());
                    Log.trace("Also notifying {} connected resource(s) of the owner of the PEP service: {}", sessions.size(), service.getAddress());

                    for (final ClientSession session : sessions) {
                        service.sendNotification( this, message, session.getAddress() );
                    }

                    // Remove the added items information
                    event.remove(items);
                }
            }
        }

        // Invoke event listeners.
        PubSubEventDispatcher.dispatchItemsDeleted(this.getUniqueIdentifier(), toDelete);
    }

    /**
     * Sends an IQ result with the list of items published to the node. Item ID and payload are always included.
     * Should only be used for use-cases described in section "6.5 Retrieve Items from a Node" from XEP-0060 (as
     * opposed to processing of notifications or service discovery, which are allowed to discard payloads).
     *
     * @param originalRequest the IQ packet sent by a subscriber (or anyone) to get the node items.
     * @param publishedItems the list of published items to send to the subscriber.
     */
    void sendPublishedItems(IQ originalRequest, List<PublishedItem> publishedItems) {
        Log.trace("Sending {} published item(s) in response to request from '{}'", publishedItems.size(), originalRequest.getFrom());
        IQ result = IQ.createResultIQ(originalRequest);
        Element pubsubElem = result.setChildElement("pubsub", "http://jabber.org/protocol/pubsub");
        Element items = pubsubElem.addElement("items");
        items.addAttribute("node", nodeID);
        
        for (PublishedItem publishedItem : publishedItems) {
            Element item = items.addElement("item");
            if (isItemRequired()) {
                item.addAttribute("id", publishedItem.getID());
            }
            item.add(publishedItem.getPayload().createCopy());
        }
        // Send the result
        getService().send(result);
    }

    @Override
    public PublishedItem getPublishedItem(String itemID) {
        if (!isItemRequired()) {
            Log.trace("Get published item {}, but returning null, as node is configured to not require item.", itemID);
            return null;
        }

        final PublishedItem.UniqueIdentifier itemIdentifier = new PublishedItem.UniqueIdentifier( getUniqueIdentifier(), itemID );
        synchronized (this) {
            if (lastPublished != null && lastPublished.getUniqueIdentifier().equals( itemIdentifier )) {
                Log.trace("Get published item {} that is the last published item", itemID);
                return lastPublished;
            }
        }
        final PublishedItem publishedItem = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().getPublishedItem(this, itemIdentifier);
        Log.trace("Get published item {} from persistence provider{}", itemID, publishedItem == null ? ", but it did not exist there." : ".");
        return publishedItem;
    }

    @Override
    public List<PublishedItem> getPublishedItems() {
        return getPublishedItems(getMaxPublishedItems());
    }

    @Override
    public synchronized List<PublishedItem> getPublishedItems(int recentItems) {
        List<PublishedItem> publishedItems = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().getPublishedItems(this, recentItems);
        if (lastPublished != null) {
            // The persistent items may not contain the last item, if it wasn't persisted anymore (e.g. if node configuration changed).
            // Therefore check, if the last item has been persisted.
            boolean persistentItemsContainsLastItem = false;
            for (PublishedItem publishedItem : publishedItems) {
                if (publishedItem.getID().equals(lastPublished.getID())) {
                    persistentItemsContainsLastItem = true;
                    break;
                }
            }
            if (!persistentItemsContainsLastItem) {
                // And if not, include the last item.
                publishedItems.add(0, lastPublished);
                // Recheck the collection size, it might have one more element now (the last item).
                // Remove it, if it exceeds the max items.
                if (publishedItems.size() > recentItems) {
                    publishedItems.remove(publishedItems.size() - 1);
                }
            }
        }
        Log.trace("Got {} published item(s)", publishedItems.size());
        return publishedItems;
    }

    @Override
    public synchronized PublishedItem getLastPublishedItem() {
        if (lastPublished == null){
            lastPublished = XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().getLastPublishedItem(this);
        }

        if (lastPublished == null) {
            Log.trace("Tried to get last published item, but could not find one.");
        } else {
            Log.trace("Got last published item");
        }
        return lastPublished;
    }

    /**
     * Returns true if the last published item is going to be sent to new subscribers.
     *
     * @return true if the last published item is going to be sent to new subscribers.
     */
    @Override
    public boolean isSendItemSubscribe() {
        return sendItemSubscribe;
    }

    void setMaxPayloadSize(int maxPayloadSize) {
        this.maxPayloadSize = maxPayloadSize;
    }

    void setPersistPublishedItems(boolean persistPublishedItems) {
        this.persistPublishedItems = persistPublishedItems;
    }

    void setMaxPublishedItems(int maxPublishedItems) {
        this.maxPublishedItems = maxPublishedItems;
    }

    void setSendItemSubscribe(boolean sendItemSubscribe) {
        this.sendItemSubscribe = sendItemSubscribe;
    }

    /**
     * Purges items that were published to the node. Only owners can request this operation.
     * This operation is only available for nodes configured to store items in the database. All
     * published items will be deleted except the last published item.
     */
    public void purge() {
        Log.trace("Purging items that were published to the node and broadcast purge notification to subscribers.");
        XMPPServer.getInstance().getPubSubModule().getPersistenceProvider().purgeNode(this);
        // Broadcast purge notification to subscribers
        // Build packet to broadcast to subscribers
        Message message = new Message();
        Element event = message.addChildElement("event", "http://jabber.org/protocol/pubsub#event");
        Element items = event.addElement("purge");
        items.addAttribute("node", nodeID);
        // Send notification that the node configuration has changed
        broadcastNodeEvent(message, false);
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException
    {
        int size = super.getCachedSize(); // parent.
        size += CacheSizes.sizeOfBoolean(); // persistPublishedItems
        size += CacheSizes.sizeOfInt(); // maxPublishedItems
        size += CacheSizes.sizeOfInt(); // maxPayloadSize
        size += CacheSizes.sizeOfBoolean(); // sendItemSubscribe
        // The 'lastPublished' field is transient - it is reloaded from the provider on-demand.
        return size;
    }

    @Override
    public void writeExternal( ObjectOutput out ) throws IOException
    {
        super.writeExternal( out );

        final ExternalizableUtil util = ExternalizableUtil.getInstance();
        util.writeBoolean( out, persistPublishedItems );
        util.writeInt( out, maxPublishedItems );
        util.writeInt( out, maxPayloadSize );
        util.writeBoolean( out, sendItemSubscribe );
        // The 'lastPublished' field is transient - it is reloaded from the provider on-demand.
    }

    @Override
    public void readExternal( ObjectInput in ) throws IOException, ClassNotFoundException
    {
        super.readExternal( in );
        final ExternalizableUtil util = ExternalizableUtil.getInstance();
        persistPublishedItems = util.readBoolean( in );
        maxPublishedItems = util.readInt( in );
        maxPayloadSize = util.readInt( in );
        sendItemSubscribe = util.readBoolean( in );
        // The 'lastPublished' field is transient - it is reloaded from the provider on-demand.
        Log = LoggerFactory.getLogger(getClass().getName() + "[" + super.serviceIdentifier + "#" + super.getNodeID() +"]");
    }

    @Override
    protected Logger getLogger() {
        return Log;
    }
}
