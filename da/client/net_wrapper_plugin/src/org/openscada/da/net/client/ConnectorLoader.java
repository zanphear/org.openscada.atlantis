package org.openscada.da.net.client;

public class ConnectorLoader implements org.openscada.rcp.da.client.ConnectorLoader
{

    public void load ()
    {
        try
        {
            Class.forName ( "org.openscada.da.client.net.Connection" );
        }
        catch ( ClassNotFoundException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
