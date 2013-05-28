/*
 * This file is part of the OpenSCADA project
 * 
 * Copyright (C) 2006-2012 TH4 SYSTEMS GmbH (http://th4-systems.com)
 * Copyright (C) 2013 Jens Reimann (ctron@dentrassi.de)
 *
 * OpenSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * OpenSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with OpenSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.ae.server.akn.proxy;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

import org.openscada.ae.connection.provider.ConnectionService;
import org.openscada.ae.server.common.akn.AknHandler;
import org.openscada.ca.ConfigurationDataHelper;
import org.openscada.ca.ConfigurationFactory;
import org.openscada.core.connection.provider.ConnectionIdTracker;
import org.openscada.core.server.OperationParameters;
import org.openscada.core.server.OperationParametersHelper;
import org.openscada.sec.UserInformation;
import org.openscada.sec.callback.CallbackHandler;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyAknService implements AknHandler, ConfigurationFactory
{

    private final static Logger logger = LoggerFactory.getLogger ( ProxyAknService.class );

    public static final String FACTORY_ID = "org.openscada.ae.server.akn.proxy";

    private static final class EntryPriorityComparator implements Comparator<Entry>
    {
        public static final EntryPriorityComparator INSTANCE = new EntryPriorityComparator ();

        @Override
        public int compare ( final Entry o1, final Entry o2 )
        {
            return Integer.valueOf ( o1.priority ).compareTo ( o2.priority );
        }
    }

    private static class Entry
    {
        private final Pattern pattern;

        private final boolean authorative;

        private final String connectionId;

        private final String id;

        private final int priority;

        public Entry ( final String id, final int priority, final Pattern pattern, final boolean authorative, final String connectionId )
        {
            this.id = id;
            this.priority = priority;
            this.pattern = pattern;
            this.authorative = authorative;
            this.connectionId = connectionId;
        }

        @Override
        public int hashCode ()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ( this.id == null ? 0 : this.id.hashCode () );
            return result;
        }

        @Override
        public boolean equals ( final Object obj )
        {
            if ( this == obj )
            {
                return true;
            }
            if ( obj == null )
            {
                return false;
            }
            if ( getClass () != obj.getClass () )
            {
                return false;
            }
            final Entry other = (Entry)obj;
            if ( this.id == null )
            {
                if ( other.id != null )
                {
                    return false;
                }
            }
            else if ( !this.id.equals ( other.id ) )
            {
                return false;
            }
            return true;
        }

        @Override
        public String toString ()
        {
            return String.format ( "[%s: %s, %s, %s -> %s]", this.id, this.pattern, this.authorative, this.priority, this.connectionId );
        }
    }

    private final List<Entry> entries = new LinkedList<Entry> ();

    private final BundleContext context;

    private final Lock readLock;

    private final Lock writeLock;

    public ProxyAknService ( final BundleContext context )
    {
        this.context = context;

        final ReentrantReadWriteLock rw = new ReentrantReadWriteLock ();
        this.readLock = rw.readLock ();
        this.writeLock = rw.writeLock ();
    }

    @Override
    public void update ( final UserInformation userInformation, final String configurationId, final Map<String, String> parameters ) throws Exception
    {
        final ConfigurationDataHelper cfg = new ConfigurationDataHelper ( parameters );

        // first parse
        final Entry entry = new Entry ( configurationId, cfg.getIntegerChecked ( "priority", "'priority' must be set" ), Pattern.compile ( cfg.getStringNonEmpty ( "pattern" ) ), cfg.getBoolean ( "authorative", true ), cfg.getStringNonEmpty ( "connection.id" ) );

        // now modify
        this.writeLock.lock ();

        try
        {
            delete ( userInformation, configurationId );
            this.entries.add ( entry );
            Collections.sort ( this.entries, EntryPriorityComparator.INSTANCE );

            logger.info ( "{} entries in processing table", this.entries.size () );
        }
        finally
        {
            this.writeLock.unlock ();
        }
    }

    @Override
    public void delete ( final UserInformation userInformation, final String configurationId ) throws Exception
    {
        this.writeLock.lock ();

        try
        {
            final Iterator<Entry> i = this.entries.iterator ();
            while ( i.hasNext () )
            {
                if ( i.next ().id.equals ( configurationId ) )
                {
                    i.remove ();
                }
            }
        }
        finally
        {
            this.writeLock.unlock ();
        }
    }

    @Override
    public boolean acknowledge ( final String monitorId, final OperationParameters operationParameters, final Date aknTimestamp )
    {
        logger.info ( "acknowledge - monitorId: {}, operationParameters: {}, aknTimestamp: {}", new Object[] { monitorId, operationParameters, aknTimestamp } );

        int matches = 0;

        this.readLock.lock ();
        try
        {
            for ( final Entry entry : this.entries )
            {
                logger.debug ( "Checking entry: {}", entry );
                if ( entry.pattern.matcher ( monitorId ).matches () )
                {
                    matches++;
                    akn ( entry.connectionId, monitorId, operationParameters, aknTimestamp );
                    if ( entry.authorative )
                    {
                        logger.debug ( "Entry is authorative" );
                        break;
                    }
                }
            }

            return matches > 0;
        }
        finally
        {
            this.readLock.unlock ();
        }
    }

    private void akn ( final String connectionId, final String monitorId, final OperationParameters operationParameters, final Date aknTimestamp )
    {
        logger.info ( "passing on acknowledge - connectionid: {}, monitorId: {}, operationParameters: {}, aknTimestamp: {}", new Object[] { connectionId, monitorId, operationParameters, aknTimestamp } );

        final ConnectionIdTracker tracker = new ConnectionIdTracker ( this.context, connectionId, null, ConnectionService.class );
        tracker.open ();
        try
        {
            final ConnectionService connection = (ConnectionService)tracker.waitForService ( 1000 );
            connection.getConnection ().acknowledge ( monitorId, aknTimestamp, OperationParametersHelper.toData ( operationParameters ), getHandler ( operationParameters ) );
        }
        catch ( final InterruptedException e )
        {
            logger.warn ( "Failed to wait for connection: " + connectionId, e );
        }
        finally
        {
            tracker.close ();
        }
    }

    private CallbackHandler getHandler ( final OperationParameters operationParameters )
    {
        return operationParameters == null ? null : operationParameters.getCallbackHandler ();
    }
}
