/*******************************************************************************
 * Copyright (c) 2013 TH4 SYSTEMS GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TH4 SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.eclipse.scada.da.server.modbus.io;

import java.nio.ByteOrder;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilter.NextFilter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.eclipse.scada.da.server.io.common.TimedEndDecoder;
import org.eclipse.scada.da.server.modbus.ModbusConstants;
import org.eclipse.scada.da.server.modbus.io.message.ResponseWrapper;
import org.eclipse.scada.da.server.modbus.io.message.request.RequestMessage;
import org.eclipse.scada.da.server.modbus.io.message.response.ResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusRtuDecoder extends TimedEndDecoder
{
    private static final Logger logger = LoggerFactory.getLogger ( ModbusRtuDecoder.class );

    private static final String SESSION_KEY_CURRENT_FRAME = ModbusRtuDecoder.class.getName () + ".currentFrame";

    public ModbusRtuDecoder ( final ScheduledExecutorService scheduler, final long interFrameDelay, final TimeUnit timeUnit )
    {
        super ( scheduler, interFrameDelay, timeUnit );
    }

    @Override
    public void timeout ( final IoSession session, final ProtocolDecoderOutput out ) throws Exception
    {
        logger.trace ( "timeout ()" );
        final IoBuffer currentFrame = (IoBuffer)session.getAttribute ( SESSION_KEY_CURRENT_FRAME );
        session.removeAttribute ( SESSION_KEY_CURRENT_FRAME );
        if ( currentFrame == null )
        {
            throw new ModbusProtocolError ( "no frame found" );
        }
        final RequestMessage originalRequest = ModbusRtuCodecFactory.getOriginalRequest ( session );
        currentFrame.flip ();
        logger.trace ( "timeout () frame = {}", currentFrame.getHexDump () );
        // check size
        if ( currentFrame.limit () <= ModbusRtuCodecFactory.RTU_HEADER_SIZE )
        {
            throw new ModbusProtocolError ( "frame must be at least 4 bytes long (address + data[] + crc low + crc high" );
        }
        // check crc, crc is stored in little endian order
        currentFrame.order ( ByteOrder.LITTLE_ENDIAN );
        final int receivedCrc = currentFrame.getShort ( currentFrame.limit () - 2 ) & 0xffff;
        currentFrame.order ( ByteOrder.BIG_ENDIAN );
        final int actualCrc = CRC.crc16 ( currentFrame.array (), 0, currentFrame.limit () - 2 );
        if ( receivedCrc != actualCrc )
        {
            throw new ModbusProtocolError ( "CRC error. received: " + receivedCrc + ", but actually was: " + actualCrc );
        }
        // read data
        currentFrame.position ( 0 );
        final byte unitIdentifier = currentFrame.get ();
        final IoBuffer pdu = IoBuffer.allocate ( currentFrame.limit () - ModbusRtuCodecFactory.RTU_HEADER_SIZE );
        for ( int i = 0; i < currentFrame.limit () - ModbusRtuCodecFactory.RTU_HEADER_SIZE; i++ )
        {
            pdu.put ( currentFrame.get () );
        }
        pdu.flip ();
        // decode and send
        final ResponseMessage responseMessage = ModbusHelper.decodeResponse ( originalRequest, pdu );
        out.write ( new ResponseWrapper ( unitIdentifier, responseMessage, originalRequest ) );

        // flush it
        NextFilter nextFilter;
        synchronized ( this )
        {
            nextFilter = this.nextFilter;
        }
        logger.trace ( "timeout () flush - nextFilter: {}", nextFilter );
        if ( nextFilter != null )
        {
            out.flush ( nextFilter, session );
        }
    }

    @Override
    public void decode ( final IoSession session, final IoBuffer in, final ProtocolDecoderOutput out ) throws Exception
    {
        if ( !session.containsAttribute ( SESSION_KEY_CURRENT_FRAME ) )
        {
            final IoBuffer newFrame = IoBuffer.allocate ( ModbusConstants.MAX_PDU_SIZE + ModbusRtuCodecFactory.RTU_HEADER_SIZE );
            newFrame.flip ();
            session.setAttribute ( SESSION_KEY_CURRENT_FRAME, newFrame );
        }
        final IoBuffer currentFrame = (IoBuffer)session.getAttribute ( SESSION_KEY_CURRENT_FRAME );
        logger.trace ( "decode () current frame = {} data = {}", currentFrame.toString (), currentFrame.getHexDump () );
        logger.trace ( "decode () new     frame = {} data = {}", in.toString (), in.getHexDump () );
        final int maxSize = ModbusConstants.MAX_PDU_SIZE + ModbusRtuCodecFactory.RTU_HEADER_SIZE;
        final int expectedSize = currentFrame.limit () + in.remaining ();
        if ( expectedSize > maxSize + 1 )
        {
            throw new ModbusProtocolError ( "received size (" + expectedSize + ") exceeds max size (" + maxSize + ")" );
        }
        currentFrame.limit ( expectedSize );
        currentFrame.put ( in );

        tick ( session, out );
    }

    @Override
    public void finishDecode ( final IoSession session, final ProtocolDecoderOutput out ) throws Exception
    {
    }
}