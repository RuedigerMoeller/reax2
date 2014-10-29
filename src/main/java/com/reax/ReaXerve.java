package com.reax;

import com.reax.datamodel.TestRecord;
import com.reax.datamodel.User;
import com.reax.datamodel.UserRole;
import org.nustaq.kontraktor.*;
import org.nustaq.kontraktor.annotations.GenRemote;
import org.nustaq.kontraktor.annotations.Local;
import org.nustaq.kontraktor.impl.ElasticScheduler;
import org.nustaq.kontraktor.remoting.Coding;
import org.nustaq.kontraktor.remoting.SerializerType;
import org.nustaq.kontraktor.remoting.http.ScriptComponentLoader;
import org.nustaq.kontraktor.remoting.http.netty.wsocket.ActorWSServer;
import org.nustaq.kson.Kson;
import org.nustaq.kson.KsonDeserializer;
import org.nustaq.reallive.RLTable;
import org.nustaq.reallive.RealLive;
import org.nustaq.reallive.impl.RLImpl;
import org.nustaq.reallive.impl.storage.TestRec;
import org.nustaq.reallive.sys.config.ConfigReader;
import org.nustaq.reallive.sys.config.SchemaConfig;

import java.io.File;
import java.util.*;
import java.util.function.Function;

/**
 * Created by ruedi on 23.10.2014.
 */
@GenRemote
public class ReaXerve extends Actor<ReaXerve> {

    Map<String,ReaXession> sessions;
    long sessionIdCounter = 1;

    Scheduler clientScheduler; // set of threads processing client requests
    RealLive realLive;
    ReaXConf conf;

    @Local
    public void $init(Scheduler clientScheduler, ReaXConf appconf) {
        this.conf = appconf;
        sessions = new HashMap<>();
        realLive = new RLImpl("./reallive-data");

        this.clientScheduler = clientScheduler;

        realLive.createTable(User.class);
        realLive.createTable(TestRecord.class);

        realLive.getTable("User").$put(
            "admin",
            new User().init("admin", "admin", new Date().toString(), new Date().toString(), UserRole.ADMIN,"me@me.com"),
            0
        );

        RLTable testTable = realLive.getTable("TestRecord");
        for ( int i = 1; i < 500; i++ ) {
            testTable.$put("test_" + i, new TestRecord().init("name"+i,""+Math.random(),13,32,5*i),0);
        }

        try {
            SchemaConfig schemaProps = ConfigReader.readConfig("modelprops.kson");;
            realLive.getMetadata().overrideWith(schemaProps); // FIXME: side effecting
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * to avoid the need for anonymous clients to create a websocket connection prior to login,
     * this is exposed as a webservice and is called using $.get(). The Id returned then can be
     * used to obtain a valid session id for the websocket connection.
     *
     * @param user
     * @param pwd
     * @return
     */
    public Future<String> $authenticate( String user, String pwd ) {
        if ( user != null && pwd != null ) // dummy auth
        {
            Promise p = new Promise();
            realLive.getTable("User").$get(user.trim().toLowerCase()).then((userRecord, error) -> {
                if ( userRecord != null && pwd.equals(((User) userRecord).getPwd())) {
                    ReaXession newSession = Actors.AsActor(ReaXession.class, clientScheduler);
                    String sessionId = "" + sessionIdCounter++; // can be more cryptic in the future
                    newSession.$init(sessionId, (User) userRecord, self(), realLive);
                    sessions.put(sessionId, newSession);
                    p.receive(sessionId,null);
                } else {
                    p.receive(null,"authentication failure");
                }
            });
            return p;
        }
        return new Promise<>(null,"authentication failure");
    }

    public Future<ReaXession> $getSession(String id) {
        return new Promise<>(sessions.get(id));
    }

    @Local
    public Future $clientTerminated(ReaXession session) {
        Promise p = new Promise();
        session.$getId().then((id, err) -> {
            sessions.remove(id);
            p.signal();
        });
        return p;
    }


    /**
     * startup server + map some files for development
     * @param arg
     * @throws Exception
     */
    public static void main( String arg[] ) throws Exception {

        ReaXConf appconf = (ReaXConf) new Kson().readObject(new File("reaxconf.kson"), ReaXConf.class.getName());

        int port = parseArgs(arg);
        if ( port <= 0 )
            port = appconf.port;

        HashMap<String,String> shortClassNameMapping = (HashMap<String, String>) new Kson().readObject(new File("name-map.kson"),HashMap.class);

        ReaXerve xerver = Actors.AsActor(ReaXerve.class);
        final ElasticScheduler scheduler = new ElasticScheduler(2, 1000);
        xerver.$init(scheduler,appconf); // 2 threads, q size 1000

        // start websocket server (default path for ws traffic /websocket)
        ActorWSServer server = ActorWSServer.startAsRestWSServer(
                port,
                xerver,         // facade actor
                new File("./"), // content root
                scheduler,      // Scheduler determining per client q size + number of worker threads
                new Coding(
                    SerializerType.MinBin,
                    conf -> shortClassNameMapping.forEach( (k,v) -> conf.registerCrossPlatformClassMapping(k,v) )
                )
        );

        // install handler to automatically search and bundle jslibs + template snippets
        ScriptComponentLoader loader = new ScriptComponentLoader().setResourcePath(appconf.componentPath);

        // e.g. src='lookup/dir/bootstrap.css will search for first dir/bootstrap.css on component path
        server.setFileMapper( (f) -> {
            if ( f.getPath().replace(File.separatorChar,'/').startsWith("./lookup") ) {
                List<File> files = loader.lookupResource(f.getPath().substring("./lookup".length() + 1), new HashSet<>(), new HashSet<>());
                if ( files.size() > 0 )
                    return files.get(0);
            }
            return f;
        });
        server.setVirtualfileMapper((f) -> {
            if (f.getName().equals("libs.js")) {
                return loader.mergeScripts(appconf.components);
            } else if (f.getName().equals("templates.js")) {
                return loader.mergeTemplateSnippets(appconf.components);
            }
            return null;
        });
    }


    // grab prot from command line args
    private static int parseArgs(String[] arg) {
        int port = 0;
        if ( arg.length > 1 ) {
            System.out.println("Expect port as first argument");
            System.exit(1);
        }
        if ( arg.length > 0 ) {
            try {
                port = Integer.parseInt(arg[0]);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.out.println("Expect port as first argument");
                System.exit(1);
            }
        }
        return port;
    }


}
