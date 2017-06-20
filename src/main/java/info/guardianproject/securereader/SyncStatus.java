package info.guardianproject.securereader;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by N-Pex on 2017-06-09.
 */

public class SyncStatus implements Serializable {
    // TODO - do we need a status for "not synced"?
    public static final SyncStatus OK = new SyncStatus(0);
    public static final SyncStatus ERROR_UNKNOWN = new SyncStatus(1);
    public static final SyncStatus ERROR_NOT_FOUND = new SyncStatus(2);
    public static final SyncStatus ERROR_BAD_URL = new SyncStatus(3);
    public static final SyncStatus ERROR_PARSE_ERROR = new SyncStatus(4);

    public long Value;
    public long tryCount;
    public Date lastTry;
    public String lastETag;

    public SyncStatus(long value) {
        Value = value;
    }

    public boolean equals(Object obj) {
        return obj != null && obj instanceof SyncStatus && ((SyncStatus) obj).Value == this.Value;
    }

    public boolean equals(long id) {
        return Value == id;
    }
}