package org.openscada.ae.server.http.internal;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.openscada.ae.Event;
import org.openscada.core.Variant;
import org.openscada.core.VariantEditor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class EventSerializer
{
    public static final String isoDatePattern = "yyyy-MM-dd HH:mm:ss.SSS";

    public static final DateFormat isoDateFormat = new SimpleDateFormat ( isoDatePattern );

    private static class VariantTypeAdapter implements JsonSerializer<Variant>, JsonDeserializer<Variant>
    {
        public JsonElement serialize ( final Variant src, final Type typeOfSrc, final JsonSerializationContext context )
        {
            return new JsonPrimitive ( src.toString () );
        }

        public Variant deserialize ( final JsonElement json, final Type typeOfT, final JsonDeserializationContext context ) throws JsonParseException
        {
            VariantEditor ve = new VariantEditor ();
            ve.setAsText ( json.getAsJsonPrimitive ().getAsString () );
            return (Variant)ve.getValue ();
        }
    }

    private static final Gson gson = new GsonBuilder ().setDateFormat ( DateFormat.FULL ).setDateFormat ( isoDatePattern ).registerTypeAdapter ( Variant.class, new VariantTypeAdapter () ).create ();

    public static String serializeEvent ( final Event event )
    {
        return gson.toJson ( event );
    }

    public static Event deserializeEvent ( final String event )
    {
        return gson.fromJson ( event, Event.class );
    }
}
