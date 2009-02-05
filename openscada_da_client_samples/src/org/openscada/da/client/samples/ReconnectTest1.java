package org.openscada.da.client.samples;

import org.apache.log4j.Logger;
import org.openscada.core.ConnectionInformation;
import org.openscada.core.client.ConnectionFactory;
import org.openscada.da.client.Connection;

public class ReconnectTest1
{
    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger ( ReconnectTest1.class );

    public static void main ( String[] args ) throws ClassNotFoundException, InterruptedException
    {
        final String className = "org.openscada.da.client.net.Connection";
        final String uri = "da:net://127.0.0.1:12345?auto-reconnect=true&reconnect-delay=0";

        if ( className != null )
        {
            Class.forName ( className );
        }

        ConnectionInformation ci = ConnectionInformation.fromURI ( uri );

        final Connection connection = (Connection)ConnectionFactory.create ( ci );
        if ( connection == null )
        {
            throw new RuntimeException ( "Unable to find a connection driver for specified URI" );
        }

        connection.connect ();

        while ( true )
        {
            Thread.sleep ( 100 );
        }
    }
}
