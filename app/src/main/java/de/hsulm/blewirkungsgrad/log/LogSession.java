package de.hsulm.blewirkungsgrad.log;

import android.content.ContentProvider;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/**
 * Created by wan5xp on 31.10.2017.
 */

public class LogSession implements ILogSession {
    /* package */final Uri sessionUri;
    private final Context context;

    /* package */LogSession(final Context context, final Uri sessionUri) {
        this.context = context;
        this.sessionUri = sessionUri;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public Uri getSessionUri() {
        return sessionUri;
    }

    @Override
    public Uri getSessionEntriesUri() {
        return sessionUri.buildUpon().appendEncodedPath(LogContract.Log.CONTENT_DIRECTORY).build();
    }

    /**
     * Returns the {@link Uri} that may by used to obtain all sessions created by the same application (and the same profile) as this session. It may be used to open the list of log sessions in the
     * nRF Logger application or to obtain list of sessions using {@link ContentProvider}. Keep in mind that sessions with {@link LogContract.Session#NUMBER} equal to 0 are "date sessions". Date
     * sessions are created each time a new session is being added by the application (and profile) in a new day. See {@link Logger} for more information.
     * <p>
     * <pre>
     * Intent intent = new Intent(Intent.ACTION_VIEW, mLogSession.getSessionsUri());
     * startActivity(intent);
     * </pre>
     *
     * @return the Uri for all sessions created by the app used to create this session or <code>null</code> if the session Uri is invalid or the owner app data does not exist in the database
     */
    public Uri getSessionsUri() {
        try {
            final Cursor cursor = context.getContentResolver().query(sessionUri, new String[]{LogContract.Session.APPLICATION_ID}, null, null, null);
            try {
                if (cursor.moveToNext()) {
                    final long appId = cursor.getLong(0);
                    return LogContract.Session.createSessionsUri(appId);
                }
                return null;
            } finally {
                cursor.close();
            }
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public Uri getSessionContentUri() {
        return sessionUri.buildUpon().appendEncodedPath(LogContract.Log.CONTENT_DIRECTORY).appendEncodedPath(LogContract.Session.Content.CONTENT).build();
    }

    @Override
    public String toString() {
        return sessionUri.toString();
    }
}
