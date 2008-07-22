/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006-2007 inavare GmbH (http://inavare.com)
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

package org.openscada.da.server.ice.impl;

import java.util.Collection;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openscada.core.Variant;
import org.openscada.core.ice.AttributesHelper;
import org.openscada.core.ice.VariantHelper;
import org.openscada.core.subscription.SubscriptionState;
import org.openscada.da.core.Location;
import org.openscada.da.core.browser.Entry;
import org.openscada.da.core.server.ItemChangeListener;
import org.openscada.da.core.server.Session;
import org.openscada.da.core.server.browser.FolderListener;
import org.openscada.da.ice.BrowserEntryHelper;

import Ice.Current;
import Ice.Identity;
import OpenSCADA.Core.InvalidSessionException;
import OpenSCADA.DA.DataCallbackPrx;
import OpenSCADA.DA.DataCallbackPrxHelper;
import OpenSCADA.DA._SessionDisp;
import OpenSCADA.DA.Browser.FolderCallbackPrx;
import OpenSCADA.DA.Browser.FolderCallbackPrxHelper;

public class SessionImpl extends _SessionDisp implements ItemChangeListener, FolderListener
{
    private static Logger _log = Logger.getLogger ( SessionImpl.class );
    
    private HiveImpl _hive;
    private Session _session;
    private DataCallbackPrx _dataCallback = null;
    private FolderCallbackPrx _folderCallback = null;
    
    public SessionImpl ( HiveImpl hive, Session session )
    {
        super ();
        _hive = hive;
        _session = session;
        _session.setListener ( (ItemChangeListener)this );
        _session.setListener ( (FolderListener)this );
        
        System.gc ();
    }
    
    @Override
    protected void finalize () throws Throwable
    {
        _log.debug ( "Session finalized" );
        super.finalize ();
    }
    
    public void setDataCallback ( Identity ident, Current __current )
    {
        _dataCallback = DataCallbackPrxHelper.uncheckedCast ( __current.con.createProxy ( ident ).ice_oneway () );
    }
    
    public void unsetDataCallback ( Current __current )
    {
        _dataCallback = null;
    }
    
    public void setFolderCallback ( Identity ident, Current __current )
    {
        _folderCallback = FolderCallbackPrxHelper.uncheckedCast ( __current.con.createProxy ( ident ).ice_oneway () );
    }

    public void unsetFolderCallback ( Current __current )
    {
        _folderCallback = null;
    }

    public Session getSession ()
    {
        return _session;
    }

    @SuppressWarnings("unchecked")
    public void dataChanged ( String itemId, Variant value, Map<String, Variant> attributes, boolean cache )
    {
        _log.debug ( String.format ( "Data changed for '%s'", itemId ) );

        DataCallbackPrx dataCallback;
        
        if ( (dataCallback = _dataCallback) != null )
        {
            dataCallback.dataChange ( itemId, VariantHelper.toIce ( value ), AttributesHelper.toIce ( attributes ), cache );
        }
    }

    public void handleListenerError ()
    {
        _log.info ( "handleListenerError" );
        destroy ();
    }
    
    public synchronized void destroy ()
    {
        _log.debug ( "destroy session" );
        
        if ( _session == null )
        {
            return;
        }
        
        _dataCallback = null;
        _folderCallback = null;
        _session.setListener ( (ItemChangeListener)null );
        _session.setListener ( (FolderListener)null );
        
        try
        {
            _hive.closeSession ( this );
        }
        catch ( InvalidSessionException e )
        {
            // we don't care
        }

        _session = null;
        _hive = null;
    }

    public void folderChanged ( Location location, Collection<Entry> added, Collection<String> removed, boolean full )
    {
       _log.debug ( String.format ( "Folder changed: %s", location.toString () ) );
       
       if ( _folderCallback == null )
       {
           _log.debug ( "Folder changed but no listener subscribed" );
           return;
       }

       FolderCallbackPrx folderCallback;
       
       if ( (folderCallback = _folderCallback) != null )
       {
           folderCallback.folderChanged ( location.asArray (), BrowserEntryHelper.toIce ( added.toArray ( new Entry[0] ) ), removed.toArray ( new String[0] ), full );
       }
    }

    public void ping ()
    {
        try
        {
            DataCallbackPrx dataCallback = _dataCallback;
            if ( dataCallback != null )
            {
                dataCallback = DataCallbackPrxHelper.uncheckedCast ( dataCallback.ice_twoway () );
                dataCallback.ice_ping ();
            }
            
            FolderCallbackPrx folderCallback = _folderCallback;
            if ( folderCallback != null )
            {
                folderCallback = FolderCallbackPrxHelper.uncheckedCast ( folderCallback.ice_twoway () );
                folderCallback.ice_ping ();
            }
        }
        catch ( Throwable e )
        {
            _log.debug ( "Ping failed", e );
            handleListenerError ();
        }
    }

    public void subscriptionChanged ( String item, SubscriptionState subscriptionState )
    {
        _log.debug ( String.format ( "Subscription changed: '%s' - '%s'", item, subscriptionState.name () ) );
        
        DataCallbackPrx dataCallback;
        
        if ( (dataCallback = _dataCallback) != null )
        {
            OpenSCADA.DA.SubscriptionState ss = OpenSCADA.DA.SubscriptionState.DISCONNECTED;
            
            switch ( subscriptionState )
            {
            case CONNECTED:
                ss = OpenSCADA.DA.SubscriptionState.CONNECTED;
                break;
            case DISCONNECTED:
                ss = OpenSCADA.DA.SubscriptionState.DISCONNECTED;
                break;
            case GRANTED:
                ss = OpenSCADA.DA.SubscriptionState.GRANTED;
                break;
            }
            
            dataCallback.subscriptionChange ( item, ss );
        }
    }
}
