package fr.mayabank;

import com.ibm.mq.*;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.MQDLH;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

final class ReplayTest {
    static final String SOURCE = "LAB.REPLAY.SOURCE.Q";
    static final String TARGET = "LAB.REPLAY.TARGET.Q";
    static final String BLOCKED = "LAB.REPLAY.BLOCKED.Q";
    static final String AUDIT = "LAB.REPLAY.AUDIT.Q";

    static MQMessage read(MQQueue queue, byte[] correlation, boolean browse) throws Exception {
        MQMessage m = new MQMessage();
        m.correlationId = correlation;
        MQGetMessageOptions get = new MQGetMessageOptions();
        get.matchOptions = MQConstants.MQMO_MATCH_CORREL_ID;
        get.options = MQConstants.MQGMO_NO_WAIT | MQConstants.MQGMO_FAIL_IF_QUIESCING
                | (browse ? MQConstants.MQGMO_BROWSE_FIRST : MQConstants.MQGMO_SYNCPOINT);
        queue.get(m, get);
        return m;
    }
    static void absent(MQQueue queue, byte[] id) throws Exception {
        try { read(queue, id, true); }
        catch (MQException e) {
            if (e.reasonCode == MQConstants.MQRC_NO_MSG_AVAILABLE) return;
            throw e;
        }
        throw new IllegalStateException("FAIL: unexpected message");
    }
    static void put(MQQueue queue, MQMessage m) throws Exception {
        MQPutMessageOptions options = new MQPutMessageOptions();
        options.options = MQConstants.MQPMO_SYNCPOINT;
        queue.put(m, options);
    }
    static MQMessage plain(byte[] id, byte[] body) throws Exception {
        MQMessage m = new MQMessage();
        m.correlationId = id;
        m.format = MQConstants.MQFMT_STRING;
        m.characterSet = 1208;
        m.persistence = MQConstants.MQPER_PERSISTENT;
        m.write(body);
        return m;
    }
    static byte[] payload(MQMessage m, byte[] id, String body) throws Exception {
        // Destination stricte, identité et contenu revalidés après le GET transactionnel.
        DlqTest.validate(m, TARGET, id, body);
        m.seek(0);
        MQDLH h = new MQDLH(m, m.encoding, m.characterSet);
        if (!TARGET.equals(h.getDestQName().trim()))
            throw new IllegalStateException("FAIL: replay destination not allowed");
        byte[] bytes = new byte[m.getDataLength()];
        m.readFully(bytes);
        return bytes;
    }
    static void checkPlain(MQMessage m, byte[] id, String text) throws Exception {
        if (!MQConstants.MQFMT_STRING.equals(m.format) || m.characterSet != 1208
                || m.persistence != MQConstants.MQPER_PERSISTENT || !Arrays.equals(id, m.correlationId))
            throw new IllegalStateException("FAIL: replay MQMD mismatch");
        m.seek(0);
        byte[] body = new byte[m.getDataLength()];
        m.readFully(body);
        if (!Arrays.equals(body, text.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalStateException("FAIL: replay body mismatch");
    }

    static void run() throws Exception {
        String probe = UUID.randomUUID().toString();
        String body = "REPLAY-PROBE|" + probe;
        byte[] id = new byte[24];
        new SecureRandom().nextBytes(id);
        String idHex = HexFormat.of().formatHex(id);
        String auditBody = "REPLAY_COMMITTED|" + probe + "|" + SOURCE + "|" + TARGET + "|" + idHex;
        MQQueueManager manager = DlqTest.connect();
        List<MQQueue> opened = new ArrayList<>();
        try {
            int common = MQConstants.MQOO_FAIL_IF_QUIESCING;
            MQQueue source = manager.accessQueue(SOURCE, common | MQConstants.MQOO_OUTPUT
                    | MQConstants.MQOO_INPUT_SHARED | MQConstants.MQOO_BROWSE); opened.add(source);
            MQQueue target = manager.accessQueue(TARGET, common | MQConstants.MQOO_OUTPUT
                    | MQConstants.MQOO_BROWSE); opened.add(target);
            MQQueue audit = manager.accessQueue(AUDIT, common | MQConstants.MQOO_OUTPUT
                    | MQConstants.MQOO_BROWSE); opened.add(audit);
            MQQueue blocked = manager.accessQueue(BLOCKED, common | MQConstants.MQOO_BROWSE); opened.add(blocked);
            put(source, DlqTest.envelope(TARGET, id, body, MQConstants.MQRC_UNKNOWN_OBJECT_NAME));
            manager.commit();
            System.out.println("REPLAY_SEEDED probeId=" + probe + " correlId=" + idHex);
            MQMessage inspected = read(source, id, true);
            payload(inspected, id, body);
            byte[] sourceMessageId = inspected.messageId.clone();
            System.out.println("INSPECT_ONLY source=" + SOURCE + " destination=" + TARGET + " probeId=" + probe);
            absent(target, id);
            absent(audit, id);

            // Faute de test uniquement : destination fixe inhibée, jamais issue du message.
            boolean failed = false;
            try {
                MQMessage incoming = read(source, id, false);
                byte[] bytes = payload(incoming, id, body);
                MQQueue faultTarget = manager.accessQueue(BLOCKED, common | MQConstants.MQOO_OUTPUT);
                try { put(faultTarget, plain(id, bytes)); }
                finally { faultTarget.close(); }
                throw new IllegalStateException("FAIL: blocked destination accepted write");
            } catch (MQException e) {
                if (e.reasonCode != MQConstants.MQRC_PUT_INHIBITED) throw e;
                failed = true;
            } finally { manager.backout(); }
            if (!failed) throw new IllegalStateException("FAIL: missing target failure");
            MQMessage restored = read(source, id, true);
            payload(restored, id, body);
            if (!Arrays.equals(sourceMessageId, restored.messageId))
                throw new IllegalStateException("FAIL: source identity changed");
            absent(blocked, id); absent(target, id); absent(audit, id);
            System.out.println("PASS: replay failure 2051 rolled back; original source preserved; probeId=" + probe);

            try {
                MQMessage incoming = read(source, id, false);
                byte[] bytes = payload(incoming, id, body);
                if (!Arrays.equals(sourceMessageId, incoming.messageId))
                    throw new IllegalStateException("FAIL: source identity mismatch");
                put(target, plain(id, bytes));
                put(audit, plain(id, auditBody.getBytes(StandardCharsets.UTF_8)));
                manager.commit();
            } catch (Exception e) {
                manager.backout();
                throw e;
            }
            absent(source, id);
            checkPlain(read(target, id, true), id, body);
            checkPlain(read(audit, id, true), id, auditBody);
            // Une seconde consommation de la même source doit échouer sans réécriture.
            try {
                read(source, id, false);
                throw new IllegalStateException("FAIL: replay source still consumable");
            } catch (MQException e) {
                if (e.reasonCode != MQConstants.MQRC_NO_MSG_AVAILABLE) throw e;
            } finally { manager.backout(); }
            System.out.println("PASS: replay committed; source absent; target and audit verified; second source GET=2033; probeId=" + probe);
        } finally {
            for (MQQueue q : opened) { try { q.close(); } catch (MQException ignored) {} }
            manager.disconnect();
        }
    }
}
