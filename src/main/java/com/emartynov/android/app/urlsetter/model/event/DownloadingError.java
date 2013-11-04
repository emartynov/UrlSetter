/*
 * Copyright 2013 Eugen Martynov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.emartynov.android.app.urlsetter.model.event;

import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

public class DownloadingError extends UrlEvent
{
    private Exception exception;

    public DownloadingError ( Uri uri, Exception e )
    {
        super( uri );
        exception = e;

        exception.printStackTrace();
    }

    public Exception getException ()
    {
        return exception;
    }

    @Override
    public Map<String, String> getLoggingParams ()
    {
        Map<String, String> loggingParams = super.getLoggingParams();

        loggingParams.put( "Type", exception.getClass().getSimpleName() );
        loggingParams.put( "Message", getMessage() );
        loggingParams.put( "StackTrace", getStackTrace() );

        return loggingParams;
    }

    private String getMessage ()
    {
        String localizedMessage = exception.getLocalizedMessage();
        return localizedMessage == null ? "No Message" : localizedMessage;
    }

    private String getStackTrace ()
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream( out );
        exception.printStackTrace( stream );
        stream.flush();

        return new String( out.toByteArray() );
    }
}