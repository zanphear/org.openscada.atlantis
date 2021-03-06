/*
 * This file is part of the openSCADA project
 * 
 * Copyright (C) 2006-2010 TH4 SYSTEMS GmbH (http://th4-systems.com)
 *
 * openSCADA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 3
 * only, as published by the Free Software Foundation.
 *
 * openSCADA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License version 3 for more details
 * (a copy is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * version 3 along with openSCADA. If not, see
 * <http://opensource.org/licenses/lgpl-3.0.html> for a copy of the LGPLv3 License.
 */

package org.openscada.da.server.modbus.io;

import java.util.BitSet;

import org.apache.mina.core.buffer.IoBuffer;
import org.openscada.da.server.modbus.ModbusConstants;
import org.openscada.da.server.modbus.ModbusType;
import org.openscada.da.server.modbus.io.message.request.AddressableRequestMessage;
import org.openscada.da.server.modbus.io.message.request.RequestMessage;
import org.openscada.da.server.modbus.io.message.request.WriteMultipleRegistersRequest;
import org.openscada.da.server.modbus.io.message.request.WriteSingleCoilRequest;
import org.openscada.da.server.modbus.io.message.response.ErrorResponse;
import org.openscada.da.server.modbus.io.message.response.ReadCoilsResponse;
import org.openscada.da.server.modbus.io.message.response.ReadDiscreteInputsResponse;
import org.openscada.da.server.modbus.io.message.response.ReadHoldingRegistersResponse;
import org.openscada.da.server.modbus.io.message.response.ReadInputRegistersResponse;
import org.openscada.da.server.modbus.io.message.response.ResponseMessage;
import org.openscada.da.server.modbus.io.message.response.WriteMultipleRegistersResponse;
import org.openscada.da.server.modbus.io.message.response.WriteSingleCoilResponse;

public class ModbusHelper
{
    public static IoBuffer encodeRequest ( final RequestMessage message ) throws ModbusProtocolError
    {
        if ( message instanceof AddressableRequestMessage )
        {
            final AddressableRequestMessage addressableMessage = (AddressableRequestMessage)message;
            final IoBuffer buffer = IoBuffer.allocate ( 14 ); // maximum size of WriteMultipleRegistersRequest
            buffer.put ( addressableMessage.getFunctionCode () );
            buffer.putShort ( (short) ( addressableMessage.getStartAddress () + addressableMessage.getOffset () ) );
            if ( message instanceof WriteSingleCoilRequest )
            {
                final WriteSingleCoilRequest request = (WriteSingleCoilRequest)message;
                try
                {
                    buffer.put ( ModbusType.BOOLEAN.convertVariant ( request.getValue () ) );
                }
                catch ( final Exception e )
                {
                    throw new ModbusProtocolError ( e );
                }
            }
            else if ( message instanceof WriteMultipleRegistersRequest )
            {
                final WriteMultipleRegistersRequest request = (WriteMultipleRegistersRequest)message;
                try
                {
                    buffer.putShort ( (short)request.getType ().getSize () ); // quantity of registers
                    buffer.put ( (byte) ( request.getType ().getSize () * 2 ) ); // number of following bytes
                    buffer.put ( request.getType ().convertVariant ( request.getValue () ) );
                }
                catch ( final Exception e )
                {
                    throw new ModbusProtocolError ( e );
                }
            }
            else
            {
                buffer.putShort ( (short)addressableMessage.getQuantity () );
            }
            buffer.flip ();
            return buffer;
        }
        throw new ModbusProtocolError ( "message type unknown" );
    }

    public static ResponseMessage decodeResponse ( final RequestMessage originalRequest, final IoBuffer buffer ) throws ModbusProtocolError
    {
        if ( buffer == null || buffer.limit () == 0 )
        {
            throw new ModbusProtocolError ( "message is empty" );
        }
        final byte functionCode = buffer.get ();
        try
        {
            if ( originalRequest instanceof AddressableRequestMessage )
            {
                final AddressableRequestMessage addressableRequestMessage = (AddressableRequestMessage)originalRequest;
                switch ( functionCode )
                {
                // read responses
                    case ModbusConstants.FUNCTION_CODE_READ_DISCRETE_INPUTS:
                        return new ReadDiscreteInputsResponse ( addressableRequestMessage.getStartAddress (), readBits ( buffer, addressableRequestMessage.getQuantity () ) );
                    case ModbusConstants.FUNCTION_CODE_READ_DISCRETE_INPUTS + ModbusConstants.FUNCTION_CODE_ERROR_BASE:
                        return new ErrorResponse ( (byte) ( ModbusConstants.FUNCTION_CODE_READ_DISCRETE_INPUTS + ModbusConstants.FUNCTION_CODE_ERROR_BASE ), buffer.get () );
                    case ModbusConstants.FUNCTION_CODE_READ_COILS:
                        return new ReadCoilsResponse ( addressableRequestMessage.getStartAddress (), readBits ( buffer, addressableRequestMessage.getQuantity () ) );
                    case ModbusConstants.FUNCTION_CODE_READ_COILS + ModbusConstants.FUNCTION_CODE_ERROR_BASE:
                        return new ErrorResponse ( (byte) ( ModbusConstants.FUNCTION_CODE_READ_COILS + ModbusConstants.FUNCTION_CODE_ERROR_BASE ), buffer.get () );
                    case ModbusConstants.FUNCTION_CODE_READ_INPUT_REGISTERS:
                        return new ReadInputRegistersResponse ( addressableRequestMessage.getStartAddress (), readBytes ( buffer, addressableRequestMessage.getQuantity () ) );
                    case ModbusConstants.FUNCTION_CODE_READ_INPUT_REGISTERS + ModbusConstants.FUNCTION_CODE_ERROR_BASE:
                        return new ErrorResponse ( (byte) ( ModbusConstants.FUNCTION_CODE_READ_INPUT_REGISTERS + ModbusConstants.FUNCTION_CODE_ERROR_BASE ), buffer.get () );
                    case ModbusConstants.FUNCTION_CODE_READ_HOLDING_REGISTERS:
                        return new ReadHoldingRegistersResponse ( addressableRequestMessage.getStartAddress (), readBytes ( buffer, addressableRequestMessage.getQuantity () ) );
                    case ModbusConstants.FUNCTION_CODE_READ_HOLDING_REGISTERS + ModbusConstants.FUNCTION_CODE_ERROR_BASE:
                        return new ErrorResponse ( (byte) ( ModbusConstants.FUNCTION_CODE_READ_HOLDING_REGISTERS + ModbusConstants.FUNCTION_CODE_ERROR_BASE ), buffer.get () );
                        // write responses
                    case ModbusConstants.FUNCTION_CODE_WRITE_SINGLE_COIL:
                        return new WriteSingleCoilResponse ( addressableRequestMessage.getStartAddress () );
                    case ModbusConstants.FUNCTION_CODE_WRITE_MULTIPLE_REGISTERS:
                        return new WriteMultipleRegistersResponse ( addressableRequestMessage.getStartAddress () );
                }
            }
        }
        catch ( final Exception e )
        {
            throw new ModbusProtocolError ( e );
        }
        throw new ModbusProtocolError ( "functionCode unknown" );
    }

    private static byte[] readBytes ( final IoBuffer buffer, final int quantity )
    {
        final int numOfBytes = buffer.get () & 0xFF; // byte is unsigned
        if ( numOfBytes != quantity * 2 )
        {
            throw new ModbusProtocolError ( "got not the same amount of bytes (" + numOfBytes + ") than expected (" + quantity + ")" );
        }
        final byte[] result = new byte[numOfBytes];
        buffer.get ( result, 0, numOfBytes );
        return result;
    }

    public static BitSet readBits ( final IoBuffer ioBuffer, final int quantity )
    {
        @SuppressWarnings ( "unused" )
        final int noOfBytes = ioBuffer.get () & 0xFF;
        final BitSet bs = new BitSet ( quantity );
        byte b = 0;
        for ( int i = 0; i < quantity; i++ )
        {
            final int shift = i % 8;
            if ( shift == 0 )
            {
                b = ioBuffer.get ();
            }
            bs.set ( i, ( b & 1 << shift ) != 0 );
        }
        return bs;
    }
}
