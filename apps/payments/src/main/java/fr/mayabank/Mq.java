package fr.mayabank;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import javax.jms.Connection;
import java.nio.file.Files;
import java.nio.file.Path;

final class Mq {
    static String env(String name, String fallback) { return System.getenv().getOrDefault(name, fallback); }

    static Connection connect(boolean wrongPassword) throws Exception {
        MQConnectionFactory factory = new MQConnectionFactory();
        factory.setHostName(env("MQ_HOST", "mq"));
        factory.setPort(1414);
        factory.setQueueManager("QM.MAYABANK");
        factory.setChannel("DEV.APP.SVRCONN");
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
        factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, true);
        Path passwordFile = Path.of(env("MQ_PASSWORD_FILE", "/etc/mayabank/mq/mqAppPassword"));
        if (!Files.isReadable(passwordFile))
            throw new java.io.IOException("MQ credential file missing or unreadable: " + passwordFile);
        String password = Files.readString(passwordFile).strip();
        if (password.isEmpty()) throw new java.io.IOException("MQ credential file is empty");
        if (wrongPassword) password = "deliberately-invalid-" + java.util.UUID.randomUUID();
        return factory.createConnection("app", password);
    }
}
