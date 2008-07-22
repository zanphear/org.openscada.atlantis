/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006 inavare GmbH (http://inavare.com)
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

package org.openscada.net.da.handler;

import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;

import org.openscada.core.Variant;
import org.openscada.core.subscription.SubscriptionState;
import org.openscada.da.core.IODirection;
import org.openscada.net.base.data.IntegerValue;
import org.openscada.net.base.data.ListValue;
import org.openscada.net.base.data.MapValue;
import org.openscada.net.base.data.Message;
import org.openscada.net.base.data.StringValue;
import org.openscada.net.base.data.Value;
import org.openscada.net.base.data.VoidValue;
import org.openscada.utils.lang.Holder;

public class Messages extends org.openscada.core.net.MessageHelper
{
    public final static int CC_CREATE_SESSION = 0x00010001;

    public final static int CC_CLOSE_SESSION = 0x00010002;

    public final static int CC_SUBSCRIBE_ITEM = 0x00010010;

    public final static int CC_UNSUBSCRIBE_ITEM = 0x00010011;

    public final static int CC_NOTIFY_DATA = 0x00010020;

    // public final static int CC_NOTIFY_ATTRIBUTES = 0x00010021; // unsupported
    public static final int CC_SUBSCRIPTION_CHANGE = 0x00010022;

    public final static int CC_WRITE_OPERATION = 0x00010030;

    public final static int CC_WRITE_OPERATION_RESULT = 0x00010031;

    public final static int CC_READ_OPERATION = 0x00010040;

    public final static int CC_WRITE_ATTRIBUTES_OPERATION = 0x00010050;

    public final static int CC_WRITE_ATTRIBUTES_OPERATION_RESULT = 0x00010040;

    public final static int CC_BROWSER_LIST_REQ = 0x00010200;

    public final static int CC_BROWSER_LIST_RES = 0x00010201;

    public final static int CC_BROWSER_EVENT = 0x00010210;

    public final static int CC_BROWSER_SUBSCRIBE = 0x00010211;

    public final static int CC_BROWSER_UNSUBSCRIBE = 0x00010212;

    public static Message createSession ( Properties props )
    {
        Message msg = new Message ( CC_CREATE_SESSION );

        for ( Map.Entry<Object, Object> entry : props.entrySet () )
        {
            msg.getValues ().put ( entry.getKey ().toString (), new StringValue ( entry.getValue ().toString () ) );
        }

        return msg;
    }

    public static Message closeSession ()
    {
        return new Message ( CC_CLOSE_SESSION );
    }

    public static Message subscribeItem ( String itemName )
    {
        Message msg = new Message ( CC_SUBSCRIBE_ITEM );
        msg.getValues ().put ( "item-id", new StringValue ( itemName ) );
        return msg;
    }

    public static Message unsubscribeItem ( String itemName )
    {
        Message msg = new Message ( CC_UNSUBSCRIBE_ITEM );
        msg.getValues ().put ( "item-id", new StringValue ( itemName ) );
        return msg;
    }

    public static Message notifyData ( String itemName, Variant value, Map<String, Variant> attributes, boolean cache )
    {
        Message msg = new Message ( CC_NOTIFY_DATA );

        msg.getValues ().put ( "item-id", new StringValue ( itemName ) );

        // flag if initial bit is set
        if ( cache )
        {
            msg.getValues ().put ( "cache-read", new VoidValue () );
        }

        // encode message
        Value messageValue = variantToValue ( value );
        if ( messageValue != null )
        {
            msg.getValues ().put ( "value", messageValue );
        }

        // encode attributes
        ListValue unsetEntries = new ListValue ();
        MapValue setEntries = new MapValue ();

        if ( attributes != null )
        {
            for ( Map.Entry<String, Variant> entry : attributes.entrySet () )
            {
                Value valueEntry = variantToValue ( entry.getValue () );
                if ( valueEntry == null )
                {
                    unsetEntries.add ( new StringValue ( entry.getKey () ) );
                }
                else
                {
                    setEntries.put ( entry.getKey (), valueEntry );
                }
            }
        }

        msg.getValues ().put ( "attributes-unset", unsetEntries );
        msg.getValues ().put ( "attributes-set", setEntries );

        return msg;
    }

    public static int encodeIO ( EnumSet<IODirection> io )
    {
        int bits = 0;
        if ( io.contains ( IODirection.INPUT ) )
        {
            bits |= 1;
        }
        if ( io.contains ( IODirection.OUTPUT ) )
        {
            bits |= 2;
        }

        return bits;
    }

    public static EnumSet<IODirection> decodeIO ( int bits )
    {
        EnumSet<IODirection> ioDirection = EnumSet.noneOf ( IODirection.class );

        if ( ( bits & 1 ) > 0 )
        {
            ioDirection.add ( IODirection.INPUT );
        }
        if ( ( bits & 2 ) > 0 )
        {
            ioDirection.add ( IODirection.OUTPUT );
        }

        return ioDirection;
    }

    public static Message notifySubscriptionChange ( String item, SubscriptionState subscriptionState )
    {
        Message msg = new Message ( CC_SUBSCRIPTION_CHANGE );
        msg.getValues ().put ( "item-id", new StringValue ( item ) );
        switch ( subscriptionState )
        {
        case DISCONNECTED:
            msg.getValues ().put ( "state", new IntegerValue ( 0 ) );
            break;
        case GRANTED:
            msg.getValues ().put ( "state", new IntegerValue ( 1 ) );
            break;
        case CONNECTED:
            msg.getValues ().put ( "state", new IntegerValue ( 2 ) );
            break;
        }
        return msg;
    }

    public static void parseSubscriptionChange ( Message msg, Holder<String> item, Holder<SubscriptionState> subscriptionState )
    {
        if ( msg.getValues ().containsKey ( "item-id" ) )
        {
            item.value = msg.getValues ().get ( "item-id" ).toString ();
        }

        if ( msg.getValues ().containsKey ( "state" ) )
        {
            if ( msg.getValues ().get ( "state" ) instanceof IntegerValue )
            {
                switch ( ( (IntegerValue)msg.getValues ().get ( "state" ) ).getValue () )
                {
                case 0:
                    subscriptionState.value = SubscriptionState.DISCONNECTED;
                    break;
                case 1:
                    subscriptionState.value = SubscriptionState.GRANTED;
                    break;
                case 2:
                    subscriptionState.value = SubscriptionState.CONNECTED;
                    break;
                }
            }
        }
    }
}
