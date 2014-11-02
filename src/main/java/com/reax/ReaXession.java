package com.reax;

import com.reax.datamodel.User;
import org.nustaq.kontraktor.Actor;
import org.nustaq.kontraktor.Callback;
import org.nustaq.kontraktor.Future;
import org.nustaq.kontraktor.Promise;
import org.nustaq.kontraktor.annotations.GenRemote;
import org.nustaq.kontraktor.annotations.Local;
import org.nustaq.kontraktor.remoting.RemotableActor;
import org.nustaq.kontraktor.util.Log;
import org.nustaq.machweb.MachWebSession;
import org.nustaq.reallive.RealLive;
import org.nustaq.reallive.RealLiveClientWrapper;
import org.nustaq.reallive.Subscription;
import org.nustaq.reallive.queries.JSQuery;
import org.nustaq.reallive.sys.messages.Invocation;
import org.nustaq.reallive.sys.messages.QueryTuple;
import org.nustaq.reallive.sys.metadata.Metadata;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Created by ruedi on 23.10.2014.
 */
@GenRemote
public class ReaXession extends MachWebSession<ReaXerve,ReaXession> {

    RealLive realLive;
    User user;

    @Local
    public void $init(User user, RealLive realLive) {
        this.realLive = new RealLiveClientWrapper(realLive);
        this.user = user;
    }

    //////////////////////////////////////////////////////
    // RealLive access (to be isolated)

    HashMap<String,Subscription> subscriptions = new HashMap<>();
    int subsCount = 1;

    public Future<String> $subscribeKey(String table, String recordKey, Callback cb) {
        Subscription subs = realLive.stream(table).subscribeKey( recordKey, (change) -> cb.receive(change,CONT) );
        String key = "subs" + subsCount++;
        subscriptions.put(key, subs);
        return new Promise<>(key);
    }

    public Future<String> $subscribe(String table, String query, Callback cb) {
        Subscription subs = realLive.stream(table).subscribe(new JSQuery(query), (change) -> cb.receive(change, CONT));
        String key = "subs" + subsCount++;
        subscriptions.put(key, subs);
        return new Promise<>(key);
    }

    public void $unsubscribe(String subsKey) {
        Subscription subs = subscriptions.get(subsKey);
        if ( subs != null ) {
            realLive.getTable(subs.getTableKey()).stream().unsubscribe(subs);
            subscriptions.remove(subsKey);
        } else {
            Log.Warn(this,"no subscription for unsubscribe found for key "+subsKey);
        }
    }

    public Future<Metadata> $getRLMeta() {
        return new Promise<>(realLive.getMetadata());
    }

//    // expect [tableName,filterString]
//    Object listen(Invocation<QueryTuple> inv) {
//        QueryTuple argument = inv.getArgument();
//        Subscription subs = getRLDB().stream("" + argument).listen(new JSQuery(argument.getQuerySource()), (change) -> sendReply(inv, change));
//        subscriptions.put(inv.getCbId(), subs);
//        return NO_RESULT;
//    }

    // expect [tableName,filterString]
    public void $query( String table, String query, Callback cb ) {
        realLive.stream( table ).filter(new JSQuery(query),
            change ->
              cb.receive(change, change.isError()||change.isSnapshotDone() ? null : CONT)
        );
    }

    //
    //////////////////////////////////////////////////////

}
