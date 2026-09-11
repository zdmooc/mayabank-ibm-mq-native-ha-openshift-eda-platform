package fr.mayabank;

import com.ibm.mq.*;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.MQDLH;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class DlqTest {
    static final String QM = "QM.MAYABANK";
    static MQMessage envelope(String destination, byte[] correlation, String body, int reason) throws Exception {
        MQMessage message = new MQMessage();
        message.format = MQConstants.MQFMT_DEAD_LETTER_HEADER;
        message.characterSet = 1208;
        message.encoding = MQConstants.MQENC_NATIVE;
        message.persistence = MQConstants.MQPER_PERSISTENT;
        message.correlationId = correlation;
        MQDLH header = new MQDLH();
        header.setReason(reason);
        header.setDestQName(destination);
        header.setDestQMgrName(QM);
        header.setEncoding(message.encoding);
        header.setCodedCharSetId(1208);
        header.setFormat(MQConstants.MQFMT_STRING);
        header.setPutApplType(MQConstants.MQAT_JAVA);
        header.setPutApplName("MAYABANK.DLQ.TEST");
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        header.setPutDate(now.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        header.setPutTime(now.format(DateTimeFormatter.ofPattern("HHmmss")) + "00");
        header.write(message, message.encoding, message.characterSet);
        message.write(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    static void validate(MQMessage message, String destination, byte[] correlation, String body) throws Exception {
        if (!MQConstants.MQFMT_DEAD_LETTER_HEADER.equals(message.format)
                || message.persistence != MQConstants.MQPER_PERSISTENT
                || !Arrays.equals(correlation, message.correlationId))
            throw new IllegalStateException("FAIL: invalid MQMD");
        message.seek(0);
        MQDLH header = new MQDLH(message, message.encoding, message.characterSet);
        header.validate();
        if (header.getReason() != MQConstants.MQRC_UNKNOWN_OBJECT_NAME
                || !destination.equals(header.getDestQName().trim())
                || !QM.equals(header.getDestQMgrName().trim())
                || !MQConstants.MQFMT_STRING.equals(header.getFormat())
                || header.getCodedCharSetId() != 1208
                || !"MAYABANK.DLQ.TEST".equals(header.getPutApplName().trim()))
            throw new IllegalStateException("FAIL: unexpected MQDLH");
        byte[] data = new byte[message.getDataLength()];
        message.readFully(data);
        if (!Arrays.equals(data, body.getBytes(StandardCharsets.UTF_8)))
            throw new IllegalStateException("FAIL: altered payload");
    }

    static void run() throws Exception {
        String id = UUID.randomUUID().toString();
        String destination = "LAB.MISSING." + id.replace("-", "");
        String body = "DLQ-PROBE|" + id;
        byte[] correlation = new byte[24];
        new SecureRandom().nextBytes(correlation);
        String password = Files.readString(Path.of(Mq.env("MQ_PASSWORD_FILE",
                "/etc/mayabank/mq/mqAppPassword"))).strip();
        if (password.isEmpty()) throw new IllegalStateException("Empty MQ credential");
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(MQConstants.HOST_NAME_PROPERTY, Mq.env("MQ_HOST", "mq"));
        props.put(MQConstants.PORT_PROPERTY, 1414);
        props.put(MQConstants.CHANNEL_PROPERTY, "DEV.APP.SVRCONN");
        props.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES_CLIENT);
        props.put(MQConstants.USER_ID_PROPERTY, "app");
        props.put(MQConstants.PASSWORD_PROPERTY, password);
        props.put(MQConstants.USE_MQCSP_AUTHENTICATION_PROPERTY, true);
        MQQueueManager manager = new MQQueueManager(QM, props);
        try {
            MQQueue dlq = manager.accessQueue("PAYMENT.DLQ",
                    MQConstants.MQOO_OUTPUT | MQConstants.MQOO_BROWSE | MQConstants.MQOO_FAIL_IF_QUIESCING);
            try {
                int reason;
                try {
                    MQQueue unexpected = manager.accessQueue(destination, MQConstants.MQOO_OUTPUT);
                    unexpected.close();
                    throw new IllegalStateException("STOP: test destination unexpectedly exists; no message sent");
                } catch (MQException failure) {
                    if (failure.reasonCode != MQConstants.MQRC_UNKNOWN_OBJECT_NAME) throw failure;
                    reason = failure.reasonCode;
                }
                System.out.println("DELIVERY_FAILED reason=" + reason + " destination=" + destination + " probeId=" + id);
                MQPutMessageOptions put = new MQPutMessageOptions();
                put.options = MQConstants.MQPMO_SYNCPOINT;
                MQMessage outgoing = envelope(destination, correlation, body, reason);
                dlq.put(outgoing, put);
                manager.commit();
                System.out.println("DLQ_WRITTEN origin=application probeId=" + id);
                MQMessage observed = new MQMessage();
                observed.correlationId = correlation;
                MQGetMessageOptions get = new MQGetMessageOptions();
                get.options = MQConstants.MQGMO_BROWSE_FIRST | MQConstants.MQGMO_NO_WAIT
                        | MQConstants.MQGMO_FAIL_IF_QUIESCING;
                get.matchOptions = MQConstants.MQMO_MATCH_CORREL_ID;
                dlq.get(observed, get);
                validate(observed, destination, correlation, body);
                System.out.println("PASS: application DLQ MQDLH reason=2085 and payload verified without consuming; probeId=" + id);
            } finally { dlq.close(); }
        } finally { manager.disconnect(); }
    }
}
