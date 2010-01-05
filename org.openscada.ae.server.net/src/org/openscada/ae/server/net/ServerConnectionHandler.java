/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2009 inavare GmbH (http://inavare.com)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.openscada.ae.server.net;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.mina.core.session.IoSession;
import org.openscada.ae.BrowserEntry;
import org.openscada.ae.BrowserListener;
import org.openscada.ae.ConditionStatusInformation;
import org.openscada.ae.Event;
import org.openscada.ae.Query;
import org.openscada.ae.QueryState;
import org.openscada.ae.UnknownQueryException;
import org.openscada.ae.net.BrowserMessageHelper;
import org.openscada.ae.net.ConditionMessageHelper;
import org.openscada.ae.net.EventMessageHelper;
import org.openscada.ae.net.Messages;
import org.openscada.ae.server.ConditionListener;
import org.openscada.ae.server.EventListener;
import org.openscada.ae.server.Service;
import org.openscada.ae.server.Session;
import org.openscada.core.ConnectionInformation;
import org.openscada.core.InvalidSessionException;
import org.openscada.core.UnableToCreateSessionException;
import org.openscada.core.net.MessageHelper;
import org.openscada.core.server.net.AbstractServerConnectionHandler;
import org.openscada.core.subscription.SubscriptionState;
import org.openscada.net.base.MessageListener;
import org.openscada.net.base.data.IntegerValue;
import org.openscada.net.base.data.LongValue;
import org.openscada.net.base.data.MapValue;
import org.openscada.net.base.data.Message;
import org.openscada.net.base.data.StringValue;
import org.openscada.net.base.data.Value;
import org.openscada.net.base.data.VoidValue;
import org.openscada.net.utils.MessageCreator;
import org.openscada.utils.concurrent.NotifyFuture;
import org.openscada.utils.concurrent.ResultHandler;
import org.openscada.utils.concurrent.task.DefaultTaskHandler;
import org.openscada.utils.concurrent.task.ResultFutureHandler;
import org.openscada.utils.concurrent.task.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerConnectionHandler extends AbstractServerConnectionHandler implements BrowserListener
{

    private static final String MESSAGE_QUERY_ID = "queryId";

    public final static String VERSION = "0.1.0";

    private final static Logger logger = LoggerFactory.getLogger ( ServerConnectionHandler.class );

    private Service service = null;

    private Session session = null;

    @SuppressWarnings ( "unused" )
    private final TaskHandler taskHandler = new DefaultTaskHandler ();

    private final Set<Long> taskMap = new HashSet<Long> ();

    @SuppressWarnings ( "unused" )
    private EventListener eventListener;

    @SuppressWarnings ( "unused" )
    private ConditionListener conditionListener;

    private final Map<Long, QueryImpl> queries = new HashMap<Long, QueryImpl> ();

    public ServerConnectionHandler ( final Service service, final IoSession ioSession, final ConnectionInformation connectionInformation )
    {
        super ( ioSession, connectionInformation );

        this.service = service;

        this.messenger.setHandler ( MessageHelper.CC_CREATE_SESSION, new MessageListener () {

            public void messageReceived ( final Message message )
            {
                createSession ( message );
            }
        } );

        this.messenger.setHandler ( MessageHelper.CC_CLOSE_SESSION, new MessageListener () {

            public void messageReceived ( final Message message )
            {
                closeSession ();
            }
        } );

        this.messenger.setHandler ( Messages.CC_SUBSCRIBE_EVENT_POOL, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                subscribeEventPool ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_UNSUBSCRIBE_EVENT_POOL, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                unsubscribeEventPool ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_SUBSCRIBE_CONDITIONS, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                subscribeMonitors ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_UNSUBSCRIBE_CONDITIONS, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                unsubscribeMonitors ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_CONDITION_AKN, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                acknowledge ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_QUERY_CREATE, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                ServerConnectionHandler.this.queryCreate ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_QUERY_CLOSE, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                ServerConnectionHandler.this.queryClose ( message );
            }
        } );

        this.messenger.setHandler ( Messages.CC_QUERY_LOAD_MORE, new MessageListener () {

            public void messageReceived ( final Message message ) throws Exception
            {
                ServerConnectionHandler.this.queryLoadMore ( message );
            }
        } );
    }

    /**
     * Extract the query id from the message
     * @param message the message
     * @return the extracted query id or <code>null</code> if there was none
     */
    private Long queryIdFromMessage ( final Message message )
    {
        Long queryId = null;
        {
            final Value value = message.getValues ().get ( MESSAGE_QUERY_ID );
            if ( value instanceof LongValue )
            {
                queryId = ( (LongValue)value ).getValue ();
            }
        }
        return queryId;
    }

    protected void queryCreate ( final Message message )
    {
        final Long queryId = queryIdFromMessage ( message );
        if ( queryId == null )
        {
            logger.warn ( "Unable to create query without query id" );
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "Unable to create query without query id" ) );
            return;
        }

        String queryType = null;
        {
            final Value value = message.getValues ().get ( "queryType" );
            if ( value instanceof StringValue )
            {
                queryType = ( (StringValue)value ).getValue ();
            }
        }

        String queryData = null;
        {
            final Value value = message.getValues ().get ( "queryData" );
            if ( value instanceof StringValue )
            {
                queryData = ( (StringValue)value ).getValue ();
            }
        }

        if ( queryType == null || queryData == null )
        {
            final String msg = "Query without queryType and queryData is not allowed";
            logger.warn ( msg );
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, msg ) );
            return;
        }

        synchronized ( this )
        {
            if ( this.queries.containsKey ( queryId ) )
            {
                final String msg = String.format ( "A query with id {} already exisits", queryId );
                logger.warn ( msg );
                this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, msg ) );
                return;
            }

            // create new
            final QueryImpl query = new QueryImpl ( queryId, this );
            try
            {
                final Query queryHandle = this.service.createQuery ( this.session, queryType, queryData, query );
                query.setQuery ( queryHandle );
                this.queries.put ( queryId, query );
            }
            catch ( final InvalidSessionException e )
            {
                final String msg = "Query without queryType and queryData is not allowed";
                logger.warn ( msg );
                this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, msg ) );
            }
        }
    }

    protected synchronized void queryClose ( final Message message )
    {
        final Long queryId = queryIdFromMessage ( message );
        if ( queryId == null )
        {
            logger.warn ( "Unable to create query without query id" );
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "Unable to create query without query id" ) );
            return;
        }

        final QueryImpl query = this.queries.get ( queryId );
        if ( query == null )
        {
            final String msg = String.format ( "No query with id {} exisits", queryId );
            logger.warn ( msg );
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, msg ) );
            return;

        }

        query.close ();
    }

    protected synchronized void queryLoadMore ( final Message message )
    {
        final Long queryId = queryIdFromMessage ( message );
        if ( queryId == null )
        {
            logger.warn ( "Unable to create query without query id" );
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "Unable to create query without query id" ) );
            return;
        }

        final QueryImpl query = this.queries.get ( queryId );
        if ( query == null )
        {
            final String msg = String.format ( "No query with id {} exisits", queryId );
            logger.warn ( msg );
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, msg ) );
            return;
        }

        Integer count = 100;
        {
            final Value value = message.getValues ().get ( "count" );
            if ( value instanceof IntegerValue )
            {
                count = ( (IntegerValue)value ).getValue ();
            }
        }

        query.loadMore ( count );
    }

    protected void acknowledge ( final Message message )
    {
        String monitorId = null;
        Date aknTimestamp = null;

        {
            final Value value = message.getValues ().get ( "id" );
            if ( value instanceof StringValue )
            {
                monitorId = value.toString ();
            }
        }
        {
            final Value value = message.getValues ().get ( "aknTimestamp" );
            if ( value instanceof LongValue )
            {
                aknTimestamp = new Date ( ( (LongValue)value ).getValue () );
            }
        }

        if ( monitorId != null && aknTimestamp != null )
        {
            try
            {
                this.service.acknowledge ( this.session, monitorId, aknTimestamp );
            }
            catch ( final Throwable e )
            {
                this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, e ) );
            }
        }
    }

    protected void subscribeEventPool ( final Message message )
    {
        final Value value = message.getValues ().get ( MESSAGE_QUERY_ID );

        String queryId = null;
        if ( value instanceof StringValue )
        {
            queryId = ( (StringValue)value ).getValue ();
        }

        try
        {
            this.service.subscribeEventQuery ( this.session, queryId );
            MessageCreator.createACK ( message );
        }
        catch ( final InvalidSessionException e )
        {
            closeSession ();
            MessageCreator.createFailedMessage ( message, e );
        }
        catch ( final UnknownQueryException e )
        {
            MessageCreator.createFailedMessage ( message, e );
        }
    }

    protected void unsubscribeEventPool ( final Message message )
    {
        final Value value = message.getValues ().get ( MESSAGE_QUERY_ID );

        String queryId = null;
        if ( value instanceof StringValue )
        {
            queryId = ( (StringValue)value ).getValue ();
        }

        try
        {
            this.service.unsubscribeEventQuery ( this.session, queryId );
            MessageCreator.createACK ( message );
        }
        catch ( final InvalidSessionException e )
        {
            closeSession ();
            MessageCreator.createFailedMessage ( message, e );
        }
    }

    protected void subscribeMonitors ( final Message message )
    {
        final Value value = message.getValues ().get ( MESSAGE_QUERY_ID );

        String queryId = null;
        if ( value instanceof StringValue )
        {
            queryId = ( (StringValue)value ).getValue ();
        }

        try
        {
            this.service.subscribeConditionQuery ( this.session, queryId );
            MessageCreator.createACK ( message );
        }
        catch ( final InvalidSessionException e )
        {
            closeSession ();
            MessageCreator.createFailedMessage ( message, e );
        }
        catch ( final UnknownQueryException e )
        {
            MessageCreator.createFailedMessage ( message, e );
        }
    }

    protected void unsubscribeMonitors ( final Message message )
    {
        final Value value = message.getValues ().get ( MESSAGE_QUERY_ID );

        String queryId = null;
        if ( value instanceof StringValue )
        {
            queryId = ( (StringValue)value ).getValue ();
        }

        try
        {
            this.service.unsubscribeConditionQuery ( this.session, queryId );
            MessageCreator.createACK ( message );
        }
        catch ( final InvalidSessionException e )
        {
            closeSession ();
            MessageCreator.createFailedMessage ( message, e );
        }
    }

    private void createSession ( final Message message )
    {
        // if session exists this is an error
        if ( this.session != null )
        {
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "Connection already bound to a session" ) );
            return;
        }

        // get the session properties
        final Properties props = new Properties ();
        final Value propertiesValue = message.getValues ().get ( "properties" );
        if ( propertiesValue instanceof MapValue )
        {
            final MapValue properties = (MapValue)propertiesValue;
            for ( final Map.Entry<String, Value> entry : properties.getValues ().entrySet () )
            {
                props.put ( entry.getKey (), entry.getValue ().toString () );
            }
        }

        // now check client version
        final String clientVersion = props.getProperty ( "client-version", "" );
        if ( clientVersion.equals ( "" ) )
        {
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "client does not pass \"client-version\" property! You may need to upgrade your client!" ) );
            return;
        }
        // client version does not match server version
        if ( !clientVersion.equals ( VERSION ) )
        {
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "protocol version mismatch: client '" + clientVersion + "' server: '" + VERSION + "'" ) );
            return;
        }

        try
        {
            this.session = (Session)this.service.createSession ( props );
        }
        catch ( final UnableToCreateSessionException e )
        {
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, e.getReason () ) );
            return;
        }

        // unknown reason why we did not get a session
        if ( this.session == null )
        {
            this.messenger.sendMessage ( MessageCreator.createFailedMessage ( message, "unable to create session" ) );
            return;
        }

        // hook up session
        this.session.setEventListener ( this.eventListener = new EventListener () {

            public void dataChanged ( final String poolId, final Event[] addedEvents )
            {
                ServerConnectionHandler.this.dataChangedEvents ( poolId, addedEvents );
            }

            public void updateStatus ( final Object topic, final SubscriptionState state )
            {
                ServerConnectionHandler.this.statusChangedEvents ( topic.toString (), state );
            }
        } );
        this.session.setConditionListener ( this.conditionListener = new ConditionListener () {

            public void dataChanged ( final String subscriptionId, final ConditionStatusInformation[] addedOrUpdated, final String[] removed )
            {
                dataChangedConditions ( subscriptionId, addedOrUpdated, removed );
            }

            public void updateStatus ( final Object topic, final SubscriptionState state )
            {
                statusChangedConditions ( topic.toString (), state );
            }
        } );
        this.session.setBrowserListener ( this );

        // send success
        this.messenger.sendMessage ( MessageCreator.createACK ( message ) );
    }

    @Override
    protected void cleanUp ()
    {
        super.cleanUp ();
        disposeSession ();
    }

    private void disposeSession ()
    {
        // if session does not exists, silently ignore it
        if ( this.session != null )
        {
            final Session session = this.session;
            this.session = null;
            try
            {
                session.setConditionListener ( null );
                session.setEventListener ( null );
                session.setBrowserListener ( null );
                this.service.closeSession ( session );
            }
            catch ( final InvalidSessionException e )
            {
                logger.warn ( "Failed to close session", e );
            }
        }
    }

    private void closeSession ()
    {
        cleanUp ();
    }

    @SuppressWarnings ( "unused" )
    private <T> void scheduleTask ( final NotifyFuture<T> task, final long id, final ResultHandler<T> resultHandler )
    {
        task.addListener ( new ResultFutureHandler<T> ( resultHandler ) );
    }

    @SuppressWarnings ( "unused" )
    private void removeTask ( final long id )
    {
        synchronized ( this.taskMap )
        {
            this.taskMap.remove ( id );
        }
    }

    public void dataChangedEvents ( final String poolId, final Event[] addedEvents )
    {
        final Message message = new Message ( Messages.CC_EVENT_POOL_DATA );

        message.getValues ().put ( MESSAGE_QUERY_ID, new StringValue ( poolId ) );
        message.getValues ().put ( "events", EventMessageHelper.toValue ( addedEvents ) );

        this.messenger.sendMessage ( message );
    }

    public void statusChangedEvents ( final String poolId, final SubscriptionState status )
    {
        final Message message = new Message ( Messages.CC_EVENT_POOL_STATUS );

        message.getValues ().put ( MESSAGE_QUERY_ID, new StringValue ( poolId ) );
        message.getValues ().put ( "status", new StringValue ( status.toString () ) );

        this.messenger.sendMessage ( message );
    }

    public void dataChangedConditions ( final String subscriptionId, final ConditionStatusInformation[] addedOrUpdated, final String[] removed )
    {
        final Message message = new Message ( Messages.CC_CONDITIONS_DATA );

        message.getValues ().put ( MESSAGE_QUERY_ID, new StringValue ( subscriptionId ) );
        message.getValues ().put ( "conditions.addedOrUpdated", ConditionMessageHelper.toValue ( addedOrUpdated ) );
        message.getValues ().put ( "conditions.removed", ConditionMessageHelper.toValue ( removed ) );

        this.messenger.sendMessage ( message );
    }

    public void statusChangedConditions ( final String subscriptionId, final SubscriptionState status )
    {
        final Message message = new Message ( Messages.CC_CONDITIONS_STATUS );

        message.getValues ().put ( MESSAGE_QUERY_ID, new StringValue ( subscriptionId ) );
        message.getValues ().put ( "status", new StringValue ( status.toString () ) );

        this.messenger.sendMessage ( message );
    }

    public void dataChanged ( final BrowserEntry[] addedOrUpdated, final String[] removed, final boolean full )
    {
        final Message message = new Message ( Messages.CC_BROWSER_UPDATE );

        message.getValues ().put ( "added", BrowserMessageHelper.toValue ( addedOrUpdated ) );
        message.getValues ().put ( "removed", BrowserMessageHelper.toValue ( removed ) );
        if ( full )
        {
            message.getValues ().put ( "full", VoidValue.INSTANCE );
        }

        this.messenger.sendMessage ( message );
    }

    public void sendQueryData ( final QueryImpl queryImpl, final Event[] events )
    {
        // TODO: check if query is still active
        final Message message = new Message ( Messages.CC_QUERY_DATA );
        message.getValues ().put ( "data", EventMessageHelper.toValue ( events ) );
        message.getValues ().put ( MESSAGE_QUERY_ID, new LongValue ( queryImpl.getQueryId () ) );
        this.messenger.sendMessage ( message );
    }

    public void sendQueryState ( final QueryImpl queryImpl, final QueryState state )
    {
        synchronized ( this )
        {
            // remove query if necessary
            if ( state == QueryState.DISCONNECTED )
            {
                this.queries.remove ( queryImpl.getQueryId () );
            }
        }

        final Message message = new Message ( Messages.CC_QUERY_STATUS_CHANGED );
        message.getValues ().put ( "state", new StringValue ( state.name () ) );
        message.getValues ().put ( MESSAGE_QUERY_ID, new LongValue ( queryImpl.getQueryId () ) );
        this.messenger.sendMessage ( message );
    }

}