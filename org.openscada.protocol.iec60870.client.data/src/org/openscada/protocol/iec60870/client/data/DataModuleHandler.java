/*******************************************************************************
 * Copyright (c) 2014 IBH SYSTEMS GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBH SYSTEMS GmbH - initial API and implementation
 *******************************************************************************/
package org.openscada.protocol.iec60870.client.data;

import io.netty.channel.ChannelHandlerContext;

import org.openscada.protocol.iec60870.asdu.ASDUHeader;
import org.openscada.protocol.iec60870.asdu.message.DataTransmissionMessage;
import org.openscada.protocol.iec60870.asdu.message.DoublePointInformationSequence;
import org.openscada.protocol.iec60870.asdu.message.DoublePointInformationSingle;
import org.openscada.protocol.iec60870.asdu.message.DoublePointInformationTimeSingle;
import org.openscada.protocol.iec60870.asdu.message.InterrogationCommand;
import org.openscada.protocol.iec60870.asdu.message.MeasuredValueScaledSequence;
import org.openscada.protocol.iec60870.asdu.message.MeasuredValueScaledSingle;
import org.openscada.protocol.iec60870.asdu.message.MeasuredValueScaledTimeSingle;
import org.openscada.protocol.iec60870.asdu.message.MeasuredValueShortFloatingPointSequence;
import org.openscada.protocol.iec60870.asdu.message.MeasuredValueShortFloatingPointSingle;
import org.openscada.protocol.iec60870.asdu.message.MeasuredValueShortFloatingPointTimeSingle;
import org.openscada.protocol.iec60870.asdu.message.SinglePointInformationSequence;
import org.openscada.protocol.iec60870.asdu.message.SinglePointInformationSingle;
import org.openscada.protocol.iec60870.asdu.message.SinglePointInformationTimeSingle;
import org.openscada.protocol.iec60870.asdu.types.ASDUAddress;
import org.openscada.protocol.iec60870.asdu.types.Cause;
import org.openscada.protocol.iec60870.asdu.types.CauseOfTransmission;
import org.openscada.protocol.iec60870.asdu.types.StandardCause;
import org.openscada.protocol.iec60870.io.AbstractModuleHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataModuleHandler extends AbstractModuleHandler implements DataModuleContext
{

    private final static Logger logger = LoggerFactory.getLogger ( DataModuleHandler.class );

    private final DataHandler dataHandler;

    private final DataModuleOptions options;

    private ChannelHandlerContext ctx;

    public DataModuleHandler ( final DataHandler dataHandler, final DataModuleOptions options )
    {
        this.dataHandler = dataHandler;
        this.options = options;
    }

    @Override
    public void channelActive ( final ChannelHandlerContext ctx ) throws Exception
    {
        super.channelActive ( ctx );
        this.ctx = ctx;
        this.dataHandler.activated ( this, ctx );
    }

    protected ASDUHeader makeHeader ( final Cause cause, final ASDUAddress address )
    {
        if ( this.options.getCauseSourceAddress () != null )
        {
            return new ASDUHeader ( new CauseOfTransmission ( cause, this.options.getCauseSourceAddress () ), address );
        }
        else
        {
            return new ASDUHeader ( new CauseOfTransmission ( cause ), address );
        }
    }

    @Override
    public void startInterrogation ( final ASDUAddress address, final short qualifierOfInterrogation )
    {
        final ChannelHandlerContext ctx = this.ctx;
        if ( ctx == null )
        {
            return;
        }

        ctx.writeAndFlush ( new InterrogationCommand ( makeHeader ( StandardCause.ACTIVATED, address ), qualifierOfInterrogation ) );
    }

    @Override
    public void requestStartData ()
    {
        final ChannelHandlerContext ctx = this.ctx;
        if ( ctx == null )
        {
            return;
        }

        ctx.writeAndFlush ( DataTransmissionMessage.REQUEST_START );
    }

    @Override
    public void channelRead ( final ChannelHandlerContext ctx, final Object msg ) throws Exception
    {
        logger.trace ( "channelRead - ctx: {}, msg: {}", ctx, msg );

        if ( msg == DataTransmissionMessage.CONFIRM_START )
        {
            handleStarted ();
        }
        else if ( msg instanceof SinglePointInformationTimeSingle )
        {
            handleDataMessage ( (SinglePointInformationTimeSingle)msg );
        }
        else if ( msg instanceof SinglePointInformationSingle )
        {
            handleDataMessage ( (SinglePointInformationSingle)msg );
        }
        else if ( msg instanceof SinglePointInformationSequence )
        {
            handleDataMessage ( (SinglePointInformationSequence)msg );
        }
        else if ( msg instanceof DoublePointInformationTimeSingle )
        {
            handleDataMessage ( (DoublePointInformationTimeSingle)msg );
        }
        else if ( msg instanceof DoublePointInformationSingle )
        {
            handleDataMessage ( (DoublePointInformationSingle)msg );
        }
        else if ( msg instanceof DoublePointInformationSequence )
        {
            handleDataMessage ( (DoublePointInformationSequence)msg );
        }
        else if ( msg instanceof MeasuredValueShortFloatingPointTimeSingle )
        {
            handleDataMessage ( (MeasuredValueShortFloatingPointTimeSingle)msg );
        }
        else if ( msg instanceof MeasuredValueShortFloatingPointSingle )
        {
            handleDataMessage ( (MeasuredValueShortFloatingPointSingle)msg );
        }
        else if ( msg instanceof MeasuredValueShortFloatingPointSequence )
        {
            handleDataMessage ( (MeasuredValueShortFloatingPointSequence)msg );
        }
        else if ( msg instanceof MeasuredValueScaledTimeSingle )
        {
            handleDataMessage ( (MeasuredValueScaledTimeSingle)msg );
        }
        else if ( msg instanceof MeasuredValueScaledSingle )
        {
            handleDataMessage ( (MeasuredValueScaledSingle)msg );
        }
        else if ( msg instanceof MeasuredValueScaledSequence )
        {
            handleDataMessage ( (MeasuredValueScaledSequence)msg );
        }
        else
        {
            super.channelRead ( ctx, msg );
        }
    }

    @Override
    public void channelInactive ( final ChannelHandlerContext ctx ) throws Exception
    {
        handleDisconnected ();
        super.channelInactive ( ctx );
    }

    protected void handleDisconnected ()
    {
        this.dataHandler.disconnected ();
    }

    protected void handleStarted ()
    {
        this.dataHandler.started ();
    }

    protected void handleDataMessage ( final SinglePointInformationTimeSingle msg )
    {
        if ( ignoreMessage ( msg ) )
        {
            return;
        }

        this.dataHandler.process ( msg );
    }

    protected boolean ignoreMessage ( final SinglePointInformationTimeSingle msg )
    {
        final Cause cause = msg.getHeader ().getCauseOfTransmission ().getCause ();
        if ( cause == StandardCause.BACKGROUND && this.options.isIgnoreBackgroundScan () )
        {
            return true;
        }

        return false;
    }

    protected void handleDataMessage ( final SinglePointInformationSequence msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final SinglePointInformationSingle msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final DoublePointInformationSequence msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final DoublePointInformationSingle msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final DoublePointInformationTimeSingle msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final MeasuredValueShortFloatingPointTimeSingle msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final MeasuredValueShortFloatingPointSingle msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final MeasuredValueShortFloatingPointSequence msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final MeasuredValueScaledSequence msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final MeasuredValueScaledSingle msg )
    {
        this.dataHandler.process ( msg );
    }

    protected void handleDataMessage ( final MeasuredValueScaledTimeSingle msg )
    {
        this.dataHandler.process ( msg );
    }

}