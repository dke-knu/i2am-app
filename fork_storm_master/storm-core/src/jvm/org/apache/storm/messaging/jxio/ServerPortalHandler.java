package org.apache.storm.messaging.jxio;

import org.accelio.jxio.*;
import org.accelio.jxio.exceptions.JxioGeneralException;
import org.accelio.jxio.exceptions.JxioSessionClosedException;
import org.apache.storm.messaging.TaskMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by seokwoo on 2017-04-27.
 */
public class ServerPortalHandler extends Thread implements WorkerCache.Worker {
    private static final Logger LOG = LoggerFactory.getLogger(ServerPortalHandler.class);

    private Server server;
    private int numOfWorkers;
    private ServerSession session = null;
    private final EventQueueHandler eqh;
    private final ServerPortal sp;
    private final ServerSessionCallbacks ssCallbacks;
    public final int portalIndex;
    private final String name;
    private MsgPool msgPool;
    private Msg msg = null;
    private boolean notifyDisconnect = false;
    private boolean waitingToClose = false;
    private boolean sessionClosed = false;
    private boolean stop = false;
    private String remoteIp;


    public ServerPortalHandler(int index, URI uri, Server.PortalServerCallbacks psc, HashMap<String, Integer> jxioConfigs, Server server) {
        this.server = server;
        portalIndex = index;
        name = "[ServerPortalHandler " + portalIndex + " ]";
        eqh = new EventQueueHandler(new EqhCallbacks());
        msgPool = new MsgPool(jxioConfigs.get("initial_buf_count"), jxioConfigs.get("msgpool"),
                jxioConfigs.get("msgpool"));
        eqh.bindMsgPool(msgPool);
        ssCallbacks = new ServerSessionCallbacks();
        sp = new ServerPortal(eqh, uri, psc);
        LOG.info(this.toString() + " is up and waiting for requests" + Thread.currentThread().getName());
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public boolean isSessionAlive() {
        return (session != null);
    }

    public void sendMsg(TaskMessage message) {
        try {

            if(session == null) LOG.info("session null...");

            Msg msg = msgPool.getMsg();
            msg.getOut().put(message.serialize());
            session.sendResponse(msg);
        } catch (JxioGeneralException e) {
            e.printStackTrace();
        } catch (JxioSessionClosedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Main loop of worker thread.
     * waits in eqh until first msgs is recieved
     */
    public void run() {
        while (!stop) {
            LOG.info(this.toString() + " waiting for a new connection");
            //infinitly
            eqh.run(); // to get the forward going
            if (notifyDisconnect) {
                stop = true;
                close();
            }
        }
        eqh.stop();
        eqh.close();
        msgPool.deleteMsgPool();
        LOG.info(this.toString() + " worker done");
    }

    /**
     * Close the session and wait until all msgs are returned to the msgpoll
     */
    private synchronized void close() {
        if (!waitingToClose && session != null) {
//            sendMsg(); // free last msg if needed
            LOG.info(this.toString() + " closing session processed ");
            waitingToClose = true;
            session.close();
            while (!sessionClosed) {
                eqh.runEventLoop(EventQueueHandler.INFINITE_EVENTS, EventQueueHandler.INFINITE_DURATION);
            }
        }
        sessionClosed();
    }

    /**
     * clears the session and return worker to pool
     */
    private void sessionClosed() {
        LOG.info(this.toString() + " disconnected from a Session");
        sessionClosed = false;
        waitingToClose = false;
        msg = null;
        session = null;
    }

    ServerSessionCallbacks getSessionCallbacks() {
        return ssCallbacks;
    }

    public ServerPortal getPortal() {
        return sp;
    }

    public void disconnect() {
        notifyDisconnect = true;
        if (waitingToClose)
            return;
        eqh.breakEventLoop();
    }

    // if session null return true, else false
    @Override
    public boolean isFree() {
        return (session == null);
    }

    public String toString() {
        return this.name;
    }

    public ServerSession getSession() {
        return session;
    }

    class EqhCallbacks implements EventQueueHandler.Callbacks {

        public MsgPool getAdditionalMsgPool(int in, int out) {
            LOG.info("Messages in Server's message ran out, Aborting test");
            return null;
        }
    }

    public class ServerSessionCallbacks implements ServerSession.Callbacks {
        List<TaskMessage> messages = new ArrayList<>();

        @Override
        public void onRequest(Msg msg) {
//            msg.getOut().position(msg.getOut().capacity()); // simulate 'out_msgSize' was written into buffer

            ByteBuffer bb = msg.getIn();
            TaskMessage tm = new TaskMessage(0, null);
            tm.deserialize(bb);
            messages.add(tm);
            try {
                server.received(messages, remoteIp);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSessionEvent(EventName event, EventReason reason) {
            LOG.info(this.toString() + " got event " + event.toString() + ", the reason is "
                    + reason.toString());
            if (event == EventName.SESSION_CLOSED) {
                sessionClosed = true;
                waitingToClose = true;
                eqh.breakEventLoop();
            }
        }

        @Override
        public boolean onMsgError(Msg msg, EventReason reason) {
            if (session.getIsClosing()) {
                LOG.debug("On Message Error while closing. Reason is=" + reason);
            } else if (reason == EventReason.MSG_FLUSHED) {
                LOG.warn(ServerPortalHandler.this.toString() + " onMsgErrorCallback. reason is " + reason);
            } else {
                LOG.error("On Message Error. Reason is=" + reason);
            }
            return true;
        }
    }
}
