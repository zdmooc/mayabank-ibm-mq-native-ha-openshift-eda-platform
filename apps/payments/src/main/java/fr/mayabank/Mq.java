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
        String password = Files.readString(Path.of("/run/secrets/mqAppPassword")).strip();
        if (wrongPassword) password = "deliberately-invalid-" + java.util.UUID.randomUUID();
        return factory.createConnection("app", password);
    }
}
